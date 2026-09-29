package io.github.plrlr.autopilot.plan;

import com.google.gson.JsonObject;
import io.github.plrlr.autopilot.Tune;
import io.github.plrlr.autopilot.state.WorldMemory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class NetherWaveTest {
	@AfterEach
	void reset() {
		Tune.reset();
	}

	@Test
	void theFortressSearchIsUnchangedWithTheGeneOff() {
		Option find = NetherPlan.blazeStep(false, false);
		assertSame(find, NetherPlan.withSkills(find, new WorldMemory()));
		Option blazes = NetherPlan.blazeStep(true, false);
		assertSame(blazes, NetherPlan.withSkills(blazes, new WorldMemory()));
	}

	@Test
	void withTheGeneTheScoutLooksFromAVantage() {
		JsonObject g = new JsonObject();
		g.addProperty("skill.fortress_scout", 1);
		Tune.apply(g, "test");
		assertEquals("fortress_scout", NetherPlan.withSkills(NetherPlan.blazeStep(false, false), new WorldMemory()).skill());
	}
}
