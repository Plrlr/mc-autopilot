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
		MENU.put("shore", new Entry(ShoreSkill::new, "swim to seen dry ground with room to work"));
		MENU.put("goto", new Entry(MoveSkills.Goto::new, "walk to a remembered block (crafting_table, furnace, nether_bricks...)"));
		MENU.put("retreat", new Entry(MoveSkills.Retreat::new, "run away from nearby monsters"));
		MENU.put("kite", new Entry(Kite::new, "face a close melee pursuer, back onto safe ground, and strike when charged"));
		MENU.put("pillar", new Entry(Pillar::new, "jump onto three placed blocks above melee mobs and descend after they leave"));
		MENU.put("block_arrows", new Entry(BlockArrows::new, "close in on a skeleton behind a raised shield, strike when in reach"));
		MENU.put("creeper_defuse", new Entry(CreeperDefuse::new, "sprint clear of a lit creeper, or knock it back with a sprint hit and back off"));
		MENU.put("panic_box", new Entry(PanicBox::new, "box in with 9 blocks right here and heal (any ground; not near creepers)"));
		MENU.put("respawn_reset", new Entry(RespawnReset::new, "just respawned: at night dig or box in until morning"));
		MENU.put("recover_items", new Entry(RecoverItems::new, "go back for dropped items: stop short, clear the spot, then pick up"));
		MENU.put("make_bed", new Entry(MakeBed::new, "make_bed:n: hunt sheep for wool and craft n beds"));
		MENU.put("food_secure", new Entry(FoodSecure::new, "food_secure:n: cook, hunt, walk back to animals seen, fish, or explore until n food"));
		MENU.put("fish", new Entry(Fish::new, "fish:n: catch n fish with a rod at known water"));
		MENU.put("secure_camp", new Entry(SecureCamp::new, "torch the dark ground around us before long work in one spot"));
		MENU.put("stair_down", new Entry(StairDown::new, "stair_down <y>: dig safe 1-wide stairs down to y"));
		MENU.put("shelter", new Entry(NightSkills.Shelter::new, "dig 3 down, cover the hole, wait for morning"));
		MENU.put("sleep", new Entry(NightSkills.Sleep::new, "sleep in a bed (places one from the inventory if needed)"));
		MENU.put("build_portal", new Entry(() -> CastPortal.needed() ? new CastPortal() : new PortalSkills.BuildPortal(),
				"build and light a nether portal: places 10 obsidian, or casts it from a lava pool with two buckets"));
		MENU.put("enter_portal", new Entry(PortalSkills.EnterPortal::new, "walk into a known nether or end portal"));
		MENU.put("locate_stronghold", new Entry(PortalSkills.LocateStronghold::new, "throw an eye of ender and walk where it points"));
		MENU.put("fill_end_portal", new Entry(PortalSkills.FillEndPortal::new, "put eyes of ender into empty end portal frames"));
		MENU.put("fill_bucket", new Entry(BucketSkills.FillBucket::new, "fill an empty bucket at a known water or lava source"));
		MENU.put("fortress", new Entry(NetherSkills.Fortress::new, "Nether: find (walk and look for a fortress) or blazes:n (fight blazes at the spawner until n rods)"));
		MENU.put("barter", new Entry(Barter::new, "Nether: trade gold ingots with adult piglins for ender pearls (wear gold armor)"));
		MENU.put("enderman_boat", new Entry(EndermanBoat::new, "trap an enderman in a boat (it can't move, teleport or hit back), kill it, take the boat back"));
		MENU.put("search_stronghold", new Entry(SearchStronghold::new, "inside a stronghold: explore the corridors in sight until the portal room shows up"));
		MENU.put("clutch", new Entry(Clutch::new, "falling far: pour the water bucket just before landing, then scoop it back (a reflex)"));
		MENU.put("dragon", new Entry(DragonFight::new, "the End fight: stay off the edges, wait by the fountain, hit the dragon's head when it lands"));
		MENU.put("unstuck", new Entry(Unstuck::new, "get out of a spot the bot stopped moving in: swim, walk, tunnel or climb (a reflex)"));
		MENU.put("make_obsidian", new Entry(BucketSkills.MakeObsidian::new, "pour a water bucket next to lava pool sources to harden them into obsidian"));
	}

	public static Skill create(String name) {
		Entry e = MENU.get(name);
		return e == null ? null : e.make().get();
	}
}
