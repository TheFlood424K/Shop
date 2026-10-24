package com.snowgears.shop.util;

import org.bukkit.block.BlockFace;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class UtilMethodsBugFixTest {
    @Test
    public void testYawToFaceNegativeYaw() {
        BlockFace face = UtilMethods.yawToFace(-90);
        assertNotNull(face);
    }

    @Test
    public void testPushLocationInDirectionWest() {
        org.bukkit.Location loc = new org.bukkit.Location(null, 0, 0, 0);
        org.bukkit.Location result = UtilMethods.pushLocationInDirection(loc, BlockFace.WEST, 0.5);
        assertNotNull(result);
    }
}
