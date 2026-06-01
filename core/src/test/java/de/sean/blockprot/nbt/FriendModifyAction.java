package de.sean.blockprot.nbt;

/**
 * Minimal test-scope stub required for loading BlockProt handler classes in tests.
 * <p>
 * The real BlockProt runtime provides this type; tests only need it to exist for Mockito/ByteBuddy.
 */
public enum FriendModifyAction {
    ALLOW,
    DENY
}


