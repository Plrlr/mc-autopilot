"""The neural brain: a multi-head network, 40 state features -> 256 -> 256 -> 128 -> one output per
action kind, trained in plain numpy (no torch: the loop runs on GitHub's free machines).

Head "_v" predicts the progress that follows a state whatever is chosen; every other head predicts
the progress after choosing its action kind ("collect:log", or "collect" for a skill as a whole).
One pass gives every head, so the mod runs it once per decision (brains/MlpModel.java). About
110k parameters, retrained from all games every generation, so it keeps learning as the bot plays.

Size alone doesn't make it learn which choice is better: that needs games where the choice varied
(the data runs' deliberate tries). The loop's gate still decides whether it plays: its held-out
advantage R2 against the tree baseline must beat model_gate, the same bar the trees had.
"""
import base64
import time

import numpy as np

HIDDEN = (256, 256, 128)
MIN_HEAD_ROWS = 80


def param_count(sizes):
    return sum(a * b + b for a, b in zip(sizes[:-1], sizes[1:]))


def heads_for(rows):
    """"_v" plus every action kind and skill with enough training rows."""
    n = {}
    for k, _, _, _ in rows:
        for g in {k, k.split(":")[0]}:
            n[g] = n.get(g, 0) + 1
    return ["_v"] + sorted(g for g, c in n.items() if c >= MIN_HEAD_ROWS)


def encode(rows, heads, scale):
    """X, the target per head (masked: a row trains "_v", its key's head and its skill's head), weights."""
    col = {h: i for i, h in enumerate(heads)}
    X = np.array([r[1] for r in rows], dtype=np.float32)
    Y = np.zeros((len(rows), len(heads)), dtype=np.float32)
    M = np.zeros_like(Y)
    for i, (k, _, y, w) in enumerate(rows):
        for g in {"_v", k, k.split(":")[0]}:
            j = col.get(g)
            if j is not None:
                Y[i, j] = y / scale
                M[i, j] = w
    return X, Y, M


def init(sizes, rng):
    return [(rng.standard_normal((a, b)).astype(np.float32) * np.float32(np.sqrt(2.0 / a)), np.zeros(b, np.float32))
            for a, b in zip(sizes[:-1], sizes[1:])]


def forward(layers, X):
    acts = [X]
    h = X
    for i, (W, b) in enumerate(layers):
        h = h @ W + b
        if i < len(layers) - 1:
            h = np.maximum(h, 0)
        acts.append(h)
    return acts


def loss(layers, X, Y, M):
    out = forward(layers, X)[-1]
    return float((M * (out - Y) ** 2).sum() / max(1e-9, M.sum()))


def fit(tr, va, heads, scale, seed=7, epochs=40, batch=512, lr=1e-3, wd=1e-4, patience=4, budget_s=360):
    """Adam on masked, weighted squared error; stops early on the validation runs or the time budget."""
    rng = np.random.default_rng(seed)
    Xt, Yt, Mt = encode(tr, heads, scale)
    Xv, Yv, Mv = encode(va, heads, scale)
    sizes = [Xt.shape[1], *HIDDEN, len(heads)]
    layers = init(sizes, rng)
    m = [(np.zeros_like(W), np.zeros_like(b)) for W, b in layers]
    v = [(np.zeros_like(W), np.zeros_like(b)) for W, b in layers]
    best, best_layers, bad, step = loss(layers, Xv, Yv, Mv), [(W.copy(), b.copy()) for W, b in layers], 0, 0
    t0 = time.time()
    b1, b2 = 0.9, 0.999
    for _ in range(epochs):
        order = rng.permutation(len(Xt))
        for s in range(0, len(order), batch):
            idx = order[s:s + batch]
            X, Y, M = Xt[idx], Yt[idx], Mt[idx]
            acts = forward(layers, X)
            g = 2 * M * (acts[-1] - Y) / max(1e-9, M.sum())
            step += 1
            for i in range(len(layers) - 1, -1, -1):
                W, b = layers[i]
                gW, gb = acts[i].T @ g + wd * W, g.sum(0)
                if i > 0:
                    g = (g @ W.T) * (acts[i] > 0)
                for j, (p, gp) in enumerate(((W, gW), (b, gb))):
                    m[i][j][...] = b1 * m[i][j] + (1 - b1) * gp
                    v[i][j][...] = b2 * v[i][j] + (1 - b2) * gp * gp
                    mh = m[i][j] / (1 - b1 ** step)
                    vh = v[i][j] / (1 - b2 ** step)
                    p -= lr * mh / (np.sqrt(vh) + 1e-8)
        cur = loss(layers, Xv, Yv, Mv)
        if cur < best - 1e-5:
            best, best_layers, bad = cur, [(W.copy(), b.copy()) for W, b in layers], 0
        else:
            bad += 1
        if bad >= patience or time.time() - t0 > budget_s:
            break
    return best_layers, sizes


def predict(layers, heads, scale, X):
    """Outputs in progress units, one column per head."""
    return forward(layers, np.asarray(X, dtype=np.float32))[-1] * scale


def b64(a):
    return base64.b64encode(np.asarray(a, dtype="<f2").tobytes()).decode("ascii")


def export(layers, sizes, heads, scale):
    """Half-precision weights, base64 (~220 KB for 110k parameters). The last layer is scaled back to
    progress units so the mod compares heads like it compared trees. W is stored input-major."""
    out = []
    for i, (W, b) in enumerate(layers):
        if i == len(layers) - 1:
            W, b = W * scale, b * scale
        out.append({"w": b64(W), "b": b64(b)})
    return {"sizes": sizes, "heads": heads, "params": param_count(sizes), "layers": out}
