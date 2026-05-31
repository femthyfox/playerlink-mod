package com.playerlink.util;

import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

import javax.annotation.Nullable;
import java.util.UUID;

/**
 * Single-owner ItemStack ownership. Stores one UUID under
 * {@value #KEY} inside the stack's vanilla {@code CUSTOM_DATA} component.
 *
 * <p>Use this for any item that needs to emit/receive a redstone-link
 * frequency on behalf of <b>one</b> specific player — e.g. a handheld
 * emitter, a worn trigger, or (future) a Create Aeronautics typewriter.
 *
 * <p>If your item needs PER-SLOT ownership (the Linked Controller's
 * 6 button columns), use {@link ControllerOwners} instead.
 *
 * <p>Always go through {@link com.playerlink.api.PlayerLinkApi} from
 * outside this package — the storage layout here may evolve.
 */
public final class StackOwners {

    /** NBT key used inside CUSTOM_DATA for the single owner UUID. */
    public static final String KEY = "playerlink_owner";

    private StackOwners() {}

    /** Returns the owner UUID on this stack, or {@code null} if none. */
    @Nullable
    public static UUID get(ItemStack stack) {
        if (stack.isEmpty()) return null;
        CustomData cd = stack.get(DataComponents.CUSTOM_DATA);
        if (cd == null || !cd.contains(KEY)) return null;            // fast no-copy reject
        CompoundTag root = cd.copyTag();
        return root.hasUUID(KEY) ? root.getUUID(KEY) : null;
    }

    /** Sets (or clears, when {@code owner == null}) the owner UUID on this stack. */
    public static void set(ItemStack stack, @Nullable UUID owner) {
        if (stack.isEmpty()) return;
        CustomData cd = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY);
        CompoundTag root = cd.copyTag();
        if (owner == null) root.remove(KEY);
        else                root.putUUID(KEY, owner);
        if (root.isEmpty()) {
            stack.remove(DataComponents.CUSTOM_DATA);
        } else {
            stack.set(DataComponents.CUSTOM_DATA, CustomData.of(root));
        }
    }
}
