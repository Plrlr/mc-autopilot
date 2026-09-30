#!/usr/bin/env python3
"""Held-out choice signs: python3 scripts/loop/test_signcheck.py (numpy, no real training)."""
import contextlib
import gzip
import io
import json
import os
import random
import sys
import tempfile
from unittest import mock

import numpy as np

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import gbt  # noqa: E402
import mlp  # noqa: E402
import train  # noqa: E402

HEADS = ["_v", "collect:log", "collect", "explore:any", "explore"]


def synthetic(games=20, decisions=20, random_signs=False):
    """Known deviation advantages; 80% of games have correct predictions, the rest disagree."""
    rng = random.Random(29)
    rows, choices = [], []
    for game in range(games):
        for i in range(decisions):
            actual = 2.0 if i % 2 else -3.0
            gap = (1 if actual > 0 else -1) * (1 if game < int(0.8 * games) else -1)
            if random_signs:
                gap = rng.choice([-1, 1])
            x = [0.0] * 40
            x[1:5] = [10.0 + game / 2, gap, i % 2, (i // 2) % 2]
            chosen = "collect:log" if x[3] else "collect:stone"
            rules = "explore:any" if x[4] else "explore:lava"
            rows.append((chosen, x, x[1] + actual, "g%d" % game))
            choices.append((2, [rules, "eat", chosen]))
    return rows, choices


def fake_predictor(layers, heads, scale, X):
    """Full-key and skill heads deliberately differ, so choosing the wrong fallback fails."""
    X = np.asarray(X)
    base, gap = X[:, 1], X[:, 2]
    full_chosen, full_rules = X[:, 3] != 0, X[:, 4] != 0
    values = {"_v": base,
              "collect:log": base + np.where(full_chosen, gap, -3 * gap),
              "collect": base + np.where(full_chosen, -3 * gap, gap),
              "explore:any": base + np.where(full_rules, 0, 4 * gap),
              "explore": base + np.where(full_rules, 4 * gap, 0)}
    return np.column_stack([values[h] for h in heads])


def check(rows, choices, heads=HEADS):
    out = fake_predictor(None, heads, 1, [r[1] for r in rows])
    return train.sign_check(rows, choices, heads, out)


def known_signs_and_whole_games():
    rows, choices = synthetic()
    sign = check(rows, choices)
    assert sign["n"] == 400 and sign["games"] == 20, sign
    assert sign["agree"] == 0.8, sign
    # Whole games agree or disagree together: resampling individual rows would give ~0.77.
    assert 0.55 <= sign["lo"] <= 0.70, sign
    assert sign["mean_gap_ok"] == 1.0 and sign["mean_gap_bad"] == -2.0, sign
    assert check(rows, choices) == sign  # seeded
    assert check(rows, choices, HEADS[1:] + HEADS[:1]) == sign  # use the named "_v" head
    return sign


def unusable_rows_are_skipped():
    rows, choices = synthetic()
    expected = check(rows, choices)
    key, x, y, _ = rows[0]
    rules = choices[0][1][0]
    for i, options in [(1, None), (0, [rules, key]), (1, []), (-1, [rules, key])]:
        rows.append((key, x, y, "ignored"))
        choices.append((i, options))
    rows.append(("attack:zombie", x, y, "missing-chosen-head"))
    choices.append((1, [rules, "attack:zombie"]))
    rows.append((key, x, y, "missing-rules-head"))
    choices.append((1, ["attack:zombie", key]))
    tied = list(x)
    tied[2] = 0
    rows.append((key, tied, y, "tied"))
    choices.append((1, [rules, key]))
    assert check(rows, choices) == expected


def thin_data_and_zero_outcomes():
    rows, choices = synthetic(games=10, decisions=3)
    assert check(rows, choices)["agree"] is not None  # exactly 30 rows, 10 games
    for rs, cs, n, games in [(rows[:-1], choices[:-1], 29, 10),
                              (*synthetic(games=9, decisions=4), 36, 9)]:
        sign = check(rs, cs)
        assert sign["n"] == n and sign["games"] == games, sign
        assert all(sign[k] is None for k in ("agree", "lo", "mean_gap_ok", "mean_gap_bad")), sign
    # A zero actual advantage doesn't agree with either nonzero predicted sign.
    rows = [(k, x, x[1], f) for k, x, _, f in rows]
    sign = check(rows, choices)
    assert sign["agree"] == 0 and sign["lo"] == 0, sign
    assert sign["mean_gap_ok"] == 0 and sign["mean_gap_bad"] == 0, sign
    for gap, empty_group in [(1, "mean_gap_bad"), (-1, "mean_gap_ok")]:
        for _, x, _, _ in rows:
            x[2] = gap
        assert check(rows, choices)[empty_group] is None


def random_signs_are_chance():
    rows, choices = synthetic(games=100, random_signs=True)
    sign = check(rows, choices)
    assert sign["n"] == 2000 and sign["games"] == 100, sign
    assert abs(sign["agree"] - 0.5) < 0.03, sign
    assert sign["lo"] < 0.5, sign


def examples_keep_choices_aligned():
    def decision(gs, pick, **kw):
        return {"k": "d", "gs": gs, "x": [pick] + [0.0] * 39, "a": "collect:log", **kw}

    options = ["explore:any", "eat", "collect:log"]
    data = [decision(0, 0), decision(20, 0.1, u=True, i=1, o=options),
            {"k": "d", "gs": 30, "x": [], "a": "bad", "i": 1, "o": options},
            decision(40, 0.2, i=2, o=options, p=0.1), {"k": "death", "gs": 70},
            decision(80, 0.4, i=0, o=options), decision(120, 0.6, i=1, o=options)]
    feats = ["pick"] + ["unused%d" % i for i in range(39)]
    with tempfile.TemporaryDirectory() as d:
        path = os.path.join(d, "game.jsonl.gz")
        with gzip.open(path, "wt", encoding="utf-8") as fh:
            for row in data:
                fh.write(json.dumps(row) + "\n")
        choices = []
        rows = list(train.examples([path], feats, 40, choices))
        assert rows == list(train.examples([path], feats, 40))
        assert len(rows) == 3 and all(len(r) == 5 for r in rows), rows
        assert choices == [(0, None), (2, options), (0, options)], choices
        assert np.allclose([r[3] for r in rows], [0.6, -2.4, 0.6]), rows
        assert [r[4] for r in rows] == [1.0, train.MAX_WEIGHT, 1.0]


def training_uses_only_test_deviations():
    test, test_choices = synthetic()
    tr = [("collect:log", [0.0] * 40, 1.0, 1.0)] * (20 * mlp.MIN_HEAD_ROWS)
    va = tr[:train.MIN_ROWS]
    part = {"training": 0.35, "validation": 0.2, **{r[3]: 0.1 for r in test}}
    rows, choices = [], []
    # Interleave the splits: filtering metadata separately must preserve alignment.
    for i in range(len(tr)):
        rows.append(("training", *tr[i]))
        choices.append((1, ["explore:any", "collect:log"]))
        if i < len(va):
            rows.append(("validation", *va[i]))
            choices.append((0, None))
        if i < len(test):
            k, x, y, f = test[i]
            rows.append((f, k, x, y, 1.0))
            choices.append(test_choices[i])
    exported = {"heads": HEADS, "params": 1}
    with mock.patch.object(mlp, "fit", return_value=([], [])) as fit, \
            mock.patch.object(mlp, "heads_for", return_value=HEADS), \
            mock.patch.object(mlp, "predict", side_effect=fake_predictor), \
            mock.patch.object(mlp, "export", return_value=exported), \
            mock.patch.object(gbt, "fit", return_value={}), \
            mock.patch.object(gbt, "predict", side_effect=lambda m, X: X[:, 1]):
        net, stats = train.train_mlp(rows, part, choices)
        assert net == exported and stats["sign"] == check(test, test_choices), stats
        assert len(fit.call_args.args[0]) == len(tr) and len(fit.call_args.args[1]) == len(va)
        _, old_stats = train.train_mlp(rows, part)
        assert old_stats["sign"]["n"] == 0
    net, stats = train.train_mlp([], {}, [])
    assert net is None and stats["sign"]["n"] == 0


def main_writes_and_prints_sign(sign):
    net = {"heads": HEADS, "params": 1}
    thin = dict(sign, n=12, games=4, agree=None, lo=None, mean_gap_ok=None, mean_gap_bad=None)
    for result, suffix in [(sign, "sign check %.2f (5%% bound %.2f) on 400 deviations in 20 games" %
                           (sign["agree"], sign["lo"])),
                           (thin, "sign check: too few deviations yet (n=12)")]:
        with tempfile.TemporaryDirectory() as d:
            state_path = os.path.join(d, "state.json")
            train.common.write_json(state_path, {"gen": 7})
            stats = {"r2": 0.1, "adv_r2": 0.01, "adv_lo": 0.02, "sign": result}
            output = io.StringIO()
            with mock.patch.object(sys, "argv", ["train.py", "--state", d]), \
                    mock.patch.object(train.common, "load_features", return_value=["pick"]), \
                    mock.patch.object(train, "train_mlp", return_value=(net, stats)), \
                    mock.patch.object(train, "train_hazard", return_value=None), \
                    mock.patch.object(train.skillstats, "train_skill_stats", return_value={}), \
                    contextlib.redirect_stdout(output):
                train.main()
            learned = train.common.read_json(os.path.join(d, "learned.json"), {})
            state = train.common.read_json(state_path, {})
            assert learned["sign_check"] == state["model_sign"] == result
            assert output.getvalue().splitlines()[-1] == (
                "learned model m7 (mlp): 0 decisions from 0 runs, 4 action kinds, "
                "held-out R2 0.1 (choice part 0.01, 5% bound 0.02), " + suffix), output.getvalue()


def gate_review_fixes():
    """Codex's review of the gate (2026-09-30): worlds, not files; state shifts aren't choice skill;
    no bound is a closed gate."""
    import loop
    # One world per generation and task: four genomes on seed 0 are one unit; a data run is its own.
    g = train.game_group
    assert g("d/gen-00086/eval-g56-0.jsonl.gz") == g("d/gen-00086/eval-g64-0.jsonl.gz") == "gen-00086/eval-0"
    assert g("d/gen-00086/eval-g56-1.jsonl.gz") != g("d/gen-00087/eval-g56-1.jsonl.gz")
    assert g("d/gen-00086/data-g56-0.jsonl.gz") == "d/gen-00086/data-g56-0.jsonl.gz"
    # Identical heads over a wrong _v: no choice skill, so the gate's pairs predict nothing.
    col = {h: i for i, h in enumerate(HEADS)}
    rng = random.Random(3)
    te, choices, out, resid = [], [], [], []
    for game in range(20):
        for _ in range(10):
            te.append(("collect:log", [0.0] * 40, 1.0, "g%d" % game))
            choices.append((1, ["explore:any", "collect:log"]))
            out.append([0.0, 1.0, 1.0, 1.0, 1.0])
            resid.append(1.0 + rng.choice([-0.1, 0.1]))
    pairs, groups = train.choice_part(te, choices, np.array(out), col, np.array(resid))
    assert len(pairs) == 200 and all(p == 0 for p, _ in pairs)
    lo = train.gain_lower_bound(pairs, groups)
    assert lo is not None and lo <= 0, lo
    # Rows without options say nothing about the choice.
    pairs, _ = train.choice_part(te, [(1, None)] * len(te), np.array(out), col, np.array(resid))
    assert pairs == []
    # An MLP without a bound (too few held-out worlds) keeps the learned genes out.
    genes = {"learned.weight": {"def": 0, "min": 0, "max": 3, "kind": "REAL"}, "a.b": {"def": 0, "min": 0, "max": 1, "kind": "BOOL"}}
    st = {"model_kind": "mlp", "model_adv_lo": None, "model_adv_r2": 0.2, "settings": {"model_gate": 0.1},
          "credit": {n: {"n": 0, "sum": 0.0} for n in genes}, "sigma": {}}
    picks = {n for s in range(50) for n in loop.pick_genes(st, genes, random.Random(s), 1)}
    assert "learned.weight" not in picks, picks


def main():
    gate_review_fixes()
    sign = known_signs_and_whole_games()
    unusable_rows_are_skipped()
    thin_data_and_zero_outcomes()
    random_signs_are_chance()
    examples_keep_choices_aligned()
    training_uses_only_test_deviations()
    main_writes_and_prints_sign(sign)
    print("signcheck ok")


if __name__ == "__main__":
    main()
