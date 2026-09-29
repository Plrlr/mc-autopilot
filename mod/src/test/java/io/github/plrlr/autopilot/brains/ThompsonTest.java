package io.github.plrlr.autopilot.brains;

import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ThompsonTest {
	@Test
	void betaDrawsCenterOnTheMeanAndNarrowWithData() {
		Random rng = new Random(7);
		double sumWide = 0, sumNarrow = 0, sqWide = 0, sqNarrow = 0;
		int n = 4000;
		for (int i = 0; i < n; i++) {
			double w = Thompson.beta(2, 2, rng), s = Thompson.beta(151, 151, rng);
			sumWide += w;
			sqWide += w * w;
			sumNarrow += s;
			sqNarrow += s * s;
		}
		assertEquals(0.5, sumWide / n, 0.02);
		assertEquals(0.5, sumNarrow / n, 0.01);
		double sdWide = Math.sqrt(sqWide / n - Math.pow(sumWide / n, 2));
		double sdNarrow = Math.sqrt(sqNarrow / n - Math.pow(sumNarrow / n, 2));
		assertTrue(sdWide > 5 * sdNarrow, "3 tries leave the chance uncertain, 300 pin it down");
	}

	@Test
	void drawsAreHeldSoRoutesDontFlipEverySecond() {
		Thompson.clear();
		SkillStats.Estimate e = new SkillStats.Estimate(0.5, 0.1, 0.9, 30, 30, 0, 4);
		double a = Thompson.draw("k", e, 1000).p();
		assertEquals(a, Thompson.draw("k", e, 1000 + Thompson.HOLD_MS - 1).p());
		Thompson.clear();
	}
}
