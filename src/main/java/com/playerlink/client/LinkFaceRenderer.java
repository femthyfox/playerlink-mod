package com.playerlink.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.playerlink.api.IOwnedLink;
import com.simibubi.create.content.redstone.link.RedstoneLinkBlock;
import com.simibubi.create.content.redstone.link.RedstoneLinkBlockEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.state.BlockState;
import org.joml.Matrix4f;

import java.util.UUID;

/**
 * Renders the owner's player face flush against the surface of the Redstone Link block.
 *
 * ─── HOW TO TUNE THE FACE POSITION FOR YOUR CUSTOM MODEL ──────────────────────
 * Once your new BlockBench model is loaded into the game, adjust the constants
 * in the FACE TUNING block below. All values are in PIXELS (1/16th of a block).
 *
 *   FACE_SIZE_PX     → width/height of the rendered face in pixels (8 = half block)
 *   FACE_OFFSET_U_PX → horizontal slide along the block face (0 = centered)
 *   FACE_OFFSET_V_PX → vertical slide along the block face   (0 = centered)
 *   FACE_DEPTH_PX    → how far the face sits ABOVE the block's front surface.
 *                      (small positive value avoids z-fighting; raise it if your
 *                       model has a recess where the face should sit deeper.)
 *   FRONT_FACE_PX    → which slab of the block is the "front face" the player
 *                      face attaches to. For Create's vanilla model the front
 *                      panel sits at 3px from the back, so we use 3.
 *                      If your new model has the panel at a different depth,
 *                      change this value.
 * ──────────────────────────────────────────────────────────────────────────────
 */
public final class LinkFaceRenderer {

    // ─── FACE TUNING — edit these once your model is ready ───
    private static final float FACE_SIZE_PX     = 8.0f;
    private static final float FACE_OFFSET_U_PX = 0.0f;
    private static final float FACE_OFFSET_V_PX = 0.0f;
    private static final float FACE_DEPTH_PX    = 0.05f;
    private static final float FRONT_FACE_PX    = 3.0f;
    // ──────────────────────────────────────────────────────────

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
        BlockState state = be.getBlockState();
        Direction facing = state.getValue(RedstoneLinkBlock.FACING);

        // Convert pixel units to block units
        final float size   = FACE_SIZE_PX / 16f;
        final float offU   = FACE_OFFSET_U_PX / 16f;
        final float offV   = FACE_OFFSET_V_PX / 16f;
        final float depth  = FACE_DEPTH_PX / 16f;
        final float front  = FRONT_FACE_PX / 16f;

        pose.pushPose();
        pose.translate(0.5f, 0.5f, 0.5f); // Center on block

        // Rotate so the face lies flat against the block's "front" panel
        switch (facing) {
            case UP    -> pose.mulPose(new org.joml.Quaternionf().rotateX((float) -Math.PI / 2f));
            case DOWN  -> pose.mulPose(new org.joml.Quaternionf().rotateX((float)  Math.PI / 2f));
            case NORTH -> pose.mulPose(new org.joml.Quaternionf().rotateY((float)  Math.PI));
            case SOUTH -> { /* default orientation, face points +Z */ }
            case WEST  -> pose.mulPose(new org.joml.Quaternionf().rotateY((float) -Math.PI / 2f));
            case EAST  -> pose.mulPose(new org.joml.Quaternionf().rotateY((float)  Math.PI / 2f));
        }

        // Move outward to the front of the panel + slight depth offset to avoid z-fighting
        pose.translate(offU, offV, (0.5f - front) + depth);

        pose.scale(size, size, size);

        Matrix4f m = pose.last().pose();
        VertexConsumer vc = buffer.getBuffer(RenderType.entityCutoutNoCull(skin));

        // Base face (front of head) — UV [8..16, 8..16] on the 64x64 skin
        float u0 = 8f / 64f, u1 = 16f / 64f;
        float v0 = 8f / 64f, v1 = 16f / 64f;
        addVertex(vc, m, -0.5F, -0.5F, u0, v1, light, overlay);
        addVertex(vc, m,  0.5F, -0.5F, u1, v1, light, overlay);
        addVertex(vc, m,  0.5F,  0.5F, u1, v0, light, overlay);
        addVertex(vc, m, -0.5F,  0.5F, u0, v0, light, overlay);

        // Hat overlay — UV [40..48, 8..16]
        pose.translate(0f, 0f, 0.01f);
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
