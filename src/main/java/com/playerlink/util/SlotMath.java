package com.playerlink.util;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public final class SlotMath {

    // ── COORDINATE FLIP TOGGLES — try one or both if slots end up mirrored
    public static final boolean FLIP_U = false;  // mirror "right" axis
    public static final boolean FLIP_V = true;   // mirror "up"    axis  (user's "up" = north in BB top-down)

    // ── Frequency slots (CENTER positions in pixels 0..16)
    public static final float FIRST_U  = 10.0f;
    public static final float FIRST_V  = 3.5f;
    public static final float SECOND_U = 10.0f;
    public static final float SECOND_V = 8.5f;

    // ── Face / GUI slot (CENTER position)
    public static final float FACE_U    = 3.0f;
    public static final float FACE_V    = 5.5f;
    public static final float FACE_SIZE = 5.0f;

    public static final float SLOT_HEIGHT_PX = 3.5f;

    private SlotMath() {}

    public static Vec3 localCenter(Direction facing, float uPx, float vPx) {
        double u = (FLIP_U ? (16f - uPx) : uPx) / 16.0;
        double v = (FLIP_V ? (16f - vPx) : vPx) / 16.0;
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