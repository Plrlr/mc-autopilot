package io.github.plrlr.autopilot.brains;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import io.github.plrlr.autopilot.plan.Option;

import java.util.HashMap;
import java.util.Map;

/**
 * The learned danger model: the chance of dying in the next 45 game seconds after taking an
 * option in a state (scripts/loop/train.py, retrained every generation on every game played).
 * Input: the state features, then a one-hot of the option's action kind ("retreat",
 * "attack:zombie", "shelter:heal"...), so the trees can learn that fleeing a skeleton at 5 hearts
 * is deadlier than walling in. Held-out AUC 0.87 on the first 598 loop games (2026-09-28).
 */
final class HazardModel {
	private final TreeModel trees;
	private final Map<String, Integer> keys = new HashMap<>();
	private final int width;
	final double auc;

	/** Reads {"keys": [...], "trees": [...], "auc": ...}; throws on anything malformed. */
	HazardModel(JsonObject o, int features) {
		JsonArray k = o.getAsJsonArray("keys");
		for (int i = 0; i < k.size(); i++) keys.put(k.get(i).getAsString(), features + i);
		width = features + k.size();
		trees = TreeModel.parse(o, width);
		auc = o.has("auc") && !o.get("auc").isJsonNull() ? o.get("auc").getAsDouble() : Double.NaN;
	}

	/** Chance of death within the horizon, 0..1. An action kind the model never saw uses the state alone. */
	double risk(Option option, double[] x) {
		double[] v = new double[width];
		System.arraycopy(x, 0, v, 0, Math.min(x.length, v.length));
		Integer i = keys.get(Learned.key(option));
		if (i != null) v[i] = 1;
		return Math.max(0, Math.min(1, trees.predict(v)));
	}
}
