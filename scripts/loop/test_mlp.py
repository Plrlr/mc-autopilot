"""mlp.py: the brain has 100k+ parameters, and it learns a choice effect that the data does hold."""
import os
import random
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import mlp  # noqa: E402
import train  # noqa: E402


def synthetic(n, rng, noise=0.3, rules=False):
    """Two actions over 40 features: "collect:log" pays off when x0 is high, "explore:any" when it's low.
    rules=True: the choice follows a habit (x2), never a deliberate try, as in most loop games."""
    rows = []
    for _ in range(n):
        x = [rng.random() for _ in range(40)]
        k = ("collect:log" if x[2] > 0.5 else "explore:any") if rules else rng.choice(["collect:log", "explore:any"])
        y = (2 * x[0] if k == "collect:log" else 2 * (1 - x[0])) + x[1] + rng.gauss(0, noise)
        rows.append((k, x, y, 1.0))
    return rows


def main():
    rng = random.Random(1)
    tr, va = synthetic(6000, rng), synthetic(1500, rng)
    heads = mlp.heads_for(tr)
    assert heads[0] == "_v" and "collect:log" in heads and "collect" in heads, heads
    sizes = [40, *mlp.HIDDEN, 60]
    assert mlp.param_count(sizes) >= 100_000, mlp.param_count(sizes)
    layers, sizes = mlp.fit(tr, va, heads, 1.0, epochs=15, budget_s=60)
    col = {h: i for i, h in enumerate(heads)}
    lo, hi = [0.1] + [0.5] * 39, [0.9] + [0.5] * 39
    out = mlp.predict(layers, heads, 1.0, [lo, hi])
    log, exp = col["collect:log"], col["explore:any"]
    assert out[0, exp] > out[0, log] + 0.8, out  # low x0: exploring is better
    assert out[1, log] > out[1, exp] + 0.8, out  # high x0: collecting is better
    e = mlp.export(layers, sizes, heads, 1.0)
    assert e["params"] == mlp.param_count(sizes) and len(e["layers"]) == 4
    gate_sees_a_real_choice_effect()
    print("ok: %d parameters, choice effect learned" % mlp.param_count([40, *mlp.HIDDEN, 60]))


def gate_sees_a_real_choice_effect():
    """The bug of 2026-09-30: with the rules' habits and noisy outcomes the net learns the better
    action, yet its choice R2 stays near 0.02, so the fixed 0.1 bar benched it forever. The
    lower bound over games must see the effect."""
    rng = random.Random(3)
    tr, va, te = (synthetic(n, rng, noise=1.0, rules=True) for n in (8000, 2000, 3000))
    heads = mlp.heads_for(tr)
    layers, _ = mlp.fit(tr, va, heads, 1.0, epochs=15, budget_s=60)
    col = {h: i for i, h in enumerate(heads)}
    out = mlp.predict(layers, heads, 1.0, [r[1] for r in te])
    base = [r[1][1] + 1.0 for r in te]  # the state's part: x1, plus the mean of either action
    adv = [(out[i, col[k]] - out[i, 0], y - base[i]) for i, (k, _, y, _) in enumerate(te)]
    games = ["g%d" % (i // 30) for i in range(len(te))]
    assert train.rsq(adv) < 0.1, train.rsq(adv)  # the old bar: benched
    lo = train.gain_lower_bound(adv, games)
    assert lo is not None and lo > 0, lo  # the new check: passes


if __name__ == "__main__":
    main()
