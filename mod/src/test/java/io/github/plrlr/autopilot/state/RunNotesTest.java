package io.github.plrlr.autopilot.state;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** Run notes (gene notes.persist): what survives a restart, and what a bad file must not break. */
class RunNotesTest {
	private static RunNotes.Pos at(String dim, int x, int z) {
		return new RunNotes.Pos(dim, x, 64, z);
	}

	@Test
	void roundTrip() {
		RunNotes n = new RunNotes();
		n.portal(at("overworld", 100, -40));
		n.portal(at("the_nether", 12, -5));
		n.stash(at("overworld", 90, -44));
		n.fortress(at("the_nether", 200, 30));
		n.spawn(at("overworld", 95, -41));
		n.eye(new RunNotes.Eye(1, 70, 2, 0.6, -0.8, 1234));
		RunNotes.Death d = n.death(at("the_nether", 150, 20), "lava", "", 5000, 9);
		d.dangerous = true;
		d.aged = 321;

		RunNotes back = RunNotes.parse(n.toJson());
		assertEquals(n.portals, back.portals);
		assertEquals(n.stashes, back.stashes);
		assertEquals(n.fortress, back.fortress);
		assertEquals(n.spawn, back.spawn);
		assertEquals(n.eyes, back.eyes);
		assertEquals(1, back.deaths.size());
		RunNotes.Death b = back.deaths.get(0);
		assertEquals(d.at, b.at);
		assertEquals("lava", b.cause);
		assertEquals(9, b.value);
		assertTrue(b.dangerous);
		assertEquals(321, b.aged);
	}

	@Test
	void aBadFileStartsEmptyInsteadOfThrowing() {
		for (String text : new String[] {"", "not json", "[]", "{\"deaths\": 7}", "{\"portals\": [{\"x\": \"a\"}]}"}) {
			RunNotes n = assertDoesNotThrow(() -> RunNotes.parse(text), text);
			assertNotNull(n);
			assertTrue(n.deaths.isEmpty(), text);
		}
	}

	@Test
	void listsAreCapped() {
		RunNotes n = new RunNotes();
		for (int i = 0; i < RunNotes.MAX_DEATHS + 20; i++) n.death(at("overworld", i * 100, 0), "mob", "zombie", i, 1);
		for (int i = 0; i < RunNotes.MAX_PORTALS + 20; i++) n.portal(at("overworld", i * 100, 0));
		assertEquals(RunNotes.MAX_DEATHS, n.deaths.size());
		assertTrue(n.portals.size() <= RunNotes.MAX_PORTALS);
		// The newest are the ones kept.
		assertEquals(RunNotes.MAX_DEATHS + 19, n.deaths.get(n.deaths.size() - 1).tick);
	}

	@Test
	void aPlaceIsDangerousAfterTwoDeathsCloseTogether() {
		RunNotes n = new RunNotes();
		n.death(at("overworld", 0, 0), "mob", "zombie", 1, 5);
		assertFalse(n.dangerousPlace(at("overworld", 3, 3), 24), "one death is bad luck");
		n.death(at("overworld", 10, 5), "mob", "skeleton", 2, 5);
		assertTrue(n.dangerousPlace(at("overworld", 3, 3), 24));
		assertFalse(n.dangerousPlace(at("overworld", 300, 300), 24), "elsewhere is fine");
		assertFalse(n.dangerousPlace(at("the_nether", 3, 3), 24), "another dimension's coordinates don't count");
	}

	@Test
	void recoveredDeathsAreNotFetchedAgain() {
		RunNotes n = new RunNotes();
		RunNotes.Death first = n.death(at("overworld", 0, 0), "mob", "zombie", 1, 5);
		RunNotes.Death second = n.death(at("overworld", 50, 0), "fall", "", 2, 5);
		assertSame(second, n.newestUnrecovered());
		n.markRecovered(second);
		// Only the latest death's drops are ever fetched (one clock, one trip): no going back to older ones.
		assertNull(n.newestUnrecovered());
		assertFalse(first.recovered);
		RunNotes.Death third = n.death(at("overworld", 80, 0), "mob", "creeper", 3, 5);
		n.markDangerous(third);
		assertNull(n.newestUnrecovered(), "a place we gave up on isn't offered again");
	}
}
