package io.github.plrlr.autopilot.skills;

/**
 * Why a skill failed, as a fixed code: logs, lessons and batch summaries count these instead of
 * free text. The detail string that goes with it is only for people reading one example.
 */
public enum Fail {
	/** A required item is missing or ran out (ingredients, fuel, a bucket, blocks). */
	NEED_ITEM,
	/** No recipe or source is known for the item. */
	NO_RECIPE,
	/** The target isn't in sight or in memory (mob, block, water, death spot). */
	NOT_FOUND,
	/** Nothing to do: what the skill looks for is already here. */
	ALREADY_DONE,
	/** The target is known but the way there failed. */
	UNREACHABLE,
	/** Working, but nothing gained for too long (mining, smelting). */
	NO_PROGRESS,
	/** No suitable free space (to place a station, a bed, a portal, a shelter). */
	NO_ROOM,
	/** A block placement click didn't produce the block. */
	PLACE_FAILED,
	/** Using an item or block didn't work (bucket, flint and steel, bed, furnace). */
	USE_FAILED,
	/** Wrong dimension or time of day for this skill. */
	WRONG_PLACE,
	/** Danger ahead (open water, lava while digging). */
	HAZARD,
	/** The inventory has no room. */
	INVENTORY_FULL,
	/** The skill's own time limit ran out. */
	TIMEOUT,
	/** Standing still while Baritone was trying to walk. */
	STUCK,
	/** Stopped from outside: a reflex, the brain, the user, the end of a test. */
	INTERRUPTED,
	/** The player died during the skill. */
	DIED,
	/** An exception in our code. */
	ERROR
}
