package com.playerlink.mixin;

import com.playerlink.api.IOwnedLink;
import com.playerlink.util.ControllerOwners;
import com.simibubi.create.content.redstone.link.LinkBehaviour;
import com.simibubi.create.content.redstone.link.RedstoneLinkBlockEntity;
import com.simibubi.create.content.redstone.link.controller.LinkedControllerBindPacket;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.lang.reflect.Field;
import java.util.UUID;

/**
 * When a player binds a Link Controller button to a Redstone Link block,
 * Create copies that link's two item frequencies onto the controller slot.
 *
 * This mixin fires AFTER Create's own handleItem() completes, and additionally
 * copies the Redstone Link's player-frequency owner UUID onto ControllerOwners
 * for the same button slot. This means the controller slot automatically gets
 * the full three-frequency binding (item 1, item 2, player) in one interaction.
 */
@Mixin(value = LinkedControllerBindPacket.class, remap = false)
public abstract class LinkedControllerBindPacketMixin {

    @Inject(method = "handleItem", at = @At("RETURN"), remap = false, require = 0)
    private void playerlink$copyOwnerOnBind(ServerPlayer player,
                                             ItemStack heldItem,
                                             CallbackInfo ci) {
        try {
            // Read the button index and linkLocation from the packet via reflection.
            // These are private final fields — we only read, never write.
            int button = playerlink$readInt(this, "button");
            BlockPos linkPos = playerlink$readBlockPos(this, "linkLocation");
            if (linkPos == null) return;

            // Get the Redstone Link block entity at that position
            LinkBehaviour linkBehaviour = BlockEntityBehaviour.get(
                    player.level(), linkPos, LinkBehaviour.TYPE);
            if (linkBehaviour == null) return;

            // Walk up to the BlockEntity to check if it has an owner
            net.minecraft.world.level.block.entity.BlockEntity be =
                    player.level().getBlockEntity(linkPos);
            if (!(be instanceof RedstoneLinkBlockEntity)) return;

            UUID owner = ((IOwnedLink) be).playerlink$getOwner();
            // Write the owner (or null to clear) onto the controller slot
            ControllerOwners.set(heldItem, button, owner);

        } catch (Throwable t) {
            // Never crash the server — this is additive behaviour
            com.playerlink.PlayerLinkMod.LOGGER.warn(
                    "[PlayerLink] LinkedControllerBindPacketMixin failed silently", t);
        }
    }

    private static int playerlink$readInt(Object obj, String fieldName)
            throws NoSuchFieldException, IllegalAccessException {
        Field f = findField(obj.getClass(), fieldName);
        f.setAccessible(true);
        return f.getInt(obj);
    }

    private static BlockPos playerlink$readBlockPos(Object obj, String fieldName) {
        try {
            Field f = findField(obj.getClass(), fieldName);
            f.setAccessible(true);
            return (BlockPos) f.get(obj);
        } catch (Throwable t) {
            return null;
        }
    }

    private static Field findField(Class<?> cls, String name) throws NoSuchFieldException {
        Class<?> c = cls;
        while (c != null && c != Object.class) {
            try { return c.getDeclaredField(name); }
            catch (NoSuchFieldException ignored) { c = c.getSuperclass(); }
        }
        throw new NoSuchFieldException(name + " not found in " + cls.getName());
    }
}
