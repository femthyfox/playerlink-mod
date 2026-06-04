package com.playerlink.mixin;

/**
 * The Typewriter mixin is implemented via TypewriterCompat (reflection-based)
 * so PlayerLink compiles without the Simulated JAR. See TypewriterCompat.java.
 *
 * At runtime, TypewriterCompatMixin handles:
 *  - injecting a per-key owner map field onto LinkedTypewriterBlockEntity
 *  - hooking write/read for NBT persistence
 *  - hooking activateKey to tag Frequency objects before network submission
 *
 * This file is kept as a placeholder; the real injection work is in
 * TypewriterCompatMixin which uses string-based class names so it compiles
 * without Simulated on the classpath.
 */
public abstract class LinkedTypewriterBlockEntityMixin {
}
