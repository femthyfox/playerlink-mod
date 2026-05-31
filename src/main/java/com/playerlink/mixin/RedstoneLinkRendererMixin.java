package com.playerlink.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.playerlink.client.LinkFaceRenderer;
import com.simibubi.create.content.redstone.link.RedstoneLinkBlockEntity;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "com.simibubi.create.content.redstone.link.LinkRenderer", remap = false)
public abstract class RedstoneLinkRendererMixin {

    @Inject(
        method  = "renderOnBlockEntity(Lcom/simibubi/create/foundation/blockEntity/SmartBlockEntity;FLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;II)V",
        at      = @At("TAIL"),
        remap   = false,
        require = 0
    )
    private static void playerlink$renderFace(SmartBlockEntity be,
                                              float partialTicks,
                                              PoseStack pose,
                                              MultiBufferSource buffer,
                                              int light,
                                              int overlay,
                                              CallbackInfo ci) {
        if (be instanceof RedstoneLinkBlockEntity linkBe) {
            LinkFaceRenderer.render(linkBe, pose, buffer, light, overlay);
        }
    }
}