package io.github.plrlr.autopilot.skills;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;

/** The tactician's menu: skill name -> a fresh instance, plus the one-line description the brains see. */
public final class Skills {
	private Skills() {}

	public record Entry(Supplier<Skill> make, String help) {}

	public static final Map<String, Entry> MENU = new LinkedHashMap<>();

	static {
		MENU.put("collect", new Entry(CollectSkill::new, "mine blocks for item:n (logs, stone, ores, gravel...) with the right pickaxe"));
		MENU.put("craft", new Entry(CraftSkill::new, "craft item:n from the recipe book; uses a crafting table for 3x3 recipes"));
		MENU.put("smelt", new Entry(SmeltSkill::new, "smelt output:n in a furnace (iron_ingot, cooked meat, charcoal)"));
		MENU.put("attack", new Entry(CombatSkills.Attack::new, "melee the nearest mob of that type in sight (also hunting)"));
		MENU.put("shoot", new Entry(CombatSkills.Shoot::new, "bow at a far target (end crystals, dragon)"));
		MENU.put("eat", new Entry(InventorySkills.Eat::new, "eat the best food in the inventory"));
		MENU.put("equip", new Entry(InventorySkills.Equip::new, "wear best armor, shield to off hand, or hold best weapon/pickaxe"));
		MENU.put("place", new Entry(InventorySkills.Place::new, "place a crafting_table, furnace, chest or torch next to you"));
		MENU.put("pickup", new Entry(MoveSkills.Pickup::new, "walk over dropped items nearby"));
		MENU.put("explore", new Entry(MoveSkills.Explore::new, "walk ~80 blocks in a direction to find new things"));
		MENU.put("goto", new Entry(MoveSkills.Goto::new, "walk to a remembered block (crafting_table, furnace, nether_bricks...)"));
		MENU.put("retreat", new Entry(MoveSkills.Retreat::new, "run away from nearby monsters"));
		MENU.put("shelter", new Entry(NightSkills.Shelter::new, "dig 3 down, cover the hole, wait for morning"));
		MENU.put("sleep", new Entry(NightSkills.Sleep::new, "sleep in a bed (places one from the inventory if needed)"));
		MENU.put("build_portal", new Entry(() -> CastPortal.needed() ? new CastPortal() : new PortalSkills.BuildPortal(),
				"build and light a nether portal: places 10 obsidian, or casts it from a lava pool with two buckets"));
		MENU.put("enter_portal", new Entry(PortalSkills.EnterPortal::new, "walk into a known nether or end portal"));
		MENU.put("locate_stronghold", new Entry(PortalSkills.LocateStronghold::new, "throw an eye of ender and walk where it points"));
		MENU.put("fill_end_portal", new Entry(PortalSkills.FillEndPortal::new, "put eyes of ender into empty end portal frames"));
		MENU.put("fill_bucket", new Entry(BucketSkills.FillBucket::new, "fill an empty bucket at a known water or lava source"));
		MENU.put("fortress", new Entry(NetherSkills.Fortress::new, "Nether: find (walk and look for a fortress) or blazes:n (fight blazes at the spawner until n rods)"));
		MENU.put("dragon", new Entry(DragonFight::new, "the End fight: stay off the edges, wait by the fountain, hit the dragon's head when it lands"));
		MENU.put("unstuck", new Entry(Unstuck::new, "get out of a spot the bot stopped moving in: swim, walk, tunnel or climb (a reflex)"));
		MENU.put("make_obsidian", new Entry(BucketSkills.MakeObsidian::new, "pour a water bucket next to lava pool sources to harden them into obsidian"));
	}

	public static Skill create(String name) {
		Entry e = MENU.get(name);
		return e == null ? null : e.make().get();
	}
}
