package net.kyori.adventure.text.object;

/**
 * Compile-only stub.
 *
 * WorldGuard 7.0.18 was built against an Adventure snapshot that introduced
 * {@code net.kyori.adventure.text.object.PlayerHeadObjectContents} and its
 * nested {@code SkinSource} interface. Neither class exists in any published
 * Adventure release jar, so javac cannot resolve the transitive type closure
 * of {@code RegionQuery}'s method signatures at compile time — even when
 * those methods are invoked only via reflection.
 *
 * This stub satisfies the compiler. It is excluded from the final shaded jar
 * by {@code maven-jar-plugin} so it is never present at runtime.
 */
public interface PlayerHeadObjectContents {
    /** Compile-only stub for the nested SkinSource type. */
    interface SkinSource {}
}
