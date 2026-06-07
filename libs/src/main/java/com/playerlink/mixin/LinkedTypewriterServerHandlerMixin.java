package com.playerlink.mixin;

import com.playerlink.PlayerLinkMod;
import com.playerlink.api.IOwnedLink;
import com.playerlink.api.ITypewriterKeyOwner;
import com.playerlink.util.ControllerOwnerContext;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.UUID;

/**
 * Hooks the Typewriter server-side handler to:
 *  1. Set ControllerOwnerContext before key transmit so FrequencyOfMixin tags
 *     the frequency with the correct player owner.
 *  2. Copy a Redstone Link's owner UUID to the Typewriter key when a bind
 *     completes (right-click link → press key).
 *
 * All injections use require=0 so the mod loads safely if method signatures
 * differ between versions of Create Aeronautics / Simulated.
 */
@Pseudo
@Mixin(targets = "dev.simulated_team.simulated.content.blocks.redstone.linked_typewriter.LinkedTypewriterServerHandler",
       remap = false)
public abstract class LinkedTypewriterServerHandlerMixin {

    // ── Key-transmit: set owner context before Frequency.of() is called ──

    @Inject(method = "receivePressed", at = @At("HEAD"), remap = false, require = 0)
    private static void playerlink$transmitHead(LevelAccessor level, BlockPos typewriterPos, UUID playerUuid,
                                                int keyNum, boolean pressed, CallbackInfo ci) {
        ControllerOwnerContext.clear();
        if (!pressed) return;
        try {
            BlockEntity be = level.getBlockEntity(typewriterPos);
            if (!(be instanceof ITypewriterKeyOwner kOwner)) return;
            UUID owner = kOwner.playerlink$getKeyOwner(keyNum);
            if (owner != null) ControllerOwnerContext.set(owner);
        } catch (Throwable t) {
            PlayerLinkMod.LOGGER.warn("[PlayerLink] Typewriter receivePressed HEAD threw", t);
        }
    }

    @Inject(method = "receivePressed", at = @At("RETURN"), remap = false, require = 0)
    private static void playerlink$transmitReturn(LevelAccessor level, BlockPos typewriterPos, UUID playerUuid,
                                                  int keyNum, boolean pressed, CallbackInfo ci) {
        ControllerOwnerContext.clear();
    }

    // ── Bind completion: copy Redstone Link owner to the just-bound key ──

    @Inject(method = "receiveBound", at = @At("TAIL"), remap = false, require = 0)
    private static void playerlink$bindTail(LevelAccessor level, BlockPos typewriterPos, UUID playerUuid,
                                            BlockPos linkPos, int keyNum, CallbackInfo ci) {
        try {
            BlockEntity linkBe = level.getBlockEntity(linkPos);
            if (!(linkBe instanceof IOwnedLink owned)) return;
            UUID owner = owned.playerlink$getOwner();

            BlockEntity typewriterBe = level.getBlockEntity(typewriterPos);
            if (!(typewriterBe instanceof ITypewriterKeyOwner kOwner)) return;
            kOwner.playerlink$setKeyOwner(keyNum, owner);
            PlayerLinkMod.LOGGER.info("[PlayerLink] Typewriter bind: copied owner {} to key {}", owner, keyNum);
        } catch (Throwable t) {
            PlayerLinkMod.LOGGER.warn("[PlayerLink] Typewriter receiveBound TAIL threw", t);
        }
    }
}
