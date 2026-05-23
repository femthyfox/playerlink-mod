
package com.playerlink.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.playerlink.api.IOwnedLink;
import com.simibubi.create.content.redstone.link.RedstoneLinkBlockEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix4f;
import org.joml.Quaternionf;

import java.util.UUID;

public final class LinkFaceRenderer {

    private LinkFaceRenderer() {}

    public static void render(RedstoneLinkBlockEntity be,
                              PoseStack pose,
                              MultiBufferSource buffer,
                              int light,
                              int overlay) {
        if (!(be instanceof IOwnedLink owned)) return;
        UUID owner = owned.playerlink$getOwner();
        if (owner == null) return;

        ResourceLocation skin = SkinCache.get(owner, null);

        pose.pushPose();
        pose.translate(0.5D, 1.35D, 0.5D);

        Quaternionf cam = Minecraft.getInstance().getEntityRenderDispatcher().cameraOrientation();
        pose.mulPose(cam);
        pose.scale(0.35F, 0.35F, 0.35F);

        Matrix4f m = pose.last().pose();
        VertexConsumer vc = buffer.getBuffer(RenderType.entityCutoutNoCull(skin));

        float u0 = 8f / 64f, u1 = 16f / 64f;
        float v0 = 8f / 64f, v1 = 16f / 64f;
        addVertex(vc, m, -0.5F, -0.5F, u0, v1, light, overlay);
        addVertex(vc, m,  0.5F, -0.5F, u1, v1, light, overlay);
        addVertex(vc, m,  0.5F,  0.5F, u1, v0, light, overlay);
        addVertex(vc, m, -0.5F,  0.5F, u0, v0, light, overlay);

        pose.translate(0F, 0F, 0.01F);
        Matrix4f m2 = pose.last().pose();
        float hu0 = 40f / 64f, hu1 = 48f / 64f;
        float hv0 = 8f  / 64f, hv1 = 16f / 64f;
        addVertex(vc, m2, -0.5F, -0.5F, hu0, hv1, light, overlay);
        addVertex(vc, m2,  0.5F, -0.5F, hu1, hv1, light, overlay);
        addVertex(vc, m2,  0.5F,  0.5F, hu1, hv0, light, overlay);
        addVertex(vc, m2, -0.5F,  0.5F, hu0, hv0, light, overlay);

        pose.popPose();
    }

    private static void addVertex(VertexConsumer vc, Matrix4f m,
                                  float x, float y, float u, float v,
                                  int light, int overlay) {
        vc.addVertex(m, x, y, 0F)
          .setColor(255, 255, 255, 255)
          .setUv(u, v)
          .setOverlay(overlay)
          .setLight(light)
          .setNormal(0F, 0F, 1F);
    }
}