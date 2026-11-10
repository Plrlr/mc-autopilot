package io.github.plrlr.autopilot.brains;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** 2026-11-09: the learned.gate check reads learned.json's held-out fields; anything odd = closed. */
class ModelGateTest {
	private static String json(String r2, String agree, String n) {
		return "{\"id\":\"m1\",\"adv_r2_heldout\":" + r2 + ",\"sign_check\":{\"agree\":" + agree + ",\"lo\":0.5,\"n\":" + n + ",\"games\":40}}";
	}

	private static boolean open(String text) {
		return ModelGate.parse(text).passes(0.05, 0.6, 500);
	}

	@Test
	void passesWhenAllThreeHold() {
		assertTrue(open(json("0.08", "0.66", "800")));
		assertTrue(open(json("0.08", "0.66", "500"))); // exactly the minimum count is enough
	}

	@Test
	void failsOnEachCondition() {
		assertFalse(open(json("-0.007", "0.66", "800")), "R2");
		assertFalse(open(json("0.05", "0.66", "800")), "R2 must be strictly above");
		assertFalse(open(json("0.08", "0.557", "800")), "sign");
		assertFalse(open(json("0.08", "0.66", "499")), "count");
	}

	@Test
	void missingFieldsAreClosed() {
		assertFalse(open("{\"id\":\"m1\"}"));
		assertFalse(open("{\"adv_r2_heldout\":0.2}"));
		assertFalse(open(json("null", "0.66", "800")));
		assertFalse(open(json("0.08", "null", "800")));
		assertFalse(open("{\"adv_r2_heldout\":0.2,\"sign_check\":null}"));
		assertFalse(open("{\"adv_r2_heldout\":0.2,\"sign_check\":{\"agree\":0.7}}"));
	}

	@Test
	void garbageIsClosed() {
		assertFalse(open(""));
		assertFalse(open("not json {"));
		assertFalse(open("[1,2]"));
		assertFalse(open(json("\"x\"", "0.66", "800")));
	}

	@Test
	void anUnloadedGateIsClosed() {
		assertFalse(new ModelGate().open());
	}
}
