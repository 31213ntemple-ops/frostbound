package com.frostbound.imbuement;

/**
 * How far a piece of armour has been imbued with frost at the imbuement table. Each tier adds a star to
 * the item's name, in its own soft blue (paler for Cold, deeper for Frozen), so a Frozen piece shows
 * three stars going from pale to deep.
 */
public enum FrostTier {
	NONE(0x000000),
	COLD(0xCDE7F2),
	ICY(0xA3C9DE),
	FROZEN(0x7FA7C6);

	/** RGB of this tier's star: light, unsaturated blues. */
	public final int starColor;

	FrostTier(int starColor) {
		this.starColor = starColor;
	}

	public int level() {
		return ordinal();
	}

	public boolean atLeast(FrostTier other) {
		return ordinal() >= other.ordinal();
	}

	public static FrostTier of(int level) {
		FrostTier[] all = values();
		return all[Math.max(0, Math.min(all.length - 1, level))];
	}
}
