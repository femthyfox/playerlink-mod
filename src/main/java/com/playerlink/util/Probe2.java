package com.playerlink.util;

public final class Probe2 {
    private Probe2() {}
    public static void run() {
        // Each line force-loads a class. Build fails with a CLEAR error
        // if the class path is wrong, so we can fix it before writing mixins.
        Class<?> a = com.simibubi.create.content.redstone.link.controller.LinkedControllerServerHandler.class;
        Class<?> b = com.simibubi.create.content.redstone.link.controller.LinkedControllerClientHandler.class;
        Class<?> c = com.simibubi.create.content.redstone.link.controller.LinkedControllerBlockItem.class;
        System.out.println("[PlayerLink Probe2] OK: " + a + " " + b + " " + c);
    }
}