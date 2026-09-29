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
		MENU.put("gaze_avoid", new Entry(EndermanGaze::new, "look away from an enderman's eyes for a moment"));
		MENU.put("stair_up", new Entry(StairUp::new, "stair_up [steps] [toward x z]: dig a 1-wide staircase up to open sky"));
		MENU.put("spawner_escape", new Entry(SpawnerRoom::new, "wall off an active overworld spawner and leave"));
		MENU.put("make_bed", new Entry(MakeBed::new, "make_bed:n: hunt sheep for wool and craft n beds"));
		MENU.put("food_secure", new Entry(FoodSecure::new, "food_secure:n: cook, hunt, walk back to animals seen, fish, or explore until n food"));
		MENU.put("fish", new Entry(Fish::new, "fish:n: catch n fish with a rod at known water"));
		MENU.put("secure_camp", new Entry(SecureCamp::new, "torch the dark ground around us before long work in one spot"));
		MENU.put("stair_down", new Entry(StairDown::new, "stair_down <y>: dig safe 1-wide stairs down to y"));
		MENU.put("lava_scout", new Entry(PortalRoutes.LavaScout::new, "find a lava pool with 10+ seen sources (castable)"));
		MENU.put("cast_portal", new Entry(CastPortalSite::new, "dig out and floor a portal site by the lava, then cast and light the portal"));
		MENU.put("ruined_portal", new Entry(PortalRoutes.RuinedPortal::new, "loot a ruined portal's chest and mine its obsidian"));
		MENU.put("obsidian_pool", new Entry(PortalRoutes.ObsidianPool::new, "harden a lava pool and mine 10 obsidian safely (diamond pickaxe)"));
		MENU.put("diamond_hunt", new Entry(PortalRoutes.DiamondHunt::new, "diamond_hunt:n: safe stairs to y -54, then branch-mine for n diamonds"));
		MENU.put("portal_repair", new Entry(PortalRoutes.PortalRepair::new, "light a finished frame, or finish ours with carried obsidian"));
		MENU.put("stash", new Entry(ChestSkills.Stash::new, "put spare iron, food, buckets... in a chest at base"));
		MENU.put("restock", new Entry(ChestSkills.Restock::new, "restock[:items]: take spares back from our chest"));
		MENU.put("nether_arrival", new Entry(NetherRoutes.Arrival::new, "just arrived in the Nether: look around, floor the portal's exit"));
		MENU.put("nether_bridge", new Entry(NetherRoutes.Bridge::new, "nether_bridge [x z]: walk straight to a point (default: the fortress), bridging over lava"));
		MENU.put("fortress_scout", new Entry(NetherRoutes.FortressScout::new, "look for a fortress from an 8-block vantage, then long legs"));
		MENU.put("blaze_farm", new Entry(NetherRoutes.BlazeFarm::new, "blaze_farm:n: a nook at the spawner; hit blazes at its opening until n rods"));
		MENU.put("ghast_defense", new Entry(NetherRoutes.GhastDefense::new, "hit a ghast's fireball back, shoot it, or wall off its sight"));
		MENU.put("gold_armor", new Entry(NetherRoutes.GoldArmor::new, "mine nether gold, craft a gold helmet or boots, wear it"));
		MENU.put("barter_loop", new Entry(NetherRoutes.BarterLoop::new, "barter_loop:n: gold on, trade with piglins until n pearls"));
		MENU.put("portal_return", new Entry(NetherRoutes.PortalReturn::new, "back to the overworld through our portal (or build one)"));
		MENU.put("enderman_warped", new Entry(NetherRoutes.EndermanWarped::new, "enderman_warped:n: a 2-high hut, hit endermen's legs until n pearls"));
		MENU.put("bow_kit", new Entry(StrongholdRoutes.BowKit::new, "bow_kit:n: string, feathers and flint into a bow and n arrows"));
		MENU.put("eye_triangulate", new Entry(StrongholdRoutes.EyeTriangulate::new, "locate_stronghold runs until the bearings cross close by"));
		MENU.put("dig_to_stronghold", new Entry(StrongholdRoutes.DigToStronghold::new, "at the stronghold point: safe stairs down until its bricks show"));
		MENU.put("stronghold_navigate", new Entry(StrongholdRoutes.Navigate::new, "corridor search rounds until the portal room shows"));
		MENU.put("silverfish_control", new Entry(StrongholdRoutes.Silverfish::new, "break the portal room's silverfish spawner"));
		MENU.put("boat_cross", new Entry(StrongholdRoutes.BoatCross::new, "boat_cross [x z]: cross water by boat toward a point"));
		MENU.put("end_landing", new Entry(EndRoutes.EndLanding::new, "from the End's spawn platform onto the main island"));
		MENU.put("crystal_hunt", new Entry(EndRoutes.CrystalHunt::new, "shoot every end crystal, climbing to break caged ones' bars"));
		MENU.put("bed_bomb", new Entry(EndRoutes.BedBomb::new, "blow up beds at the perched dragon's head"));
		MENU.put("dragon_strike", new Entry(EndRoutes.DragonStrike::new, "arrows while the dragon circles, the head fight while it perches"));
		MENU.put("end_guard", new Entry(EndRoutes.EndGuard::new, "the End's reflex: away from enderman eyes, out of void falls"));
		MENU.put("fluid_seal", new Entry(Fluids.Seal::new, "fluid_seal [water|lava]: block the fluid sources near us (fireproof blocks)"));
		MENU.put("lava_guard", new Entry(Fluids.LavaGuard::new, "cover lava beside us or behind the block being dug"));
		MENU.put("fluid_cross", new Entry(Fluids.Cross::new, "fluid_cross <x z>: bridge, boat or swim across water or lava toward a point"));
		MENU.put("drain_tunnel", new Entry(Fluids.DrainTunnel::new, "seal and fill the water flooding our tunnel"));
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
