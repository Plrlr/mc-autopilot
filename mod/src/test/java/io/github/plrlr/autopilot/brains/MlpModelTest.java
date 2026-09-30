package io.github.plrlr.autopilot.brains;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** The network format mlp.py writes: the forward pass matches its predict, and bad files are refused. */
class MlpModelTest {
	// mlp.export of a 2 -> 3 -> 2 net with small exact weights (half precision holds them exactly).
	private static final String TINY = """
			{"sizes": [2, 3, 2], "heads": ["_v", "collect:log"], "params": 17, "layers": [
			  {"w": "ADwAvAA4ADQAQAC8", "b": "AAAAOAC0"},
			  {"w": "ADwAAAC4ADwAQAA6", "b": "ADAAvA=="}]}""";

	private static JsonObject json(String s) {
		return JsonParser.parseString(s).getAsJsonObject();
	}

	@Test
	void matchesNumpy() {
		MlpModel m = MlpModel.parse(json(TINY), 2);
		assertArrayEquals(new double[]{0.4375, -0.5}, m.forward(new double[]{0.5, 0.25}), 1e-6);
		assertArrayEquals(new double[]{0.625, 0.5}, m.forward(new double[]{1, 1}), 1e-6);
		assertArrayEquals(new double[]{-0.5, 0.5}, m.forward(new double[]{0, 0.5}), 1e-6);
		assertEquals(1, m.head("collect:log"));
		assertEquals(-1, m.head("eat"));
		assertEquals(17, m.params());
	}

	@Test
	void refusesAnotherFeatureCount() {
		assertThrows(IllegalArgumentException.class, () -> MlpModel.parse(json(TINY), 3));
	}

	@Test
	void refusesTruncatedWeights() {
		String cut = TINY.replace("ADwAvAA4ADQAQAC8", "ADwAvAA4");
		assertThrows(IllegalArgumentException.class, () -> MlpModel.parse(json(cut), 2));
	}
}
