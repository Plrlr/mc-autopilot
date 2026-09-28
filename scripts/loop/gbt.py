"""Gradient-boosted regression trees in plain numpy, small enough to read and to run in Java.

Why not a library: the model is evaluated inside the game (brains/Learned.java), so the trees must
come out in a format we control, and the loop's machines only have numpy. A straight line per
action (the old model) can't learn combinations such as "fighting pays at full health but not at
4 hearts with 3 zombies around"; a tree splits on health, then on the number of mobs.

Histogram method: each feature is cut into at most 32 bins once, then each split is found from
per-bin sums, so one tree over 40k rows takes milliseconds. Squared loss, per-row weights,
shrinkage, L2 on leaf values, and early stopping on a held-out set.

Exported tree: {"f": [...], "t": [...], "l": [...], "r": [...], "v": [...]}, one entry per node;
f = -1 marks a leaf with value v; otherwise go left when x[f] <= t. Prediction = bias + sum(trees).
"""
import numpy as np

BINS = 32


def bin_edges(X):
    """Split points per feature: midpoints between distinct values, thinned to quantiles."""
    edges = []
    for j in range(X.shape[1]):
        u = np.unique(X[:, j])
        if len(u) <= 1:
            edges.append(np.array([]))
            continue
        mids = (u[:-1] + u[1:]) / 2
        if len(mids) > BINS - 1:
            mids = np.unique(np.quantile(X[:, j], np.linspace(0, 1, BINS + 1)[1:-1]))
        edges.append(mids)
    return edges


def binned(X, edges):
    B = np.zeros(X.shape, dtype=np.int16)
    for j, e in enumerate(edges):
        if len(e):
            B[:, j] = np.searchsorted(e, X[:, j], side="left")
    return B


def _tree(B, g, w, edges, depth, min_leaf, lam):
    """Fits one tree to targets g (weights w) on rows indexed; returns node lists."""
    nodes = {"f": [], "t": [], "l": [], "r": [], "v": []}

    def add():
        for k in nodes:
            nodes[k].append(0 if k != "f" else -1)
        return len(nodes["f"]) - 1

    def grow(rows, d):
        me = add()
        sw, sg = w[rows].sum(), (w[rows] * g[rows]).sum()
        nodes["v"][me] = float(sg / (sw + lam))
        if d == depth or len(rows) < 2 * min_leaf:
            return me
        base = sg * sg / (sw + lam)
        best = (1e-9, None, None)
        wr, gr = w[rows], w[rows] * g[rows]
        for j, e in enumerate(edges):
            if not len(e):
                continue
            b = B[rows, j]
            hw = np.bincount(b, weights=wr, minlength=len(e) + 1)
            hg = np.bincount(b, weights=gr, minlength=len(e) + 1)
            hn = np.bincount(b, minlength=len(e) + 1)
            cw, cg, cn = np.cumsum(hw)[:-1], np.cumsum(hg)[:-1], np.cumsum(hn)[:-1]
            ok = (cn >= min_leaf) & (len(rows) - cn >= min_leaf)
            if not ok.any():
                continue
            gain = cg ** 2 / (cw + lam) + (sg - cg) ** 2 / (sw - cw + lam) - base
            gain[~ok] = -1
            k = int(np.argmax(gain))
            if gain[k] > best[0]:
                best = (gain[k], j, k)
        if best[1] is None:
            return me
        j, k = best[1], best[2]
        left = rows[B[rows, j] <= k]
        right = rows[B[rows, j] > k]
        nodes["f"][me] = j
        nodes["t"][me] = float(edges[j][k])
        nodes["l"][me] = grow(left, d + 1)
        nodes["r"][me] = grow(right, d + 1)
        return me

    grow(np.arange(len(g)), 0)
    return nodes


def predict_tree(t, X):
    out = np.zeros(len(X))
    node = np.zeros(len(X), dtype=np.int64)
    f, th, l, r, v = (np.array(t[k]) for k in ("f", "t", "l", "r", "v"))
    for _ in range(64):
        leaf = f[node] < 0
        if leaf.all():
            break
        fj = np.where(leaf, 0, f[node])
        go_left = X[np.arange(len(X)), fj] <= th[node]
        node = np.where(leaf, node, np.where(go_left, l[node], r[node]))
    out[:] = v[node]
    return out


def fit(X, y, w, Xv=None, yv=None, wv=None, trees=300, depth=4, lr=0.05, min_leaf=40, lam=10.0,
        patience=30, init=None):
    """Boosting from `init` (a per-row starting prediction, e.g. a baseline) or the weighted mean.
    Stops when the held-out weighted error hasn't improved for `patience` trees."""
    edges = bin_edges(X)
    B = binned(X, edges)
    bias = 0.0 if init is not None else float(np.average(y, weights=w))
    pred = (init[0] if init is not None else np.full(len(y), bias)).astype(float)
    pv = None
    if Xv is not None:
        pv = (init[1] if init is not None else np.full(len(yv), bias)).astype(float)
    out, best_err, best_n = [], np.inf, 0
    for _ in range(trees):
        t = _tree(B, y - pred, w, edges, depth, min_leaf, lam)
        t["v"] = [round(lr * v, 6) for v in t["v"]]
        t["t"] = [round(v, 6) for v in t["t"]]
        out.append(t)
        pred += predict_tree(t, X)
        if pv is not None:
            pv += predict_tree(t, Xv)
            err = np.average((yv - pv) ** 2, weights=wv)
            if err < best_err - 1e-9:
                best_err, best_n = err, len(out)
            elif len(out) - best_n >= patience:
                break
    if pv is not None:
        out = out[:best_n]
    return {"bias": round(bias, 6), "trees": out}


def predict(model, X):
    p = np.full(len(X), model["bias"], dtype=float)
    for t in model["trees"]:
        p += predict_tree(t, X)
    return p
