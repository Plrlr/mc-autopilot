package io.github.plrlr.autopilot.brains;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

/**
 * One action kind's advantage model: a sum of small regression trees, as scripts/loop/gbt.py
 * writes them. Each tree is flat arrays, one entry per node: f = -1 marks a leaf with value v;
 * otherwise go left when x[f] <= t. A few hundred comparisons per option: microseconds.
 */
final class TreeModel {
	private final int[][] f, l, r;
	private final double[][] t, v;

	private TreeModel(int n) {
		f = new int[n][];
		l = new int[n][];
		r = new int[n][];
		t = new double[n][];
		v = new double[n][];
	}

	/** Reads {"trees": [...]}; throws on anything malformed (the caller then ignores the model). */
	static TreeModel parse(JsonObject o, int features) {
		JsonArray trees = o.getAsJsonArray("trees");
		TreeModel m = new TreeModel(trees.size());
		for (int k = 0; k < trees.size(); k++) {
			JsonObject tr = trees.get(k).getAsJsonObject();
			m.f[k] = ints(tr.getAsJsonArray("f"));
			m.l[k] = ints(tr.getAsJsonArray("l"));
			m.r[k] = ints(tr.getAsJsonArray("r"));
			m.t[k] = doubles(tr.getAsJsonArray("t"));
			m.v[k] = doubles(tr.getAsJsonArray("v"));
			int n = m.f[k].length;
			if (n == 0 || m.l[k].length != n || m.r[k].length != n || m.t[k].length != n || m.v[k].length != n)
				throw new IllegalArgumentException("tree " + k + ": node arrays differ in length");
			// Children must point forward (gbt.py numbers a node before its children), so a bad file
			// can't loop forever on the game thread.
			for (int i = 0; i < n; i++) {
				if (m.f[k][i] < 0) continue;
				if (m.f[k][i] >= features || m.l[k][i] <= i || m.r[k][i] <= i || m.l[k][i] >= n || m.r[k][i] >= n)
					throw new IllegalArgumentException("tree " + k + ": bad node " + i);
			}
		}
		return m;
	}

	double predict(double[] x) {
		double sum = 0;
		for (int k = 0; k < f.length; k++) {
			int i = 0;
			while (f[k][i] >= 0) i = x[f[k][i]] <= t[k][i] ? l[k][i] : r[k][i];
			sum += v[k][i];
		}
		return sum;
	}

	private static int[] ints(JsonArray a) {
		int[] out = new int[a.size()];
		int i = 0;
		for (JsonElement e : a) out[i++] = e.getAsInt();
		return out;
	}

	private static double[] doubles(JsonArray a) {
		double[] out = new double[a.size()];
		int i = 0;
		for (JsonElement e : a) out[i++] = e.getAsDouble();
		return out;
	}
}
