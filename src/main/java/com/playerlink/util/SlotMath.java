package com.playerlink.util;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Shared math for placing the redstone-link slots relative to the block's
 * top-face (top-down) layout.
 *
 * The user provides slot positions as TOP-DOWN coordinates where:
 *   • U (right) — world X on a floor block (0 = left edge, 16 = right edge)
 *   • V (up)    — world Z on a floor block (0 = south/front, 16 = north/back)
 *   • H         — slot height above the block top (3.5px above the 3px plate)
 *
 * For other facings the position is rotated around the block center so the slot
 * lives on the block's "working face" the same way it would on the floor.
 *
 * ─── EDITABLE SLOT LAYOUT (all in PIXELS, 0..16) ────────────────────────
 * If the user ever wants to move slots, change these numbers.
 *   FIRST_*  → blue frequency slot
 *   SECOND_* → red frequency slot
 *   FACE_*   → GUI-open slot (player face)
 * Coordinates are the CENTER of each slot.
 * ─────────────────────────────────────────────────────────────────────────
 */
public final class SlotMath {

    // ── Frequency slot 1 (blue) — bottom-left (10, 3.5), 4x4 → center (12, 5.5)
    public static final float FIRST_U = 12.0f;
    public static final float FIRST_V = 5.5f;

    // ── Frequency slot 2 (red) — bottom-left (10, 8.5), 4x4 → center (12, 10.5)
    public static final float SECOND_U = 12.0f;
    public static final float SECOND_V = 10.5f;

    // ── Face / GUI slot — bottom-left (3, 5.5), 5x5 → center (5.5, 8.0)
    public static final float FACE_U = 5.5f;
    public static final float FACE_V = 8.0f;
    public static final float FACE_SIZE = 5.0f;

    // ── How high above the block-top the slot floats (just above the 3px plate)
    public static final float SLOT_HEIGHT_PX = 3.5f;

    private SlotMath() {}

    /**
     * Convert TOP-DOWN (U, V) slot coordinates into a block-local Vec3
     * (0..1 each axis) based on the block's facing.
     */
    public static Vec3 localCenter(Direction facing, float uPx, float vPx) {
        double u = uPx / 16.0;
        double v = vPx / 16.0;
        double h = SLOT_HEIGHT_PX / 16.0;
        return switch (facing) {
            case UP    -> new Vec3(u, h, v);
            case DOWN  -> new Vec3(u, 1.0 - h, 1.0 - v);
            case SOUTH -> new Vec3(u, v, 1.0 - h);
            case NORTH -> new Vec3(1.0 - u, v, h);
            case EAST  -> new Vec3(1.0 - h, v, 1.0 - u);
            case WEST  -> new Vec3(h, v, u);
        };
    }

    /**
     * Build an AABB in WORLD coordinates around the face slot, used for hover
     * detection and click testing.
     */
    public static AABB faceSlotWorldAABB(BlockPos pos, Direction facing) {
        Vec3 center = localCenter(facing, FACE_U, FACE_V);
        double half = (FACE_SIZE / 2.0) / 16.0;
        double thick = 1.0 / 16.0; // depth of the slot box
        Vec3 origin = Vec3.atLowerCornerOf(pos);

        double minX, maxX, minY, maxY, minZ, maxZ;
        switch (facing) {
            case UP, DOWN -> {
                minX = center.x - half; maxX = center.x + half;
                minY = center.y - thick / 2; maxY = center.y + thick / 2;
                minZ = center.z - half; maxZ = center.z + half;
            }
            case NORTH, SOUTH -> {
                minX = center.x - half; maxX = center.x + half;
                minY = center.y - half; maxY = center.y + half;
                minZ = center.z - thick / 2; maxZ = center.z + thick / 2;
            }
            default -> { // EAST, WEST
                minX = center.x - thick / 2; maxX = center.x + thick / 2;
                minY = center.y - half; maxY = center.y + half;
                minZ = center.z - half; maxZ = center.z + half;
            }
        }
        return new AABB(
                origin.x + minX, origin.y + minY, origin.z + minZ,
                origin.x + maxX, origin.y + maxY, origin.z + maxZ);
    }

    /**
     * Is the given world-space hit location inside the face slot's bounds?
     * Adds a small slack so grazing hits still count.
     */
    public static boolean isFaceSlotHit(BlockPos pos, Direction facing, Vec3 hitLocation) {
        return faceSlotWorldAABB(pos, facing).inflate(0.01).contains(hitLocation);
    }
}
