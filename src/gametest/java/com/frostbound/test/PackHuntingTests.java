package com.frostbound.test;

import com.frostbound.alpha.AlphaWolfEntity;
import com.frostbound.alpha.AlphaWolves;
import com.frostbound.alpha.PackTactics;
import com.frostbound.alpha.PackWolfEntity;
import com.frostbound.alpha.WolfAction;
import com.frostbound.config.FrostboundConfig;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.passive.PigEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.test.GameTest;
import net.minecraft.test.GameTestException;
import net.minecraft.test.TestContext;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.GameMode;

import java.util.Arrays;
import java.util.List;

/**
 * The pack hunting as one: the ring it keeps round its prey, the Alpha sending in whoever is behind you,
 * surrounding new prey before the first bite, being stared down, and the Pincer. Arenas are built away
 * from the test (see WolfDenTests' helpers) and the prey is a pig standing still (mock players aren't in
 * the world). Every step is scheduled up front.
 */
public class PackHuntingTests implements FabricGameTest {
	private static final FrostboundConfig.AlphaWolfOptions CONFIG = FrostboundConfig.get().alphaWolf;
	private static final int START = 10;

	@GameTest(templateName = EMPTY_STRUCTURE)
	public void ringSpreadsThePackEvenlyAndTurns(TestContext ctx) {
		// Four wolves bunched up on one side, one across.
		double[] angles = {0.1, 0.2, 0.35, 3.0};
		double[] slots = PackTactics.ringSlots(angles, 0.0);
		Integer[] order = {0, 1, 2, 3};
		Arrays.sort(order, (a, b) -> Double.compare(slots[a], slots[b]));
		check(Arrays.equals(order, new Integer[] {0, 1, 2, 3}) || isRotationOf(order), "wolves keep their order round the ring: "
				+ Arrays.toString(slots));
		for (int i = 0; i < 4; i++) {
			double gap = PackTactics.separation(slots[i], slots[(i + 1) % 4]);
			check(Math.abs(gap - Math.PI / 2) < 1.0e-6, "four wolves sit a quarter turn apart, gap " + gap);
		}
		// The ring moves as little as it can: less in all than lining up on any one wolf, nobody more than a quarter turn.
		double moved = 0.0;
		for (int i = 0; i < 4; i++) {
			double d = PackTactics.separation(slots[i], angles[i]);
			check(d < Math.PI / 2 + 0.05, "wolf " + i + " sent too far round: " + angles[i] + " → " + slots[i]);
			moved += d;
		}
		for (int anchor = 0; anchor < 4; anchor++) {
			double other = 0.0;
			for (int k = 0; k < 4; k++) {
				other += PackTactics.separation(angles[anchor] + ((k - anchor + 4) % 4) * Math.PI / 2, angles[k]);
			}
			check(moved <= other + 1.0e-6, "lining up on wolf " + anchor + " would move the pack less: " + other + " < " + moved);
		}
		double[] turned = PackTactics.ringSlots(angles, 0.2);
		for (int i = 0; i < 4; i++) {
			check(Math.abs(PackTactics.separation(turned[i], slots[i]) - 0.2) < 1.0e-6, "the whole ring turns together");
		}
		check(PackTactics.ringSlots(new double[] {1.0}, 0.0)[0] == 1.0, "one wolf stays where it is");
		ctx.complete();
	}

	private static boolean isRotationOf(Integer[] order) {
		for (int k = 0; k < 4; k++) {
			boolean ok = true;
			for (int i = 0; i < 4; i++) {
				ok &= order[i] == (i + k) % 4;
			}
			if (ok) {
				return true;
			}
		}
		return false;
	}

	@GameTest(templateName = EMPTY_STRUCTURE)
	public void pincerPicksTheMostOppositePair(TestContext ctx) {
		AlphaWolfEntity alpha = ctx.spawnEntity(AlphaWolves.ALPHA_WOLF, 1, 2, 1);
		List<PackWolfEntity> wolves = alpha.spawnPack(3);
		Vec3d center = new Vec3d(100.0, 0.0, 100.0);
		place(wolves.get(0), center.add(4, 0, 0.5));
		place(wolves.get(1), center.add(3, 0, 3));
		place(wolves.get(2), center.add(-4, 0, -0.2));
		PackWolfEntity[] pair = PackTactics.pincerPair(wolves, center, 120.0);
		check(pair != null && List.of(pair).containsAll(List.of(wolves.get(0), wolves.get(2))), "the two across from each other are sent");
		check(PackTactics.pincerPair(wolves.subList(0, 2), center, 120.0) == null, "two wolves side by side make no Pincer");
		ctx.complete();
	}

	@GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 40)
	public void nextBiteGoesToTheWolfBehindYou(TestContext ctx) {
		AlphaWolfEntity alpha = ctx.spawnEntity(AlphaWolves.ALPHA_WOLF, 1, 2, 1);
		alpha.setAiDisabled(true);
		List<PackWolfEntity> wolves = alpha.spawnPack(3);
		PigEntity pig = ctx.spawnEntity(EntityType.PIG, 4, 2, 4);
		pig.setAiDisabled(true);
		Vec3d at = pig.getPos();
		face(pig, 0.0f); // looking along +z
		PackWolfEntity front = wolves.get(0), side = wolves.get(1), behind = wolves.get(2);
		place(front, at.add(0, 0, 3));
		place(side, at.add(3, 0, 0));
		place(behind, at.add(0.3, 0, -3));
		for (PackWolfEntity wolf : wolves) {
			wolf.setAiDisabled(true);
			wolf.setTarget(pig);
			wolf.setLungeCooldown(0);
		}
		ctx.runAtTick(2, () -> {
			check(PackTactics.behindness(pig, behind.getPos()) > 0.9 && PackTactics.behindness(pig, front.getPos()) < 0.1,
					"behindness: 1 behind, 0 in front");
			check(alpha.nextBiter(pig) == behind, "the bite goes to the wolf behind the prey, not " + name(alpha.nextBiter(pig), wolves));
			check(alpha.canClaimLunge(behind) && !alpha.canClaimLunge(front) && !alpha.canClaimLunge(side),
					"only the wolf it sends may take the turn");
			behind.setLungeCooldown(40);
			face(pig, 180.0f); // turns round
		});
		ctx.runAtTick(4, () -> {
			check(alpha.nextBiter(pig) == front, "turned round, the wolf now behind it is sent: " + name(alpha.nextBiter(pig), wolves));
			ctx.complete();
		});
	}

	@GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 220)
	public void newPreyIsSurroundedBeforeTheFirstBite(TestContext ctx) {
		BlockPos floor = WolfDenTests.arena(ctx, -1100, 14);
		int encircle = (int) Math.round(CONFIG.hunting.encircleSeconds * 20);
		Object[] s = new Object[3];
		ctx.runAtTick(START, () -> {
			AlphaWolfEntity alpha = WolfDenTests.spawnAlpha(ctx, floor.add(-7, 1, 0));
			alpha.setCallCooldown(100000);
			alpha.setMoveCooldown(100000);
			List<PackWolfEntity> pack = alpha.spawnPack(4);
			for (int i = 0; i < pack.size(); i++) {
				place(pack.get(i), Vec3d.ofBottomCenter(floor.add(-6 + i % 2, 1, -1 + i / 2 * 2)));
			}
			PigEntity pig = pig(ctx, floor.add(2, 1, 0));
			pig.setInvulnerable(true);
			alpha.setTarget(pig);
			alpha.setPincerCooldown(100000);
			for (PackWolfEntity wolf : alpha.getPack()) {
				check(wolf.lungeCooldown() >= encircle, "on new prey every wolf holds off ~" + encircle + " ticks, one waits " + wolf.lungeCooldown());
			}
			s[0] = alpha;
			s[1] = pack;
			s[2] = pig;
		});
		ctx.runAtEveryTick(() -> {
			// (Two ticks short of the wait: a wolf whose wait comes out shortest may start on the last one.)
			if (ctx.getTick() <= START || ctx.getTick() >= START + encircle - 2) {
				return;
			}
			@SuppressWarnings("unchecked") List<PackWolfEntity> pack = (List<PackWolfEntity>) s[1];
			for (PackWolfEntity wolf : pack) {
				check(!wolf.isLunging() && wolf.getAction() != WolfAction.CROUCH, "nobody bites while the pack is still surrounding its prey");
			}
		});
		// Then hold bites off, so what's checked is the ring itself rather than wolves mid-bite.
		ctx.runAtTick(START + encircle - 2, () -> ((AlphaWolfEntity) s[0]).getPack().forEach(wolf -> wolf.setLungeCooldown(100000)));
		ctx.runAtTick(START + 120, () -> {
			@SuppressWarnings("unchecked") List<PackWolfEntity> pack = (List<PackWolfEntity>) s[1];
			PigEntity pig = (PigEntity) s[2];
			double[] angles = pack.stream().mapToDouble(w -> PackTactics.angleAround(pig.getPos(), w.getPos())).sorted().toArray();
			double widest = 0.0;
			for (int i = 0; i < angles.length; i++) {
				double next = i + 1 < angles.length ? angles[i + 1] : angles[0] + Math.PI * 2;
				widest = Math.max(widest, next - angles[i]);
			}
			// Spawned bunched up on the Alpha's side; spread round, the biggest gap is far less than a half turn.
			check(widest < Math.toRadians(170), "the pack should have spread round its prey, biggest gap " + Math.round(Math.toDegrees(widest)) + "°");
			ctx.complete();
		});
	}

	/**
	 * A pack wolf with no Alpha runs straight in. The player looks at the one in front: it breaks off,
	 * as often as gazeHoldsOff allows, then calls the bluff. The one behind, unwatched, comes straight in.
	 */
	@GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 200)
	public void staringAWolfDownHoldsItOff(TestContext ctx) {
		BlockPos floor = WolfDenTests.arena(ctx, -1200, 12);
		Object[] s = new Object[3];
		int[] crouched = {-1, -1};
		int[] checksWhenCrouched = {-1, -1};
		ctx.runAtTick(START, () -> {
			PlayerEntity player = ctx.createMockPlayer(GameMode.SURVIVAL);
			player.setInvulnerable(true);
			Vec3d at = Vec3d.ofBottomCenter(floor.add(0, 1, 0));
			player.refreshPositionAndAngles(at.x, at.y, at.z, 0.0f, 8.0f); // looking along +z
			player.setHeadYaw(0.0f);
			PackWolfEntity front = wolf(ctx, at.add(0, 0, 8));
			PackWolfEntity behind = wolf(ctx, at.add(0, 0, -8));
			front.setTarget(player);
			behind.setTarget(player);
			s[0] = player;
			s[1] = front;
			s[2] = behind;
		});
		ctx.runAtEveryTick(() -> {
			if (ctx.getTick() <= START) {
				return;
			}
			PlayerEntity player = (PlayerEntity) s[0];
			PackWolfEntity front = (PackWolfEntity) s[1];
			PackWolfEntity behind = (PackWolfEntity) s[2];
			// Keep looking at the front wolf, wherever it circles.
			Vec3d to = front.getPos().subtract(player.getEyePos());
			float yaw = (float) Math.toDegrees(Math.atan2(-to.x, to.z));
			float pitch = (float) -Math.toDegrees(Math.atan2(to.y + front.getHeight() * 0.5, Math.hypot(to.x, to.z)));
			player.refreshPositionAndAngles(player.getX(), player.getY(), player.getZ(), yaw, pitch);
			player.setHeadYaw(yaw);
			int tick = (int) ctx.getTick();
			if (crouched[1] < 0 && behind.getAction() == WolfAction.CROUCH) {
				crouched[1] = tick;
				checksWhenCrouched[1] = behind.gazeChecks();
			}
			if (crouched[0] < 0 && front.getAction() == WolfAction.CROUCH) {
				crouched[0] = tick;
				checksWhenCrouched[0] = front.gazeChecks();
			}
			if (crouched[0] >= 0 && crouched[1] >= 0) {
				check(checksWhenCrouched[0] == CONFIG.hunting.gazeHoldsOff,
						"the watched wolf should break off " + CONFIG.hunting.gazeHoldsOff + " times before biting, broke off " + checksWhenCrouched[0]);
				check(checksWhenCrouched[1] == 0, "the unwatched wolf should come straight in, but broke off " + checksWhenCrouched[1] + " times");
				ctx.complete();
			}
		});
		ctx.runAtTick(START + 185, () -> {
			throw new GameTestException("expected both wolves to crouch: behind at " + crouched[1] + ", front at " + crouched[0]
					+ " (front broke off " + ((PackWolfEntity) s[1]).gazeChecks() + " times)");
		});
	}

	@GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 200)
	public void pincerBitesFromBothSides(TestContext ctx) {
		this.pincer(ctx, -1300, false);
	}

	@GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 200)
	public void sidesteppingThePincerDodgesBothWolves(TestContext ctx) {
		this.pincer(ctx, -1400, true);
	}

	/** s: alpha, the two wolves, the pig. marks: the tick the order was seen, bites counted (health drops). */
	private void pincer(TestContext ctx, int dz, boolean dodge) {
		BlockPos floor = WolfDenTests.arena(ctx, dz, 12);
		Object[] s = new Object[5];
		int[] mark = {-1};
		int[] bites = {0};
		float[] health = {0.0f};
		ctx.runAtTick(START, () -> {
			AlphaWolfEntity alpha = WolfDenTests.spawnAlpha(ctx, floor.add(0, 1, -7));
			alpha.setCallCooldown(100000);
			alpha.setMoveCooldown(100000);
			List<PackWolfEntity> pack = alpha.spawnPack(2);
			PigEntity pig = pig(ctx, floor.add(0, 1, 0));
			place(pack.get(0), pig.getPos().add(-4.5, 0, 0));
			place(pack.get(1), pig.getPos().add(4.5, 0, 0.3));
			alpha.setTarget(pig);
			alpha.setPincerCooldown(0);
			// Only the Pincer's two bites count here.
			alpha.setLungeCooldown(100000);
			pack.forEach(wolf -> wolf.setLungeCooldown(100000));
			s[0] = alpha;
			s[1] = pack.get(0);
			s[2] = pack.get(1);
			s[3] = pig;
			health[0] = pig.getHealth();
		});
		ctx.runAtEveryTick(() -> {
			if (ctx.getTick() <= START) {
				return;
			}
			AlphaWolfEntity alpha = (AlphaWolfEntity) s[0];
			PigEntity pig = (PigEntity) s[3];
			int tick = (int) ctx.getTick();
			if (pig.getHealth() < health[0]) {
				bites[0]++;
			}
			health[0] = pig.getHealth();
			if (mark[0] < 0 && alpha.getAction() == WolfAction.COMMANDING) {
				mark[0] = tick;
				// The Alpha picks which of the two goes first.
				s[4] = alpha.pincer().wolves.clone();
				check(alpha.pincer() != null && alpha.isPerformingMove(), "the order is a big move: the rest of the pack holds back");
			}
			if (mark[0] < 0) {
				return;
			}
			int t = tick - mark[0];
			PackWolfEntity first = ((PackWolfEntity[]) s[4])[0];
			PackWolfEntity second = ((PackWolfEntity[]) s[4])[1];
			if (t == 5) {
				check(first.getAction() == WolfAction.CROUCH && second.getAction() == WolfAction.CROUCH,
						"both wolves crouch while the Alpha barks: " + first.getAction() + ", " + second.getAction());
				check(alpha.pincer().prey == pig, "they're sent at the Alpha's prey");
			}
			int secondLock = AlphaWolfEntity.Pincer.leapAt(1) - CONFIG.pincer.lockTicks;
			if (dodge && t == secondLock + 1) {
				// Both lines have stopped following: step out from between them.
				pig.refreshPositionAndAngles(pig.getX(), pig.getY(), pig.getZ() + 4.0, 0.0f, 0.0f);
			}
			if (t == AlphaWolfEntity.Pincer.leapAt(0) + 2) {
				check(alpha.getAction() == WolfAction.NONE, "once the first goes the Alpha stands and watches, " + alpha.getAction());
				check(dodge || first.getAction() == WolfAction.LEAPING || first.getAction() == WolfAction.RECOVERING,
						"the first wolf leaps at " + AlphaWolfEntity.Pincer.leapAt(0) + ": " + first.getAction());
				check(second.getAction() == WolfAction.CROUCH, "the second is still crouched: " + second.getAction());
			}
			if (t == AlphaWolfEntity.Pincer.leapAt(1) + CONFIG.pincer.recoverTicks + 15) {
				if (dodge) {
					check(bites[0] == 0, "stepping aside once both lines lock should dodge both bites, took " + bites[0]);
				} else {
					check(bites[0] == 2, "staying put between them takes a bite from each side, took " + bites[0]);
				}
				check(alpha.pincer() == null && alpha.pincerCooldown() > 0, "the Pincer is over and on cooldown");
				ctx.complete();
			}
		});
		ctx.runAtTick(START + 60, () -> {
			if (mark[0] < 0) {
				throw new GameTestException("the Alpha never gave the order (action " + ((AlphaWolfEntity) s[0]).getAction() + ")");
			}
		});
	}

	/**
	 * The pack actually fights: 20 s against a player walking a slow circle and always turning to face the
	 * nearest wolf (a mock player never ticks, so its hurt cooldown and Draining Bite are reset by hand).
	 * Bites have to keep landing, wolves have to be on the attack most of the time, and the Alpha mustn't
	 * hold the pack back for most of the fight.
	 */
	@GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 500)
	public void packKeepsUpThePressure(TestContext ctx) {
		BlockPos floor = WolfDenTests.arena(ctx, -1500, 16);
		int ticks = 400;
		Object[] s = new Object[3];
		int[] c = new int[3]; // bites landed, ticks the Alpha held the pack back, ticks a pack wolf was on the attack
		Vec3d center = Vec3d.ofBottomCenter(floor.add(0, 1, 0));
		ctx.runAtTick(START, () -> {
			AlphaWolfEntity alpha = WolfDenTests.spawnAlpha(ctx, floor.add(-7, 1, 0));
			alpha.setCallCooldown(100000);
			List<PackWolfEntity> pack = alpha.spawnPack(6);
			for (int i = 0; i < pack.size(); i++) {
				place(pack.get(i), center.add(-7 + (i % 3), 0, -1 + i / 3 * 2));
			}
			PlayerEntity player = ctx.createMockPlayer(GameMode.SURVIVAL);
			player.refreshPositionAndAngles(center.x + 4, center.y, center.z, 0.0f, 0.0f);
			alpha.setTarget(player);
			s[0] = alpha;
			s[1] = pack;
			s[2] = player;
		});
		ctx.runAtEveryTick(() -> {
			if (ctx.getTick() <= START || ctx.getTick() > START + ticks) {
				return;
			}
			AlphaWolfEntity alpha = (AlphaWolfEntity) s[0];
			@SuppressWarnings("unchecked") List<PackWolfEntity> pack = (List<PackWolfEntity>) s[1];
			PlayerEntity player = (PlayerEntity) s[2];
			if (player.getHealth() < player.getMaxHealth() || player.hasStatusEffect(AlphaWolves.DRAINED)) {
				c[0]++;
			}
			player.clearStatusEffects();
			player.setHealth(player.getMaxHealth());
			player.timeUntilRegen = 0;
			double a = (ctx.getTick() - START) * 0.04; // ~0.16 blocks a tick round a circle of 4
			Vec3d at = center.add(Math.cos(a) * 4, 0, Math.sin(a) * 4);
			PackWolfEntity near = pack.stream().min(java.util.Comparator.comparingDouble(w -> w.squaredDistanceTo(player))).orElseThrow();
			Vec3d to = near.getPos().subtract(player.getEyePos());
			float yaw = (float) Math.toDegrees(Math.atan2(-to.x, to.z));
			float pitch = (float) -Math.toDegrees(Math.atan2(to.y + 0.4, Math.hypot(to.x, to.z)));
			player.refreshPositionAndAngles(at.x, at.y, at.z, yaw, pitch);
			player.setHeadYaw(yaw);
			if (alpha.holdsPackBack()) {
				c[1]++;
			}
			if (pack.stream().anyMatch(PackWolfEntity::isLunging)) {
				c[2]++;
			}
			if (ctx.getTick() == START + ticks) {
				String counts = c[0] + " bites, held back " + c[1] + "/" + ticks + " ticks, attacking " + c[2] + "/" + ticks;
				com.frostbound.Frostbound.LOGGER.info("Pack pressure over {} ticks: {}", ticks, counts);
				check(c[0] >= 10, "the pack should keep biting (about one a second), landed " + counts);
				check(c[2] >= ticks / 2, "some wolf should be on the attack most of the time: " + counts);
				check(c[1] <= ticks * 0.4, "the Alpha shouldn't hold the pack back for most of the fight: " + counts);
				ctx.complete();
			}
		});
	}

	// --- Helpers ---

	private static PigEntity pig(TestContext ctx, BlockPos pos) {
		PigEntity pig = EntityType.PIG.create(ctx.getWorld());
		pig.refreshPositionAndAngles(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, 0.0f, 0.0f);
		pig.setAiDisabled(true);
		pig.setPersistent();
		ctx.getWorld().spawnEntity(pig);
		return pig;
	}

	private static PackWolfEntity wolf(TestContext ctx, Vec3d pos) {
		PackWolfEntity wolf = AlphaWolves.PACK_WOLF.create(ctx.getWorld());
		wolf.setPersistent();
		wolf.refreshPositionAndAngles(pos.x, pos.y, pos.z, 0.0f, 0.0f);
		ctx.getWorld().spawnEntity(wolf);
		return wolf;
	}

	private static void place(PackWolfEntity wolf, Vec3d pos) {
		wolf.refreshPositionAndAngles(pos.x, pos.y, pos.z, 0.0f, 0.0f);
	}

	private static void face(PigEntity pig, float yaw) {
		pig.setYaw(yaw);
		pig.setHeadYaw(yaw);
		pig.setBodyYaw(yaw);
	}

	private static String name(PackWolfEntity wolf, List<PackWolfEntity> wolves) {
		return wolf == null ? "nobody" : "wolf " + wolves.indexOf(wolf);
	}

	private static void check(boolean condition, String message) {
		if (!condition) {
			// Failures thrown from scheduled steps don't reach the log on their own.
			com.frostbound.Frostbound.LOGGER.error("PackHuntingTests: {}", message);
			throw new GameTestException(message);
		}
	}
}
