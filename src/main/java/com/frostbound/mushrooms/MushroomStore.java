package com.frostbound.mushrooms;

import com.frostbound.Frostbound;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ReferenceLinkedOpenHashSet;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.ChunkSectionPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.PersistentState;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Every placed giant mushroom in one world: saved data (origin and shape only) plus what the game needs to
 * look them up quickly.
 *
 *  - A chunk index: each mushroom is listed under every chunk its box touches, so {@link #find} (run on
 *    every cap/stem block change and break) only looks at the few mushrooms in that chunk.
 *  - {@link #ticking()}: the mushrooms someone is mining (sprouts growing). The world tick only visits these.
 *  - {@link #changed}: mushrooms with a block changed this tick, looked at again at the end of the tick.
 *
 * Only placing and forgetting a mushroom changes what's saved, so only those mark the data dirty.
 */
public final class MushroomStore extends PersistentState {
	private static final PersistentState.Type<MushroomStore> TYPE = new PersistentState.Type<>(MushroomStore::new, MushroomStore::read, null);

	private final List<Mushroom> mushrooms = new ArrayList<>();
	private final Long2ObjectOpenHashMap<ObjectArrayList<Mushroom>> byChunk = new Long2ObjectOpenHashMap<>();
	private final Set<Mushroom> ticking = new ReferenceLinkedOpenHashSet<>();
	private final Set<Mushroom> changed = new ReferenceLinkedOpenHashSet<>();
	/** Reused by {@link #drainChanged} so the end-of-tick pass allocates nothing. */
	private final List<Mushroom> scratch = new ArrayList<>();

	public static MushroomStore get(ServerWorld world) {
		return world.getPersistentStateManager().getOrCreate(TYPE, Frostbound.MOD_ID + "_giant_mushrooms");
	}

	// ---- Membership ----

	void add(Mushroom m) {
		insert(m);
		markDirty();
	}

	void remove(Mushroom m) {
		if (m.removed) {
			return;
		}
		m.removed = true;
		this.mushrooms.remove(m);
		forEachChunk(m, key -> {
			ObjectArrayList<Mushroom> list = this.byChunk.get(key);
			if (list != null) {
				list.remove(m);
				if (list.isEmpty()) {
					this.byChunk.remove(key);
				}
			}
		});
		this.ticking.remove(m);
		this.changed.remove(m);
		markDirty();
	}

	private void insert(Mushroom m) {
		this.mushrooms.add(m);
		forEachChunk(m, key -> this.byChunk.computeIfAbsent(key, k -> new ObjectArrayList<>()).add(m));
	}

	/** Calls {@code action} with the key of every chunk the mushroom's box touches. Only when it's added or removed. */
	private static void forEachChunk(Mushroom m, java.util.function.LongConsumer action) {
		int minX = ChunkSectionPos.getSectionCoord(m.box.getMinX()), maxX = ChunkSectionPos.getSectionCoord(m.box.getMaxX());
		int minZ = ChunkSectionPos.getSectionCoord(m.box.getMinZ()), maxZ = ChunkSectionPos.getSectionCoord(m.box.getMaxZ());
		for (int cx = minX; cx <= maxX; cx++) {
			for (int cz = minZ; cz <= maxZ; cz++) {
				action.accept(ChunkPos.toLong(cx, cz));
			}
		}
	}

	public boolean isEmpty() {
		return this.mushrooms.isEmpty();
	}

	public List<Mushroom> all() {
		return Collections.unmodifiableList(this.mushrooms);
	}

	// ---- Lookups ----

	/** The mushroom this position is part of (a cap or stem spot, whatever's there now), or null. */
	public Mushroom find(BlockPos pos) {
		ObjectArrayList<Mushroom> list = this.byChunk.get(ChunkPos.toLong(ChunkSectionPos.getSectionCoord(pos.getX()),
				ChunkSectionPos.getSectionCoord(pos.getZ())));
		if (list == null) {
			return null;
		}
		for (int i = 0, n = list.size(); i < n; i++) {
			Mushroom m = list.get(i);
			if (m.contains(pos)) {
				return m;
			}
		}
		return null;
	}

	/** Nearest mushroom whose origin is within {@code reach} of {@code pos}, or null. Looks only in the chunks in reach. */
	public Mushroom nearest(Vec3d pos, double reach) {
		int minX = ChunkSectionPos.getSectionCoord(MathHelper.floor(pos.x - reach)), maxX = ChunkSectionPos.getSectionCoord(MathHelper.floor(pos.x + reach));
		int minZ = ChunkSectionPos.getSectionCoord(MathHelper.floor(pos.z - reach)), maxZ = ChunkSectionPos.getSectionCoord(MathHelper.floor(pos.z + reach));
		Mushroom best = null;
		double bestDistance = reach * reach;
		for (int cx = minX; cx <= maxX; cx++) {
			for (int cz = minZ; cz <= maxZ; cz++) {
				ObjectArrayList<Mushroom> list = this.byChunk.get(ChunkPos.toLong(cx, cz));
				if (list == null) {
					continue;
				}
				for (int i = 0, n = list.size(); i < n; i++) {
					Mushroom m = list.get(i);
					double dx = m.origin.getX() + 0.5 - pos.x, dy = m.origin.getY() - pos.y, dz = m.origin.getZ() + 0.5 - pos.z;
					double distance = dx * dx + dy * dy + dz * dz;
					if (distance <= bestDistance && (best == null || distance < bestDistance)) {
						best = m;
						bestDistance = distance;
					}
				}
			}
		}
		return best;
	}

	// ---- Runtime sets ----

	/** Mushrooms being mined right now: the world tick grows their sprouts. */
	public Set<Mushroom> ticking() {
		return this.ticking;
	}

	void startTicking(Mushroom m) {
		if (!m.removed) {
			this.ticking.add(m);
		}
	}

	void markChanged(Mushroom m) {
		this.changed.add(m);
	}

	/** Hands each mushroom changed since the last call to {@code refresh} once; changes made meanwhile wait for the next call. */
	void drainChanged(Consumer<Mushroom> refresh) {
		if (this.changed.isEmpty()) {
			return;
		}
		this.scratch.addAll(this.changed);
		this.changed.clear();
		for (int i = 0, n = this.scratch.size(); i < n; i++) {
			Mushroom m = this.scratch.get(i);
			if (!m.removed) {
				refresh.accept(m);
			}
		}
		this.scratch.clear();
	}

	// ---- Saving ----

	@Override
	public NbtCompound writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
		NbtList list = new NbtList();
		for (Mushroom m : this.mushrooms) {
			NbtCompound entry = new NbtCompound();
			entry.putInt("X", m.origin.getX());
			entry.putInt("Y", m.origin.getY());
			entry.putInt("Z", m.origin.getZ());
			entry.putString("Shape", m.shape.id());
			list.add(entry);
		}
		nbt.put("Mushrooms", list);
		return nbt;
	}

	static MushroomStore read(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
		MushroomStore store = new MushroomStore();
		for (NbtElement element : nbt.getList("Mushrooms", NbtElement.COMPOUND_TYPE)) {
			NbtCompound entry = (NbtCompound) element;
			GiantMushroomShape shape = GiantMushroomShape.byId(entry.getString("Shape"));
			if (shape != null) {
				store.insert(new Mushroom(new BlockPos(entry.getInt("X"), entry.getInt("Y"), entry.getInt("Z")), shape));
			}
		}
		return store;
	}
}
