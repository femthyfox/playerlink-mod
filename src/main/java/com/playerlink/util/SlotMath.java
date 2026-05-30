package com.playerlink.util;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

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

    public static AABB faceSlotWorldAABB(BlockPos pos, Direction facing) {
        Vec3 center = localCenter(facing, FACE_U, FACE_V);
        double half = (FACE_SIZE / 2.0) / 16.0;
        double thick = 1.0 / 16.0;
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
            default -> {
                minX = center.x - thick / 2; maxX = center.x + thick / 2;
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
}