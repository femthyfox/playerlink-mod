package com.playerlink.util;

import com.simibubi.create.content.redstone.link.controller.LinkedControllerServerHandler;

public final class Probe3 {

    private Probe3() {}

    public static void run() {
        StringBuilder sb = new StringBuilder("[PlayerLink Probe3] LinkedControllerServerHandler static methods: ");
        for (java.lang.reflect.Method m : LinkedControllerServerHandler.class.getDeclaredMethods()) {
            if (java.lang.reflect.Modifier.isStatic(m.getModifiers())) {
                sb.append(m.getName()).append("(").append(m.getParameterCount()).append("), ");
            }
        }
        System.out.println(sb);
    }
}