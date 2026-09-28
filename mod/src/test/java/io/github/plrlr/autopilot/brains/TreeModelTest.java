package io.github.plrlr.autopilot.brains;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** The tree format gbt.py writes: evaluation matches its predict_tree, and bad files are refused. */
class TreeModelTest {
	// Tree 1: x[0] <= 0.5 ? (x[1] <= 0.25 ? 1 : 2) : 3.  Tree 2: a single leaf, 0.5.
	private static final String TWO_TREES = """
			{"trees": [
			  {"f": [0, 1, -1, -1, -1], "t": [0.5, 0.25, 0, 0, 0], "l": [1, 2, 0, 0, 0], "r": [4, 3, 0, 0, 0], "v": [0, 0, 1, 2, 3]},
			  {"f": [-1], "t": [0], "l": [0], "r": [0], "v": [0.5]}
			]}""";

	private static JsonObject json(String s) {
		return JsonParser.parseString(s).getAsJsonObject();
	}

	@Test
	void sumsTheLeavesEachTreeReaches() {
		TreeModel m = TreeModel.parse(json(TWO_TREES), 2);
		assertEquals(1.5, m.predict(new double[]{0.2, 0.1}), 1e-9);
		assertEquals(2.5, m.predict(new double[]{0.5, 0.9}), 1e-9); // <= goes left, as in numpy
		assertEquals(3.5, m.predict(new double[]{0.7, 0.0}), 1e-9);
	}

	@Test
	void refusesChildrenThatPointBackwards() {
		String loop = """
				{"trees": [{"f": [0, -1], "t": [0.5, 0], "l": [0, 0], "r": [1, 0], "v": [0, 1]}]}""";
		assertThrows(IllegalArgumentException.class, () -> TreeModel.parse(json(loop), 2));
	}

	@Test
	void refusesAFeatureTheStateDoesNotHave() {
		String wide = """
				{"trees": [{"f": [5, -1, -1], "t": [0.5, 0, 0], "l": [1, 0, 0], "r": [2, 0, 0], "v": [0, 1, 2]}]}""";
		assertThrows(IllegalArgumentException.class, () -> TreeModel.parse(json(wide), 2));
	}
}
