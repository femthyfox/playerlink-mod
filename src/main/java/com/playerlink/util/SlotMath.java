package com.playerlink.util;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Slot positioning math. Each facing has an explicit transform.
 *
 * Two separate "height" values let us position frequency slots and the face
 * slot at different distances from the block surface (e.g. face hovers
 * slightly higher than the freq slots on the floor).
 */
public final class SlotMath {

    // ── Frequency slots (CENTER positions in pixels)
    public static final float FIRST_U  = 11.0f;
    public static final float FIRST_V  = 5.5f;
    public static final float SECOND_U = 11.0f;
    public static final float SECOND_V = 10.5f;

    // ── Face / GUI slot (CENTER position)
    public static final float FACE_U    = 5.5f;
    public static final float FACE_V    = 8.0f;
    public static final float FACE_SIZE = 5.0f;

    // ── Surface offsets — independently tuned for floor/ceiling vs walls
    //    and for frequency vs face slots.
    public static final float FREQ_HEIGHT_VERT_PX  = 2.8f;   // freq slots on floor / ceiling
    public static final float FACE_HEIGHT_VERT_PX  = 3.8f;   // face on floor / ceiling
    public static final float FREQ_HEIGHT_HORIZ_PX = 7.5f;   // freq slots on walls
    public static final float FACE_HEIGHT_HORIZ_PX = 7.5f;   // face on walls

    private SlotMath() {}

    /** Position in block-local coords (0..1) for a frequency-slot pixel UV. */
    public static Vec3 localFreqCenter(Direction facing, float uPx, float vPx) {
        return localCenter(facing, uPx, vPx, FREQ_HEIGHT_VERT_PX, FREQ_HEIGHT_HORIZ_PX);
    }

    /** Position in block-local coords for the face-slot pixel UV. */
    public static Vec3 localFaceCenter(Direction facing) {
        return localCenter(facing, FACE_U, FACE_V, FACE_HEIGHT_VERT_PX, FACE_HEIGHT_HORIZ_PX);
    }

    private static Vec3 localCenter(Direction facing, float uPx, float vPx,
                                    float vertDepthPx, float horizDepthPx) {
        double u = uPx / 16.0;
        double v = vPx / 16.0;
        double hV = vertDepthPx  / 16.0;
        double hH = horizDepthPx / 16.0;
        return switch (facing) {
            case UP    -> new Vec3(1.0 - u, hV, v);
            case DOWN  -> new Vec3(1.0 - u, 1.0 - hV, v);
            case NORTH -> new Vec3(1.0 - u, 1.0 - v, hH);
            case SOUTH -> new Vec3(u, 1.0 - v, 1.0 - hH);
            case EAST  -> new Vec3(1.0 - hH, 1.0 - v, 1.0 - u);
            case WEST  -> new Vec3(hH, 1.0 - v, u);
        };
    }

    /**
     * Build an AABB for the face slot that extends THROUGH the block in the
     * facing-perpendicular axis. This ensures the clickable region covers the
     * visible block surface, regardless of how deep the slot's render position
     * sits behind/in-front-of the model.
     */
    public static AABB faceSlotWorldAABB(BlockPos pos, Direction facing) {
        Vec3 center = localFaceCenter(facing);
        double half = (FACE_SIZE / 2.0) / 16.0;
        Vec3 origin = Vec3.atLowerCornerOf(pos);

        double minX, maxX, minY, maxY, minZ, maxZ;
        switch (facing) {
            case UP, DOWN -> {
                minX = center.x - half; maxX = center.x + half;
                minY = 0;               maxY = 1.0;
                minZ = center.z - half; maxZ = center.z + half;
            }
            case NORTH, SOUTH -> {
                minX = center.x - half; maxX = center.x + half;
                minY = center.y - half; maxY = center.y + half;
                minZ = 0;               maxZ = 1.0;
            }
            default -> { // EAST / WEST
                minX = 0;               maxX = 1.0;
                minY = center.y - half; maxY = center.y + half;
                minZ = center.z - half; maxZ = center.z + half;
            }
        }
        return new AABB(
                origin.x + minX, origin.y + minY, origin.z + minZ,
                origin.x + maxX, origin.y + maxY, origin.z + maxZ);
    }

    public static boolean isFaceSlotHit(BlockPos pos, Direction facing, Vec3 hitLocation) {
        return faceSlotWorldAABB(pos, facing).inflate(0.01).contains(hitLocation);
    }

    /**
     * Distance from a block-local hit point to the frequency slot center,
     * IGNORING the facing-perpendicular axis. Used to override Create's
     * 3D distance check so clicks on the block's surface register even when
     * the slot's render position sits at a different depth.
     */
    public static double freqSlotPlanarDistance(Direction facing, boolean first, Vec3 localHit) {
        float u = first ? FIRST_U  : SECOND_U;
        float v = first ? FIRST_V  : SECOND_V;
        Vec3 c = localFreqCenter(facing, u, v);
        return switch (facing) {
            case UP, DOWN     -> Math.hypot(localHit.x - c.x, localHit.z - c.z);
            case NORTH, SOUTH -> Math.hypot(localHit.x - c.x, localHit.y - c.y);
            default           -> Math.hypot(localHit.z - c.z, localHit.y - c.y);
        };
    }
}
