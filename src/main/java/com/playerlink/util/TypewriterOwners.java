package com.playerlink.util;

import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

import javax.annotation.Nullable;
import java.util.UUID;

/**
 * Read/write a single player-owner UUID on a Linked Typewriter's BlockEntity NBT.
 *
 * The Typewriter is a placed block (not an item held in hand), so its owner
 * is stored in the block entity tag under the key "playerlink_owner".
 * This class provides helpers for the mixin to read/write that field in a
 * consistent, safe way without depending directly on our IOwnedLink interface
 * (which is on RedstoneLinkBlockEntity, not the Typewriter).
 */
public final class TypewriterOwners {

    public static final String NBT_KEY = "playerlink_owner";

    private TypewriterOwners() {}

    @Nullable
    public static UUID readFromTag(CompoundTag tag) {
        if (tag == null) return null;
        if (!tag.hasUUID(NBT_KEY)) return null;
        return tag.getUUID(NBT_KEY);
    }

    public static void writeToTag(CompoundTag tag, @Nullable UUID owner) {
        if (owner == null) {
            tag.remove(NBT_KEY);
        } else {
            tag.putUUID(NBT_KEY, owner);
        }
    }
}
