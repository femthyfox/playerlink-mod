package com.playerlink.util;

public final class Probe2 {
    private Probe2() {}
    public static void run() {
        Class<?> a = com.simibubi.create.content.redstone.link.controller.LinkedControllerServerHandler.class;
        Class<?> b = com.simibubi.create.content.redstone.link.controller.LinkedControllerClientHandler.class;
        System.out.println("[PlayerLink Probe2] OK: " + a + " " + b);
    }
}