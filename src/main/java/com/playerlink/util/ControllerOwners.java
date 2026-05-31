package com.playerlink.util;

import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

import javax.annotation.Nullable;
import java.util.UUID;

/**
 * Read/write the per-slot player-owner UUIDs stored on a Linked
 * Controller ItemStack. There are {@value #SLOT_COUNT} slots (one per
 * button column). A null/missing UUID means "no owner -- transmits as
 * the public shared frequency".
 *
 * <p>Storage layout inside the vanilla {@code CUSTOM_DATA} component:
 * <pre>
 *   playerlink_owners (Compound)
 *     ├── "0" -> UUID intArray  (slot 0 owner, optional)
 *     ├── "1" -> UUID intArray
 *     └── ... up to "5"
 * </pre>
 *
 * <p>Always go through {@link com.playerlink.api.PlayerLinkApi} from
 * outside this package.
 */
public final class ControllerOwners {

    public static final int SLOT_COUNT = 6;

    /** Root key inside CUSTOM_DATA. */
    private static final String ROOT_KEY = "playerlink_owners";

    /**
     * Pre-allocated slot key strings. Saves an {@code Integer.toString}
     * allocation per read/write -- non-trivial when the GUI is open
     * (60 fps × 6 slots = 360 calls/s).
     */
    private static final String[] SLOT_KEYS = { "0", "1", "2", "3", "4", "5" };

    private ControllerOwners() {}

    // ─── READS ─────────────────────────────────────────────────────────

    /** Returns the owner UUID for the given slot, or {@code null}. */
    @Nullable
    public static UUID get(ItemStack stack, int slot) {
        if (stack.isEmpty() || slot < 0 || slot >= SLOT_COUNT) return null;
        CustomData cd = stack.get(DataComponents.CUSTOM_DATA);
        if (cd == null || !cd.contains(ROOT_KEY)) return null;       // no-copy fast path
        CompoundTag root = cd.copyTag();
        if (!root.contains(ROOT_KEY, 10)) return null;               // 10 = Compound
        CompoundTag owners = root.getCompound(ROOT_KEY);
        return owners.hasUUID(SLOT_KEYS[slot]) ? owners.getUUID(SLOT_KEYS[slot]) : null;
    }

    /**
     * Bulk read all {@value #SLOT_COUNT} slot owners with a single
     * {@code copyTag()} call. Always returns a fresh array of length
     * {@value #SLOT_COUNT} (entries may be null). Use this on render
     * paths -- calling {@link #get} six times per frame allocates six
     * deep tag copies.
     */
    public static UUID[] getAll(ItemStack stack) {
        UUID[] out = new UUID[SLOT_COUNT];
        if (stack.isEmpty()) return out;
        CustomData cd = stack.get(DataComponents.CUSTOM_DATA);
        if (cd == null || !cd.contains(ROOT_KEY)) return out;
        CompoundTag root = cd.copyTag();
        if (!root.contains(ROOT_KEY, 10)) return out;
        CompoundTag owners = root.getCompound(ROOT_KEY);
        for (int i = 0; i < SLOT_COUNT; i++) {
            if (owners.hasUUID(SLOT_KEYS[i])) out[i] = owners.getUUID(SLOT_KEYS[i]);
        }
        return out;
    }

    // ─── WRITES ────────────────────────────────────────────────────────

    /**
     * Sets (or clears, if {@code owner == null}) the owner UUID for the
     * given slot. Mutates the given ItemStack's CUSTOM_DATA component.
     */
    public static void set(ItemStack stack, int slot, @Nullable UUID owner) {
        if (stack.isEmpty() || slot < 0 || slot >= SLOT_COUNT) return;
        CustomData cd = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY);
        CompoundTag root = cd.copyTag();

        CompoundTag owners = root.contains(ROOT_KEY, 10)
                ? root.getCompound(ROOT_KEY)
                : new CompoundTag();

        if (owner == null) owners.remove(SLOT_KEYS[slot]);
        else                owners.putUUID(SLOT_KEYS[slot], owner);

        if (owners.isEmpty()) root.remove(ROOT_KEY);
        else                  root.put(ROOT_KEY, owners);

        if (root.isEmpty()) {
            stack.remove(DataComponents.CUSTOM_DATA);
        } else {
            stack.set(DataComponents.CUSTOM_DATA, CustomData.of(root));
        }
    }
}
