"""mlp.py: the brain has 100k+ parameters, and it learns a choice effect that the data does hold."""
import os
import random
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import mlp  # noqa: E402


def synthetic(n, rng):
    """Two actions over 40 features: "collect:log" pays off when x0 is high, "explore:any" when it's low."""
    rows = []
    for _ in range(n):
        x = [rng.random() for _ in range(40)]
        k = rng.choice(["collect:log", "explore:any"])
        y = (2 * x[0] if k == "collect:log" else 2 * (1 - x[0])) + x[1] + rng.gauss(0, 0.3)
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
    print("ok: %d parameters, choice effect learned" % mlp.param_count([40, *mlp.HIDDEN, 60]))


if __name__ == "__main__":
    main()
