package com.playerlink.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.playerlink.api.IOwnedLink;
import com.playerlink.util.SlotMath;
import com.simibubi.create.content.redstone.link.RedstoneLinkBlock;
import com.simibubi.create.content.redstone.link.RedstoneLinkBlockEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.state.BlockState;
import org.joml.Matrix4f;

import java.util.UUID;

public final class LinkFaceRenderer {

    public static volatile BlockPos HOVERED_FACE_SLOT_POS = null;

    private static long playerlink$lastDiagLog = 0L;

    private LinkFaceRenderer() {}

    public static void render(RedstoneLinkBlockEntity be,
                              PoseStack pose,
                              MultiBufferSource buffer,
                              int light,
                              int overlay) {
        long now = System.currentTimeMillis();
        if (now - playerlink$lastDiagLog > 5000L) {
            playerlink$lastDiagLog = now;
            com.playerlink.PlayerLinkMod.LOGGER.info(
                "[PlayerLink][face-render] called for {}  (owned={}, owner={})",
                be.getBlockPos(),
                be instanceof IOwnedLink,
                (be instanceof IOwnedLink io) ? io.playerlink$getOwner() : "n/a");
        }

        if (!(be instanceof IOwnedLink owned)) return;
        UUID owner = owned.playerlink$getOwner();
        if (owner == null) return;

        ResourceLocation skin = SkinCache.get(owner, null);
        if (skin == null) return;

        BlockState state = be.getBlockState();
        Direction facing = state.getValue(RedstoneLinkBlock.FACING);
        BlockPos pos = be.getBlockPos();

        pose.pushPose();
        applyFaceTransform(pose, facing);

        float scale = SlotMath.FACE_SIZE / 16f;
        pose.scale(scale, scale, scale);

        Matrix4f m = pose.last().pose();
        VertexConsumer vc = buffer.getBuffer(RenderType.entityCutoutNoCull(skin));

        addQuad(vc, m, 8f / 64f, 16f / 64f, 8f / 64f, 16f / 64f, light, overlay);

        pose.translate(0f, 0f, 0.01f);
        Matrix4f m2 = pose.last().pose();
        addQuad(vc, m2, 40f / 64f, 48f / 64f, 8f / 64f, 16f / 64f, light, overlay);

        pose.popPose();

        if (pos.equals(HOVERED_FACE_SLOT_POS)) {
            pose.pushPose();
            applyFaceTransform(pose, facing);
            float boxScale = (SlotMath.FACE_SIZE + 1.0f) / 16f;
            pose.scale(boxScale, boxScale, boxScale);
            drawHighlightSquare(pose, buffer);
            pose.popPose();
        }
    }

    private static void applyFaceTransform(PoseStack pose, Direction facing) {
        var center = SlotMath.localFaceCenter(facing);
        pose.translate(center.x, center.y, center.z);

        switch (facing) {
            case UP    -> pose.mulPose(new org.joml.Quaternionf().rotateX((float) -Math.PI / 2f));
            case DOWN  -> pose.mulPose(new org.joml.Quaternionf().rotateX((float)  Math.PI / 2f));
            case NORTH -> pose.mulPose(new org.joml.Quaternionf().rotateY((float)  Math.PI));
            case SOUTH -> { }
            case WEST  -> pose.mulPose(new org.joml.Quaternionf().rotateY((float) -Math.PI / 2f));
            case EAST  -> pose.mulPose(new org.joml.Quaternionf().rotateY((float)  Math.PI / 2f));
        }
        pose.translate(0f, 0f, 0.002f);
    }

    private static void addQuad(VertexConsumer vc, Matrix4f m,
                                float u0, float u1, float v0, float v1,
                                int light, int overlay) {
        addVertex(vc, m, -0.5F, -0.5F, u0, v1, light, overlay);
        addVertex(vc, m,  0.5F, -0.5F, u1, v1, light, overlay);
        addVertex(vc, m,  0.5F,  0.5F, u1, v0, light, overlay);
        addVertex(vc, m, -0.5F,  0.5F, u0, v0, light, overlay);
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

    private static void drawHighlightSquare(PoseStack pose, MultiBufferSource buffer) {
        Matrix4f m = pose.last().pose();
        VertexConsumer vc = buffer.getBuffer(RenderType.lines());
        int r = 255, g = 255, b = 255, a = 220;

        line(vc, m, -0.5f, -0.5f,  0.5f, -0.5f, r, g, b, a);
        line(vc, m,  0.5f, -0.5f,  0.5f,  0.5f, r, g, b, a);
        line(vc, m,  0.5f,  0.5f, -0.5f,  0.5f, r, g, b, a);
        line(vc, m, -0.5f,  0.5f, -0.5f, -0.5f, r, g, b, a);
    }

    private static void line(VertexConsumer vc, Matrix4f m,
                             float x1, float y1, float x2, float y2,
                             int r, int g, int b, int a) {
        vc.addVertex(m, x1, y1, 0f).setColor(r, g, b, a).setNormal(0f, 0f, 1f);
        vc.addVertex(m, x2, y2, 0f).setColor(r, g, b, a).setNormal(0f, 0f, 1f);
    }
}