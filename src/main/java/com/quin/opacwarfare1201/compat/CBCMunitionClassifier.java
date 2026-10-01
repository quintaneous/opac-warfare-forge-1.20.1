package com.quin.opacwarfare1201.compat;

import com.quin.opacwarfare1201.OpacWarfare1201;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.fml.ModList;

/**
 * Runtime-only CBC compatibility helper. We deliberately avoid a compile-time
 * Create Big Cannons dependency so the warfare mod can still load without CBC.
 *
 * CBC projectiles and propellant both implement BigCannonMunitionBlock. In CBC,
 * BigCannonPropellantBlock extends the same interface, so this one check covers
 * shells/shot, powder charges, and big cartridges without whitelisting cannon
 * barrels, breeches, loaders, armor, or arbitrary CBC construction blocks.
 */
public final class CBCMunitionClassifier {
    private static boolean initialized;
    private static Class<?> munitionBlockClass;

    private CBCMunitionClassifier() {}

    public static boolean isBigCannonMunition(Block block) {
        if (!ModList.get().isLoaded("createbigcannons")) return false;
        init();
        return munitionBlockClass != null && munitionBlockClass.isInstance(block);
    }

    private static synchronized void init() {
        if (initialized) return;
        initialized = true;

        try {
            munitionBlockClass = Class.forName(
                    "rbasamoyai.createbigcannons.munitions.big_cannon.BigCannonMunitionBlock");
            OpacWarfare1201.LOGGER.info(
                    "CBC siege reload exception enabled for BigCannonMunitionBlock placements");
        } catch (ClassNotFoundException e) {
            munitionBlockClass = null;
            OpacWarfare1201.LOGGER.warn(
                    "Could not find CBC BigCannonMunitionBlock; active-siege manual reload placement will remain blocked");
        }
    }
}
