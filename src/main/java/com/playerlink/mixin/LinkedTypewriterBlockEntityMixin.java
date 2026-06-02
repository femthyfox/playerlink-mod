package com.playerlink.mixin;

/**
 * Intentionally empty placeholder.
 *
 * LinkedTypewriter support is implemented via reflection in
 * com.playerlink.server.TypewriterCompat so that the mod compiles
 * without the Simulated JAR present in the build environment.
 * The mixin is kept in the mixins list with require=0 so it is
 * skipped silently when Simulated is absent at runtime.
 */
public abstract class LinkedTypewriterBlockEntityMixin {
}
