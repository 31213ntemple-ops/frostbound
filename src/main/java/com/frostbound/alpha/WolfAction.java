package com.frostbound.alpha;

/**
 * What a pack wolf (or the Alpha) is doing right now, synced to the client so every move has a pose
 * you can read before it lands (see client.alpha.PackWolfModel). Each attack runs tell → action → recovery:
 *
 * <pre>
 *   Lunge (any wolf)    CROUCH ──────────▶ LEAPING ──▶ (falls back)
 *   Pounce (Alpha)      POUNCE_WINDUP ───▶ LEAPING ──▶ RECOVERING
 *   Charge (Bloodied)   CHARGE_WINDUP ───▶ CHARGING ─▶ STUNNED (if it hits a wall) / RECOVERING
 *   Pincer (Alpha)      COMMANDING (two wolves: CROUCH ─▶ LEAPING ─▶ RECOVERING)
 *   Call of the Pack    HOWLING (the pack joins in: CHORUS)
 *   Bloodied            ROARING
 *   The den             SLEEPING ─▶ WAKING
 *   Circling its prey   STALKING (head low, creeping)
 * </pre>
 *
 * New actions go at the end: the id synced to the client is the position in this list.
 */
public enum WolfAction {
	NONE,
	SLEEPING,
	WAKING,
	CROUCH,
	POUNCE_WINDUP,
	LEAPING,
	RECOVERING,
	CHARGE_WINDUP,
	CHARGING,
	STUNNED,
	HOWLING,
	CHORUS,
	ROARING,
	STALKING,
	COMMANDING;

	private static final WolfAction[] VALUES = values();

	public static WolfAction byId(int id) {
		return id >= 0 && id < VALUES.length ? VALUES[id] : NONE;
	}

	/** Lying down or getting up: not a threat yet. */
	public boolean isResting() {
		return this == SLEEPING || this == WAKING;
	}

	/** Sitting back on its haunches for this action. */
	public boolean sits() {
		return this == HOWLING || this == CHORUS || this == STUNNED;
	}
}
