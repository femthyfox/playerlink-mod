package com.playerlink.util;

import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

import javax.annotation.Nullable;
import java.util.UUID;

/**
 * Read/write the per-slot player-owner UUIDs stored on a Linked Controller
 * ItemStack. There are 6 slots (one per button column). A null/missing UUID
 * means "no owner — transmits as the public shared frequency".
 */
public final class ControllerOwners {

    public static final int SLOT_COUNT = 6;

    private static final String ROOT_KEY = "playerlink_owners";

    private ControllerOwners() {}

    @Nullable
    public static UUID get(ItemStack stack, int slot) {
        if (stack.isEmpty() || slot < 0 || slot >= SLOT_COUNT) return null;
        CustomData cd = stack.get(DataComponents.CUSTOM_DATA);
        if (cd == null) return null;
        CompoundTag root = cd.copyTag();
        if (!root.contains(ROOT_KEY, 10)) return null;
        CompoundTag owners = root.getCompound(ROOT_KEY);
        String key = Integer.toString(slot);
        if (!owners.hasUUID(key)) return null;
        return owners.getUUID(key);
    }

    public static void set(ItemStack stack, int slot, @Nullable UUID owner) {
        if (stack.isEmpty() || slot < 0 || slot >= SLOT_COUNT) return;
        CustomData cd = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY);
        CompoundTag root = cd.copyTag();

        CompoundTag owners = root.contains(ROOT_KEY, 10)
                ? root.getCompound(ROOT_KEY)
                : new CompoundTag();

        String key = Integer.toString(slot);
        if (owner == null) {
            owners.remove(key);
        } else {
            owners.putUUID(key, owner);
        }

        if (owners.isEmpty()) {
            root.remove(ROOT_KEY);
        } else {
            root.put(ROOT_KEY, owners);
        }

        if (root.isEmpty()) {
            stack.remove(DataComponents.CUSTOM_DATA);
        } else {
            stack.set(DataComponents.CUSTOM_DATA, CustomData.of(root));
        }
    }
}
