package com.frostbound.test;

import com.frostbound.alpha.AlphaWolfEntity;
import com.frostbound.alpha.AlphaWolves;
import com.frostbound.alpha.PackWolfEntity;
import com.frostbound.alpha.WolfAction;
import com.frostbound.alpha.den.WolfDenLayout;
import com.frostbound.alpha.den.WolfDenPiece;
import com.frostbound.config.FrostboundConfig;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.boss.BossBar;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.passive.PigEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.test.GameTest;
import net.minecraft.test.GameTestException;
import net.minecraft.test.TestContext;
import net.minecraft.util.math.BlockBox;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.GameMode;

import java.util.List;

/**
 * The Alpha's den (layout, the sleeping pack, waking, going back to bed) and the fight's choreography:
 * the Pounce's ring, the Charge into a wall, the Bloodied roar, the pack giving the Alpha room and
 * howling with it.
 *
 * Dens and arenas are built well away from the other tests, their chunks force-loaded so the wolves
 * tick there; force-loaded chunks only take entities a few ticks later, so each scenario starts at
 * {@link #START}. (Every step is scheduled up front: scheduling from inside a scheduled step breaks
 * the test runner.)
 */
public class WolfDenTests implements FabricGameTest {
	private static final FrostboundConfig.AlphaWolfOptions CONFIG = FrostboundConfig.get().alphaWolf;
	private static final int START = 10;

	/** What a scenario sets up at {@link #START}, for the steps after it. */
	private static final class Scene {
		AlphaWolfEntity alpha;
		List<PackWolfEntity> pack = List.of();
		PlayerEntity player;
		int markA = -1;
		int markB = -1;
		boolean flag;
		float health;
	}

	// --- The den ---

	@GameTest(templateName = EMPTY_STRUCTURE)
	public void denLayoutIsAFairArena(TestContext ctx) {
		int chests = 0;
		for (long seed = 0; seed < 24; seed++) {
			WolfDenLayout den = WolfDenLayout.generate(seed * 7919L);
			String where = " (seed " + seed + ")";
			check(den.beds.size() == WolfDenLayout.PILLARS, "a den should have a spot for each of 6 pack wolves" + where);
			check(den.alphaBed.equals(new BlockPos(0, 2, 0)), "the Alpha sleeps in the middle, on the dais" + where);
			check(isSolid(den.blocks.get(new BlockPos(0, 1, 0))), "the Alpha's spot should be on the raised dais" + where);
			check(!den.blocks.containsKey(new BlockPos(0, 3, 0)), "nothing should stand over the Alpha" + where);
			for (BlockPos bed : den.beds) {
				check(isSolid(den.blocks.get(bed.down())), "every wolf's spot needs floor under it: " + bed + where);
				BlockState on = den.blocks.get(bed);
				check(on == null || on.isOf(Blocks.MOSS_CARPET), "a wolf's spot is clear (moss at most): " + bed + " has " + on + where);
				check(!den.blocks.containsKey(bed.up()), "a wolf's spot has head room: " + bed + where);
			}
			long standing = den.pillars.stream().filter(p -> isSolid(den.blocks.get(p.up(3)))).count();
			check(standing >= 3, "at least 3 pillars should still stand tall (cover from the Charge), " + standing + where);
			check(den.blocks.values().stream().anyMatch(s -> s.isOf(Blocks.LANTERN)), "the den should be lit" + where);
			// The low wall has gaps to walk in through.
			int ring = 0;
			int walled = 0;
			for (BlockPos floor : den.floor) {
				double d = Math.sqrt(floor.getX() * floor.getX() + floor.getZ() * floor.getZ());
				if (d >= 9.2 && d <= 10.4) {
					ring++;
					if (isSolid(den.blocks.get(floor.up()))) {
						walled++;
					}
				}
			}
			check(ring > 0 && walled < ring * 0.8 && walled > ring * 0.15, "the wall should be broken, with ways in: " + walled + "/" + ring + where);
			if (den.chest != null) {
				chests++;
			}
		}
		check(chests >= 20, "nearly every den should have a chest, " + chests + "/24 did");
		ctx.complete();
	}

	@GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 300)
	public void denPackSleepsUntilAttacked(TestContext ctx) {
		Scene s = new Scene();
		BlockPos center = den(ctx, -400, 11L, s);
		ctx.runAtEveryTick(() -> {
			int t = this.denTick(ctx, s, center);
			if (t == 0) {
				for (PackWolfEntity wolf : s.pack) {
					check(wolf.isSleeping(), "the whole pack should be asleep");
					check(center.equals(wolf.getDen()), "every wolf should know its den");
					check(wolf.isPersistent(), "den wolves never despawn");
				}
				s.player = ctx.createMockPlayer(GameMode.SURVIVAL);
				s.player.setInvulnerable(true);
				s.player.setPosition(Vec3d.ofBottomCenter(center.add(3, 1, 0)));
			} else if (t == 40) {
				check(s.pack.stream().allMatch(PackWolfEntity::isSleeping), "walking through the den shouldn't wake anyone");
				check(s.alpha.getTarget() == null, "a sleeping Alpha has no target");
				PackWolfEntity wolf = s.pack.stream().filter(w -> w != s.alpha).findFirst().orElseThrow();
				wolf.damage(ctx.getWorld().getDamageSources().playerAttack(s.player), 1.0f);
			} else if (t == 43) {
				check(s.alpha.getAction() == WolfAction.WAKING, "hitting a pack wolf wakes the Alpha first, is " + s.alpha.getAction());
			} else if (t == 130) {
				for (PackWolfEntity wolf : s.pack) {
					check(!wolf.getAction().isResting(), "the whole pack should be up by now, one is " + wolf.getAction());
				}
				check(s.alpha.getTarget() == s.player, "the Alpha should go for whoever attacked");
				check(s.alpha.barColor() == BossBar.Color.WHITE, "the boss bar starts white");
				ctx.complete();
			}
		});
	}

	@GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 1500)
	public void denPackGoesBackToSleepAfterAFight(TestContext ctx) {
		Scene s = new Scene();
		BlockPos center = den(ctx, -500, 23L, s);
		ctx.runAtEveryTick(() -> {
			int t = this.denTick(ctx, s, center);
			if (t == 0) {
				s.player = ctx.createMockPlayer(GameMode.SURVIVAL);
				s.player.setInvulnerable(true);
				s.player.setPosition(Vec3d.ofBottomCenter(center.add(4, 1, 1)));
				s.alpha.damage(ctx.getWorld().getDamageSources().playerAttack(s.player), 1.0f);
			} else if (t == 80) {
				// The player gets away: the pack gives up, walks home and lies back down.
				check(!s.alpha.getAction().isResting(), "the Alpha should be awake");
				s.player.discard();
			} else if (t > 80 && !s.flag && s.alpha.isSleeping()
					&& s.pack.stream().filter(PackWolfEntity::isAlive).allMatch(PackWolfEntity::isSleeping)) {
				s.flag = true;
				check(s.alpha.getBed() != null && s.alpha.squaredDistanceTo(Vec3d.ofBottomCenter(s.alpha.getBed())) < 3.0 * 3.0,
						"the Alpha should sleep back on its dais");
				check(s.alpha.reinforcementsLeft() == CONFIG.callOfThePack.maxReinforcements, "a night's sleep restores its reinforcements");
				ctx.complete();
			}
		});
	}

	@GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 400)
	public void denPackChasesYouPastTheDen(TestContext ctx) {
		Scene s = new Scene();
		BlockPos center = den(ctx, -600, 29L, s, 48);
		ctx.runAtEveryTick(() -> {
			int t = this.denTick(ctx, s, center);
			if (t == 0) {
				s.player = ctx.createMockPlayer(GameMode.SURVIVAL);
				s.player.setInvulnerable(true);
				s.player.setPosition(Vec3d.ofBottomCenter(center.add(4, 1, 1)));
				s.alpha.damage(ctx.getWorld().getDamageSources().playerAttack(s.player), 1.0f);
			} else if (t == 130) {
				check(s.alpha.getTarget() == s.player, "the Alpha should be after the player who woke it");
				// Back off well past the den's wall: not far enough to have got away.
				s.player.setPosition(Vec3d.ofBottomCenter(center.add(34, 1, 0)));
				s.health = s.alpha.distanceTo(s.player);
			} else if (t > 130 && t <= 200) {
				for (PackWolfEntity wolf : s.pack) {
					check(!wolf.isAlive() || wolf.getTarget() == s.player,
							"a den pack shouldn't give up on someone just for leaving the den (tick " + t + ")");
				}
				check(!s.alpha.isGoingHome(), "the Alpha shouldn't head home mid-chase");
				if (t == 200) {
					float now = s.alpha.distanceTo(s.player);
					check(now < s.health - 4.0f, "the Alpha should come after the player: " + s.health + " → " + now + " blocks");
					ctx.complete();
				}
			}
		});
	}

	@GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 400)
	public void awakeDenPackFindsYouAgain(TestContext ctx) {
		Scene s = new Scene();
		BlockPos center = den(ctx, -700, 31L, s, 30);
		ServerWorld world = ctx.getWorld();
		ctx.runAtEveryTick(() -> {
			int t = this.denTick(ctx, s, center);
			if (t == 0) {
				// A real player in the world, so the pack can look for them.
				ServerPlayerEntity player = ImbuementTests.serverPlayer(ctx);
				player.changeGameMode(GameMode.SURVIVAL);
				player.addStatusEffect(new StatusEffectInstance(StatusEffects.RESISTANCE, 100000, 4));
				player.setPosition(Vec3d.ofBottomCenter(center.add(5, 1, 0)));
				world.onPlayerConnected(player);
				s.player = player;
				s.alpha.damage(world.getDamageSources().playerAttack(player), 1.0f);
			} else if (t == 130) {
				check(s.alpha.getTarget() == s.player, "the Alpha should be after the player who woke it");
				// Out of reach: the pack loses them.
				s.player.setPosition(Vec3d.ofBottomCenter(center.add(0, 1, 70)));
			} else if (t == 150) {
				check(s.pack.stream().allMatch(w -> w.getTarget() == null), "nobody should still be after a player 70 blocks away");
				check(!s.alpha.isGoingHome() && !s.alpha.isSleeping(), "it hasn't given up yet");
				// Back in sight before the pack gives up and heads home.
				s.player.setPosition(Vec3d.ofBottomCenter(center.add(6, 1, 0)));
			} else if (t == 200) {
				check(s.alpha.getTarget() == s.player, "a woken pack should find a player who comes back into sight");
				long after = s.pack.stream().filter(w -> w.isAlive() && w.getTarget() == s.player).count();
				check(after >= s.pack.size() - 1, "the pack should take the fight back up, " + after + " of " + s.pack.size() + " did");
				world.removePlayer((ServerPlayerEntity) s.player, Entity.RemovalReason.DISCARDED);
				ctx.complete();
			}
		});
	}

	/**
	 * Ticks since the whole den pack could be seen (the entity index catches up with a freshly built den
	 * a little later), or −1 before then. Fills in the scene's Alpha and pack at 0.
	 */
	private int denTick(TestContext ctx, Scene s, BlockPos center) {
		if (s.markA >= 0) {
			return (int) ctx.getTick() - s.markA;
		}
		if (s.alpha == null || ctx.getTick() < START) {
			return -1;
		}
		check(!s.alpha.isRemoved(), "the den's Alpha shouldn't be gone (" + s.alpha.getRemovalReason() + ")");
		if (ctx.getWorld().getEntity(s.alpha.getUuid()) == null || s.alpha.getPack().size() < CONFIG.initialPackSize) {
			check(ctx.getTick() < START + 120, "the den's pack never showed up (" + s.alpha.getPack().size() + " of "
					+ CONFIG.initialPackSize + " wolves)");
			return -1;
		}
		List<AlphaWolfEntity> found = ctx.getWorld().getEntitiesByClass(AlphaWolfEntity.class, new Box(center).expand(4.0), a -> true);
		check(found.size() == 1, "a den should hold exactly one Alpha, found " + found.size());
		s.pack = List.copyOf(s.alpha.getPack());
		s.markA = (int) ctx.getTick();
		return 0;
	}

	// --- The Alpha's moves ---

	@GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 220)
	public void pounceHitsWhoStaysInTheRing(TestContext ctx) {
		this.pounce(ctx, -600, false);
	}

	@GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 220)
	public void steppingOutOfThePounceRingDodgesIt(TestContext ctx) {
		this.pounce(ctx, -700, true);
	}

	/**
	 * The target is a pig standing still (mock players aren't really in the world, so an area attack
	 * can't find them). markA: the tick the windup was seen.
	 */
	private void pounce(TestContext ctx, int offset, boolean dodge) {
		BlockPos floor = arena(ctx, offset, 14);
		Scene s = new Scene();
		PigEntity[] target = new PigEntity[1];
		ctx.runAtTick(START, () -> {
			s.alpha = spawnAlpha(ctx, floor.add(-5, 1, 0));
			PigEntity pig = EntityType.PIG.create(ctx.getWorld());
			pig.refreshPositionAndAngles(floor.getX() + 5.5, floor.getY() + 1, floor.getZ() + 0.5, 0.0f, 0.0f);
			pig.setAiDisabled(true);
			pig.setPersistent();
			ctx.getWorld().spawnEntity(pig);
			target[0] = pig;
			s.alpha.setTarget(pig);
			s.alpha.setCallCooldown(100000);
			s.alpha.forceNextMove(false);
			s.health = pig.getHealth();
		});
		ctx.runAtEveryTick(() -> {
			if (ctx.getTick() <= START || s.flag) {
				return;
			}
			int tick = (int) ctx.getTick();
			PigEntity pig = target[0];
			WolfAction action = s.alpha.getAction();
			if (s.markA < 0 && action == WolfAction.POUNCE_WINDUP) {
				s.markA = tick;
			}
			if (s.markA >= 0 && dodge && tick == s.markA + CONFIG.pounce.windupTicks - 6) {
				// The ring has stopped following: step out of it.
				pig.refreshPositionAndAngles(pig.getX(), pig.getY(), pig.getZ() + CONFIG.pounce.radius + 3.0, 0.0f, 0.0f);
			}
			if (action == WolfAction.RECOVERING) {
				s.flag = true;
				float lost = s.health - pig.getHealth();
				if (dodge) {
					check(lost == 0.0f, "stepping out of the ring once it stops should dodge the slam, lost " + lost);
				} else {
					check(Math.abs(lost - CONFIG.pounce.damage) < 0.01, "staying in the ring takes the slam (" + CONFIG.pounce.damage + "), lost "
							+ lost + " (landed " + String.format("%.2f", Math.sqrt(s.alpha.squaredDistanceTo(pig))) + " away)");
				}
				ctx.complete();
				return;
			}
			s.health = pig.getHealth();
		});
		ctx.runAtTick(START + 160, () -> {
			if (s.markA < 0) {
				throw new GameTestException("the Alpha never wound up a Pounce (action " + s.alpha.getAction() + ")");
			}
		});
	}

	/** markA: the tick the Charge's windup was seen. flag: it roared first. */
	@GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 300)
	public void bloodiedAlphaRoarsThenChargesIntoAWall(TestContext ctx) {
		BlockPos floor = arena(ctx, -800, 14);
		ServerWorld world = ctx.getWorld();
		Scene s = new Scene();
		ctx.runAtTick(START, () -> {
			// A pillar-like wall behind the player.
			for (int z = -3; z <= 3; z++) {
				for (int y = 1; y <= 3; y++) {
					world.setBlockState(floor.add(8, y, z), Blocks.MOSSY_STONE_BRICKS.getDefaultState());
				}
			}
			s.alpha = spawnAlpha(ctx, floor.add(-4, 1, 0));
			s.player = ctx.createMockPlayer(GameMode.SURVIVAL);
			s.player.setInvulnerable(true);
			s.player.setPosition(Vec3d.ofBottomCenter(floor.add(5, 1, 0)));
			s.alpha.setTarget(s.player);
			s.alpha.setCallCooldown(100000);
			s.alpha.setMoveCooldown(100000);
			// Below half health: Bloodied.
			s.alpha.damage(world.getDamageSources().magic(), (float) (CONFIG.health * 0.55));
			check(s.alpha.isBloodied(), "below half health the Alpha should be Bloodied");
			check(s.alpha.barColor() == BossBar.Color.RED, "the boss bar turns red when it's Bloodied");
		});
		ctx.runAtEveryTick(() -> {
			if (ctx.getTick() <= START || s.markB >= 0) {
				return;
			}
			int tick = (int) ctx.getTick();
			WolfAction action = s.alpha.getAction();
			if (action == WolfAction.ROARING) {
				s.flag = true;
			}
			if (s.markA < 0 && action == WolfAction.CHARGE_WINDUP) {
				check(s.flag, "it should roar before its first Charge");
				s.markA = tick;
			}
			if (s.markA >= 0 && tick == s.markA + CONFIG.charge.windupTicks - CONFIG.charge.lockTicks + 2) {
				// Locked on: sidestep, and let it run into the wall.
				s.player.setPosition(s.player.getPos().add(0.0, 0.0, 4.0));
			}
			if (action == WolfAction.STUNNED) {
				s.markB = tick;
				float health = s.alpha.getHealth();
				s.alpha.damage(world.getDamageSources().magic(), 4.0f);
				float lost = health - s.alpha.getHealth();
				check(Math.abs(lost - 4.0f * CONFIG.charge.stunnedDamageMultiplier) < 0.01,
						"a stunned Alpha should take " + CONFIG.charge.stunnedDamageMultiplier + "× damage, took " + lost + " from 4");
				ctx.complete();
			}
		});
		ctx.runAtTick(START + 270, () -> {
			if (s.markB < 0) {
				throw new GameTestException("the Alpha should have charged into the wall and been stunned (roared " + s.flag
						+ ", wound up at " + s.markA + ", now " + s.alpha.getAction() + " at " + s.alpha.getBlockPos() + ")");
			}
		});
	}

	/** markA: the tick the Pounce was seen. */
	@GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 300)
	public void packGivesTheAlphaRoomAndHowlsWithIt(TestContext ctx) {
		BlockPos floor = arena(ctx, -900, 12);
		Scene s = new Scene();
		ctx.runAtTick(START, () -> {
			s.alpha = spawnAlpha(ctx, floor.add(-5, 1, 0));
			s.pack = s.alpha.spawnPack(4);
			s.player = ctx.createMockPlayer(GameMode.SURVIVAL);
			s.player.setInvulnerable(true);
			s.player.setPosition(Vec3d.ofBottomCenter(floor.add(4, 1, 0)));
			s.alpha.setTarget(s.player);
			s.alpha.setCallCooldown(100000);
			s.alpha.forceNextMove(false);
		});
		ctx.runAtEveryTick(() -> {
			if (ctx.getTick() <= START || s.flag) {
				return;
			}
			int tick = (int) ctx.getTick();
			if (s.markA < 0 && s.alpha.getAction() == WolfAction.POUNCE_WINDUP) {
				s.markA = tick;
			}
			// Once the Pounce is under way, no pack wolf is crouching to bite.
			if (s.markA >= 0 && tick > s.markA + 18 && s.alpha.isPerformingMove() && !s.alpha.isCalling()) {
				for (PackWolfEntity wolf : s.pack) {
					check(wolf.getAction() != WolfAction.CROUCH, "pack wolves should give the Alpha room during its Pounce");
				}
			}
			if (s.markA >= 0 && !s.alpha.isPerformingMove() && tick > s.markA + 30 && s.alpha.callCooldown() > 1000) {
				// Then the howl: the pack joins in.
				s.alpha.setCallCooldown(0);
			}
			if (s.alpha.isCalling() && s.alpha.actionTicks() > 25) {
				s.flag = true;
				long chorus = s.pack.stream().filter(w -> w.getAction() == WolfAction.CHORUS).count();
				check(chorus >= 2, "the pack should howl with its Alpha, " + chorus + " of 4 did");
				check(s.pack.stream().noneMatch(PackWolfEntity::isLunging), "nobody bites while the pack howls");
				ctx.complete();
			}
		});
	}

	// --- Helpers ---

	private static boolean isSolid(BlockState state) {
		return state != null && !state.isAir() && !state.isOf(Blocks.MOSS_CARPET) && state.isOpaque();
	}

	/** Force-loads (so mobs tick) and returns a flat stone floor of radius {@code r}, {@code dz} blocks from the test. */
	private static BlockPos arena(TestContext ctx, int dz, int r) {
		ServerWorld world = ctx.getWorld();
		BlockPos floor = ctx.getAbsolutePos(new BlockPos(0, 1, 0)).add(0, 0, dz);
		forceLoad(world, floor, r + 8);
		for (int x = -r; x <= r; x++) {
			for (int z = -r; z <= r; z++) {
				world.setBlockState(floor.add(x, 0, z), Blocks.STONE.getDefaultState());
				for (int y = 1; y <= 6; y++) {
					world.setBlockState(floor.add(x, y, z), Blocks.AIR.getDefaultState());
				}
			}
		}
		return floor;
	}

	/** A whole den with its sleeping pack, {@code dz} blocks from the test, built just before {@link #START}. Returns its centre. */
	private static BlockPos den(TestContext ctx, int dz, long seed, Scene scene) {
		return den(ctx, dz, seed, scene, WolfDenLayout.RADIUS + 2);
	}

	/** A den on a floor of radius {@code floor}, for chasing someone out of it. */
	private static BlockPos den(TestContext ctx, int dz, long seed, Scene scene, int floor) {
		BlockPos center = arena(ctx, dz, floor);
		ctx.runAtTick(START - 4, () -> scene.alpha = WolfDenPiece.place(ctx.getWorld(), center, WolfDenLayout.generate(seed),
				BlockBox.infinite(), true));
		return center;
	}

	private static void forceLoad(ServerWorld world, BlockPos center, int r) {
		ChunkPos min = new ChunkPos(center.add(-r, 0, -r));
		ChunkPos max = new ChunkPos(center.add(r, 0, r));
		for (int cx = min.x; cx <= max.x; cx++) {
			for (int cz = min.z; cz <= max.z; cz++) {
				world.setChunkForced(cx, cz, true);
			}
		}
	}

	/** A roaming Alpha on its own (persistent: no real player is ever near the test arenas). */
	private static AlphaWolfEntity spawnAlpha(TestContext ctx, BlockPos pos) {
		AlphaWolfEntity alpha = AlphaWolves.ALPHA_WOLF.create(ctx.getWorld());
		alpha.setPersistent();
		alpha.refreshPositionAndAngles(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, 0.0f, 0.0f);
		ctx.getWorld().spawnEntity(alpha);
		return alpha;
	}

	private static void check(boolean condition, String message) {
		if (!condition) {
			throw new GameTestException(message);
		}
	}
}
