#!/usr/bin/env python3
"""Training rows for the learned brain from human play on the internet: OpenAI's VPT contractor data.

    python scripts/loop/vpt.py fetch --index 10 --out DIR [--n 200] [--shard 0 --shards 1]
    python scripts/loop/vpt.py convert FILE.jsonl --out DIR
    python scripts/loop/vpt.py compare --bot LOOP_DATA_DIR --vpt DIR

OpenAI paid players to play Minecraft 1.16 and published each 5-minute segment as a video plus a
JSONL action log (github.com/openai/Video-Pre-Training). We never download the video. The action
log has, every tick, the inventory, position, and cumulative stats (blocks mined, items crafted,
mobs killed, deaths). From stat deltas we read what the player was doing, in the bot's own action
names ("collect:log", "craft:stone_pickaxe", "smelt:iron_ingot", "attack:zombie", ...), and write
the same rows loop.py saves from our runs (see save_training_rows), so train.py learns from both.

Features the log doesn't carry (health, hunger, night, nearby mobs, lava/water seen) get neutral
values; the inventory ones are exact. Index 7 is the first 30 minutes of a fresh world, 10 the
"obtain a diamond pickaxe" task. Neither reaches the Nether: this data teaches the early-game
order, not the portal.
"""
import argparse
import concurrent.futures
import glob
import gzip
import json
import os
import random
import sys
import urllib.request

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import common  # noqa: E402

BASE = "https://openaipublic.blob.core.windows.net/minecraft-rl/"
INDEX = {"6": "snapshots/all_6xx_Jun_29.json", "7": "snapshots/all_7xx_Apr_6.json",
         "8": "snapshots/all_8xx_Jun_29.json", "9": "snapshots/all_9xx_Jun_29.json",
         "10": "snapshots/all_10xx_Jun_29.json"}
TIMEOUT = 60

THROWAWAY = {"dirt", "cobblestone", "cobbled_deepslate", "netherrack", "stone", "andesite", "diorite", "granite",
             "tuff", "blackstone"}
RAW_MEAT = {"beef", "porkchop", "mutton", "chicken", "rabbit", "cod", "salmon"}
FOOD = {"apple", "bread", "baked_potato", "carrot", "sweet_berries", "melon_slice", "golden_apple", "cookie",
        "pumpkin_pie", "mushroom_stew", "beetroot", "dried_kelp", "cooked_beef", "cooked_porkchop", "cooked_mutton",
        "cooked_chicken", "cooked_rabbit", "cooked_cod", "cooked_salmon"}
SMELTED = {"iron_ingot", "gold_ingot", "charcoal", "glass", "smooth_stone", "stone"} | {f for f in FOOD if f.startswith("cooked_")}
TIER = {"wooden": 0, "golden": 0, "stone": 1, "iron": 2, "diamond": 3, "netherite": 4}
# Mined block -> the bot's collect argument (Planner option "collect <item>"). Others (leaves,
# grass, flowers) aren't actions the bot takes, so they're not rows.
MINED = {"coal_ore": "coal", "iron_ore": "raw_iron", "diamond_ore": "diamond", "gravel": "flint", "sand": "sand",
         "obsidian": "obsidian", "gold_ore": "gold_ore"}
STONES = {"stone", "cobblestone", "andesite", "diorite", "granite", "deepslate"}
IDLE_TICKS = 200     # 10 s without events ends an activity; moving on counts as exploring
LEAD_TICKS = 40      # the state 2 s before the first event is the state the choice was made in


def short(k):
    return k.split(":")[-1].replace("minecraft.", "")


def collect_key(block):
    if block.endswith(("_log", "_stem", "_wood")):
        return "collect:log"
    if block in STONES:
        return "collect:stone"
    return "collect:" + MINED[block] if block in MINED else None


def craft_key(item):
    if item == "air":
        return None
    if item in SMELTED:
        return "smelt:" + item
    return "craft:" + ("planks" if item.endswith("_planks") else item)


def inventory(line):
    inv = {}
    for s in line.get("inventory", []):
        inv[s["type"]] = inv.get(s["type"], 0) + s.get("quantity", 0)
    return inv


def cap(v, m):
    return min(v, m) / float(m)


def features(feats, line, deaths):
    inv = inventory(line)
    n = lambda pred: sum(q for i, q in inv.items() if pred(i))  # noqa: E731
    def tier(kind):
        ts = [TIER.get(i.split("_")[0], 0) for i in inv if i.endswith("_" + kind)]
        return (max(ts) + 1) / 5.0 if ts else 0.0
    m = 0
    if inv.get("wooden_pickaxe") or inv.get("crafting_table"):
        m = 1
    if m >= 1 and (inv.get("stone_pickaxe") or inv.get("iron_pickaxe") or inv.get("diamond_pickaxe")):
        m = 2
    if m >= 2 and (inv.get("iron_pickaxe") or inv.get("diamond_pickaxe")):
        m = 4
    if m >= 4 and inv.get("diamond_pickaxe"):
        m = 6
    y = line.get("ypos", 64)
    v = {"hp": 1.0, "food": 0.9, "armor": 0.0, "night": 0, "underground": 1 if y < 55 else 0, "y": y / 64.0,
         "nether": 0, "end": 0, "pick": tier("pickaxe"), "sword": tier("sword"), "shield": 1 if inv.get("shield") else 0,
         "log": cap(n(lambda i: i.endswith(("_log", "_stem"))), 16), "planks": cap(n(lambda i: i.endswith("_planks")), 16),
         "blocks": cap(n(lambda i: i in THROWAWAY), 32), "iron": cap(inv.get("iron_ingot", 0), 16),
         "raw_iron": cap(inv.get("iron_ore", 0) + inv.get("raw_iron", 0), 16), "coal": cap(inv.get("coal", 0), 16),
         "cooked": cap(n(lambda i: i in FOOD), 16), "raw_meat": cap(n(lambda i: i in RAW_MEAT), 16),
         "buckets": cap(n(lambda i: i.endswith("bucket")), 2), "water_bucket": 1 if inv.get("water_bucket") else 0,
         "lava_bucket": 1 if inv.get("lava_bucket") else 0, "flint_steel": 1 if inv.get("flint_and_steel") else 0,
         "obsidian": cap(inv.get("obsidian", 0), 10), "hostiles8": 0.0, "hostile_dist": 1.0, "deaths": cap(deaths, 5),
         "milestone": m / 13.0, "gold": cap(inv.get("gold_ingot", 0), 16), "rods": cap(inv.get("blaze_rod", 0), 6),
         "pearls": cap(inv.get("ender_pearl", 0), 12), "eyes": cap(inv.get("ender_eye", 0), 12)}
    return [round(float(v.get(f, 0.0)), 3) for f in feats]


def events(prev, cur):
    """What happened this tick, most telling first: [(kind, key)]."""
    ps, cs = prev.get("stats", {}), cur.get("stats", {})
    out = []
    for k, val in cs.items():
        if val <= ps.get(k, 0):
            continue
        what = short(k)
        if k.endswith("custom:minecraft.deaths"):
            out.append((0, "death"))
        elif ".killed:" in k:
            out.append((1, "attack:" + what))
        elif ".use_item:" in k and what in FOOD | RAW_MEAT:
            out.append((2, "eat"))
        elif ".craft_item:" in k:
            key = craft_key(what)
            if key:
                out.append((3, key))
        elif ".mine_block:" in k:
            key = collect_key(what)
            if key:
                out.append((5, key))
    pi, ci = inventory(prev), inventory(cur)
    for b in ("water_bucket", "lava_bucket"):
        if ci.get(b, 0) > pi.get(b, 0):
            out.append((4, "fill_bucket:" + b.split("_")[0]))
    return [k for _, k in sorted(out)]


def moved(prev, cur):
    return abs(cur.get("xpos", 0) - prev.get("xpos", 0)) + abs(cur.get("zpos", 0) - prev.get("zpos", 0)) > 0.05


def convert(lines, feats):
    """Rows like loop.save_training_rows: decisions {k:d, gs, x, a, p, i, u} and {k:death, gs}."""
    rows, deaths = [], 0
    current, last_event, last_row_tick, walk_since = None, -10 ** 9, 0, None
    for t in range(1, len(lines)):
        prev, cur = lines[t - 1], lines[t]
        evs = events(prev, cur)
        if "death" in evs:
            deaths += 1
            rows.append({"k": "death", "gs": t // 20, "detail": "vpt"})
            current = None
            evs = [e for e in evs if e != "death"]
        if evs:
            walk_since = None
            key = evs[0]
            if key != current:
                at = max(last_row_tick, t - LEAD_TICKS)
                rows.append({"k": "d", "gs": at // 20, "x": features(feats, lines[at], deaths), "a": key,
                             "p": 1, "i": 0, "u": False})
                current, last_row_tick = key, t
            last_event = t
            continue
        if t - last_event > IDLE_TICKS:
            current = None if current != "explore:any" else current
            if moved(prev, cur) and not cur.get("isGuiOpen"):
                walk_since = t if walk_since is None else walk_since
                if current is None and t - walk_since > IDLE_TICKS:
                    rows.append({"k": "d", "gs": walk_since // 20, "x": features(feats, lines[walk_since], deaths),
                                 "a": "explore:any", "p": 1, "i": 0, "u": False})
                    current, last_row_tick = "explore:any", t
            else:
                walk_since = None
    rows.sort(key=lambda r: r["gs"])
    return rows


def write_rows(rows, path):
    os.makedirs(os.path.dirname(path) or ".", exist_ok=True)
    with gzip.open(path, "wt", encoding="utf-8") as f:
        for r in rows:
            f.write(json.dumps(r, separators=(",", ":")) + "\n")


def get(url):
    with urllib.request.urlopen(url, timeout=TIMEOUT) as r:
        return r.read()


def parse(text):
    out = []
    for l in text.splitlines():
        try:
            out.append(json.loads(l))
        except ValueError:
            pass
    return out


def fetch_one(basedir, rel, index, out_dir, feats):
    """One segment's action log -> rows; True if kept. Some logs are missing (404): skipped."""
    out = os.path.join(out_dir, "vpt%s-%s.jsonl.gz" % (index, os.path.basename(rel)[:-4]))
    if os.path.exists(out):
        return True
    try:
        lines = parse(get(basedir + rel[:-4] + ".jsonl").decode("utf-8", "replace"))
    except OSError:
        return False
    rows = convert(lines, feats)
    if sum(1 for r in rows if r["k"] == "d") < 3:
        return False
    write_rows(rows, out)
    return True


def cmd_fetch(a):
    idx = json.loads(get(BASE + INDEX[a.index]))
    rels = sorted(r for r in idx["relpaths"] if r.endswith(".mp4"))
    rels = rels[a.shard::a.shards]
    if a.n:
        random.Random(a.seed).shuffle(rels)
        rels = rels[:a.n]
    feats = common.load_features()
    with concurrent.futures.ThreadPoolExecutor(8) as ex:
        kept = sum(ex.map(lambda r: fetch_one(idx["basedir"], r, a.index, a.out, feats), rels))
    print("vpt index %s shard %d/%d: %d segments read, %d kept in %s" % (a.index, a.shard, a.shards, len(rels), kept, a.out))


def fit(files, feats, horizon):
    """train.py's model (baseline ridge + one advantage ridge per action kind), from these files."""
    import train
    by = {}
    for _, key, x, y, w in train.examples(files, feats, horizon):
        for k in (key, key.split(":")[0]):
            X, Y, W = by.setdefault(k, ([], [], []))
            X.append(x)
            Y.append(y)
            W.append(w)
    allX, allY, allW = [], [], []
    for k, (X, Y, W) in by.items():
        if ":" not in k:
            allX, allY, allW = allX + X, allY + Y, allW + W
    base = train.ridge(allX, allY, allW, train.RIDGE)
    models = {}
    for k, (X, Y, W) in by.items():
        if len(X) >= train.MIN_ROWS:
            resid = [y - train.predict(base, x) for x, y in zip(X, Y)]
            models[k] = [b + d for b, d in zip(base, train.ridge(X, resid, W, train.RIDGE * 4))]
    return base, models, len(allY)


def cmd_compare(a):
    """Does human data help predict the bot's own games? Same held-out bot runs, with and without it.
    The choice part (how well it tells actions apart in the same state) is the number that matters."""
    import train
    feats = common.load_features()
    bot = sorted(glob.glob(os.path.join(a.bot, "**", "*.jsonl.gz"), recursive=True))
    human = sorted(glob.glob(os.path.join(a.vpt, "**", "*.jsonl.gz"), recursive=True))
    rng = random.Random(7)
    held = {f for f in bot if rng.random() < 0.2}
    test = [(k, x, y) for _, k, x, y, _ in train.examples(sorted(held), feats, a.horizon)]
    kept = [f for f in bot if f not in held]
    print("| training data | decisions | action kinds | held-out R2 | choice part R2 |")
    print("|---|---|---|---|---|")
    for name, files in (("bot only", kept), ("bot + human", kept + human), ("human only", human)):
        base, models, n = fit(files, feats, a.horizon)
        preds, advs = [], []
        for key, x, y in test:
            w = models.get(key) or models.get(key.split(":")[0])
            if w:
                p, b = train.predict(w, x), train.predict(base, x)
                preds.append((p, y))
                advs.append((p - b, y - b))
        print("| %s | %d | %d | %s | %s |" % (name, n, len(models), train.rsq(preds), train.rsq(advs)))
    print("\n%d human segments, %d bot runs (%d held out)." % (len(human), len(bot), len(held)))


def cmd_convert(a):
    rows = convert(parse(open(a.file, encoding="utf-8").read()), common.load_features())
    name = os.path.basename(a.file).rsplit(".", 1)[0]
    write_rows(rows, os.path.join(a.out, "vpt-" + name + ".jsonl.gz"))
    for r in rows:
        print(r["gs"], r["k"], r.get("a", ""))


def main():
    ap = argparse.ArgumentParser()
    sub = ap.add_subparsers(dest="cmd", required=True)
    f = sub.add_parser("fetch")
    f.add_argument("--index", default="10", choices=sorted(INDEX))
    f.add_argument("--n", type=int, default=0, help="a random sample of this many (0: the whole shard)")
    f.add_argument("--out", required=True)
    f.add_argument("--seed", type=int, default=1)
    f.add_argument("--shard", type=int, default=0)
    f.add_argument("--shards", type=int, default=1)
    c = sub.add_parser("convert")
    c.add_argument("file")
    c.add_argument("--out", required=True)
    p = sub.add_parser("compare")
    p.add_argument("--bot", required=True)
    p.add_argument("--vpt", required=True)
    p.add_argument("--horizon", type=float, default=120)
    a = ap.parse_args()
    {"fetch": cmd_fetch, "convert": cmd_convert, "compare": cmd_compare}[a.cmd](a)


if __name__ == "__main__":
    main()
