package com.playerlink.util;

public final class ClassProbe {
    private ClassProbe() {}
    public static void probe() {
        // Force-load these. Any wrong path will fail the build with a clear error.
        Class<?> a = com.simibubi.create.content.redstone.link.controller.LinkedControllerItem.class;
        Class<?> b = com.simibubi.create.content.redstone.link.controller.LinkedControllerScreen.class;
        Class<?> c = com.simibubi.create.content.redstone.link.controller.LinkedControllerClientHandler.class;
        System.out.println("[PlayerLink probe] " + a + " " + b + " " + c);
    }
}