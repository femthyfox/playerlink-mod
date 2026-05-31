package com.playerlink.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.playerlink.api.PlayerLinkApi;
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
import org.joml.Quaternionf;

import java.util.EnumMap;
import java.util.Map;
import java.util.UUID;

/**
 * Renders the owner's player face inside the GUI "face slot" on the block, and
 * draws a hover highlight outline when the player is looking at the slot
 * (matching Create's value-box hover look).
 *
 * <p>Hot path — this is invoked once per visible link per frame. All per-frame
 * allocations have been hoisted into {@code static final} fields below.
 */
public final class LinkFaceRenderer {

    /** Updated each client tick by {@link ClientEvents}. */
    public static volatile BlockPos HOVERED_FACE_SLOT_POS = null;

    private LinkFaceRenderer() {}

    /**
     * Pre-baked rotation quaternions, one per facing. Computed once at
     * class-load — avoids allocating a {@link Quaternionf} every frame
     * for every visible link.
     */
    private static final Map<Direction, Quaternionf> ROT = new EnumMap<>(Direction.class);
    static {
        ROT.put(Direction.UP,    new Quaternionf().rotateX((float) -Math.PI / 2f));
        ROT.put(Direction.DOWN,  new Quaternionf().rotateX((float)  Math.PI / 2f));
        ROT.put(Direction.NORTH, new Quaternionf().rotateY((float)  Math.PI));
        ROT.put(Direction.SOUTH, new Quaternionf());                     // identity
        ROT.put(Direction.WEST,  new Quaternionf().rotateY((float) -Math.PI / 2f));
        ROT.put(Direction.EAST,  new Quaternionf().rotateY((float)  Math.PI / 2f));
    }

    public static void render(RedstoneLinkBlockEntity be,
                              PoseStack pose,
                              MultiBufferSource buffer,
                              int light,
                              int overlay) {
        UUID owner = PlayerLinkApi.readBlockOwner(be);
        if (owner == null) return;

        ResourceLocation skin = SkinCache.get(owner, null);
        if (skin == null) return;
        BlockState state = be.getBlockState();
        Direction facing = state.getValue(RedstoneLinkBlock.FACING);
        BlockPos pos = be.getBlockPos();

        // Position the face flush with the face slot's location on the block
        pose.pushPose();
        playerlink$applyFaceTransform(pose, facing);

        // Render base head face + hat overlay
        float scale = SlotMath.FACE_SIZE / 16f; // ~5/16 block
        pose.scale(scale, scale, scale);

        Matrix4f m = pose.last().pose();
        VertexConsumer vc = buffer.getBuffer(RenderType.entityCutoutNoCull(skin));

        // Front of head: UV [8..16, 8..16] on 64x64 skin
        addQuad(vc, m, 8f / 64f, 16f / 64f, 8f / 64f, 16f / 64f, light, overlay);

        // Slight forward bump for hat layer to avoid z-fighting
        pose.translate(0f, 0f, 0.01f);
        Matrix4f m2 = pose.last().pose();
        addQuad(vc, m2, 40f / 64f, 48f / 64f, 8f / 64f, 16f / 64f, light, overlay);

        pose.popPose();

        // Hover highlight when the player is targeting this face slot
        if (pos.equals(HOVERED_FACE_SLOT_POS)) {
            pose.pushPose();
            playerlink$applyFaceTransform(pose, facing);
            float boxScale = (SlotMath.FACE_SIZE + 1.0f) / 16f; // slightly larger than the face
            pose.scale(boxScale, boxScale, boxScale);
            playerlink$drawHighlightSquare(pose, buffer, light);
            pose.popPose();
        }
    }

    /**
     * Translates + rotates the pose so its local +Z faces OUTWARD from the
     * block's "front" surface, centred on the face slot.
     */
    private static void playerlink$applyFaceTransform(PoseStack pose, Direction facing) {
        // Place at the face-slot center in block-local space, then orient.
        var center = SlotMath.localFaceCenter(facing);
        pose.translate(center.x, center.y, center.z);

        Quaternionf q = ROT.get(facing);
        if (q != null && facing != Direction.SOUTH) {
            // SOUTH is the identity rotation — no mulPose call needed.
            pose.mulPose(q);
        }
        // Push a hair forward to avoid z-fighting with the block surface
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

    /**
     * Draws a thin white outline square in the pose's local XY plane to match
     * the look of Create's hovered value boxes.
     */
    private static void playerlink$drawHighlightSquare(PoseStack pose,
                                                       MultiBufferSource buffer,
                                                       int light) {
        Matrix4f m = pose.last().pose();
        VertexConsumer vc = buffer.getBuffer(RenderType.lines());
        int r = 255, g = 255, b = 255, a = 220;

        // Four edges of a unit square in the local XY plane at z = 0
        playerlink$line(vc, m, -0.5f, -0.5f,  0.5f, -0.5f, r, g, b, a);
        playerlink$line(vc, m,  0.5f, -0.5f,  0.5f,  0.5f, r, g, b, a);
        playerlink$line(vc, m,  0.5f,  0.5f, -0.5f,  0.5f, r, g, b, a);
        playerlink$line(vc, m, -0.5f,  0.5f, -0.5f, -0.5f, r, g, b, a);
    }

    private static void playerlink$line(VertexConsumer vc, Matrix4f m,
                                        float x1, float y1, float x2, float y2,
                                        int r, int g, int b, int a) {
        float nx = 0f, ny = 0f, nz = 1f;
        vc.addVertex(m, x1, y1, 0f).setColor(r, g, b, a).setNormal(nx, ny, nz);
        vc.addVertex(m, x2, y2, 0f).setColor(r, g, b, a).setNormal(nx, ny, nz);
    }
}
