package com.playerlink.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.playerlink.client.LinkFaceRenderer;
import com.simibubi.create.content.redstone.link.RedstoneLinkBlockEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "com.simibubi.create.content.redstone.link.RedstoneLinkRenderer", remap = false)
public abstract class RedstoneLinkRendererMixin {

    @Inject(
        method  = "renderSafe",
        at      = @At("TAIL"),
        remap   = false,
        require = 0
    )
    private void playerlink$renderFace(RedstoneLinkBlockEntity be,
                                       float partialTicks,
                                       PoseStack pose,
                                       MultiBufferSource buffer,
                                       int light,
                                       int overlay,
                                       CallbackInfo ci) {
        LinkFaceRenderer.render(be, pose, buffer, light, overlay);
    }
}
