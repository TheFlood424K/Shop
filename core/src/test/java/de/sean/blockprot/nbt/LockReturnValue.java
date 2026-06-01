package de.sean.blockprot.nbt;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Minimal test-scope stub to satisfy BlockProt API classes that reference this annotation.
 * <p>
 * The real BlockProt runtime provides this type; our tests only need it to exist so Mockito/ByteBuddy
 * can inspect the BlockProt handler class hierarchy without {@link ClassNotFoundException}.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD, ElementType.TYPE})
public @interface LockReturnValue {
}


