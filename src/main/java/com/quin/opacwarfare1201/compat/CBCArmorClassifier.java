package com.quin.opacwarfare1201.compat;

import com.quin.opacwarfare1201.OpacWarfare1201;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.fml.ModList;

import java.lang.reflect.Method;

/**
 * Reads Create Big Cannons' own block armor values at runtime.
 *
 * CBC 5.11.x exposes per-block hardness and toughness through
 * BlockArmorPropertiesHandler. We intentionally reuse those values instead
 * of maintaining a second hand-written block classification table.
 */
public final class CBCArmorClassifier {
    private static boolean initialized;
    private static boolean available;
    private static Method getProperties;
    private static Method hardness;
    private static Method toughness;

    private CBCArmorClassifier() {}

    public static ArmorValues armor(Level level, BlockState state, BlockPos pos) {
        if (!ModList.get().isLoaded("createbigcannons")) {
            return fallback(state);
        }

        init();
        if (!available) return fallback(state);

        try {
            Object provider = getProperties.invoke(null, state);
            double h = ((Number) hardness.invoke(provider, level, state, pos, true)).doubleValue();
            double t = ((Number) toughness.invoke(provider, level, state, pos, true)).doubleValue();
            return new ArmorValues(Math.max(0D, h), Math.max(0D, t));
        } catch (ReflectiveOperationException | RuntimeException e) {
            return fallback(state);
        }
    }

    /**
     * Converts CBC's continuous hardness/toughness values into our initial
     * infantry breaching tiers. CBC remains the source of truth for the block;
     * these thresholds only decide the minimum hand-mining time in a city siege.
     */
    public static float minimumSiegeBreakSeconds(Level level, BlockState state, BlockPos pos) {
        ArmorValues v = armor(level, state, pos);
        double score = v.hardness() + v.toughness() / 4D;

        if (score < 2D) return 2F;
        if (score < 3D) return 4F;
        if (score < 4.5D) return 6F;
        return 8F;
    }

    private static synchronized void init() {
        if (initialized) return;
        initialized = true;

        try {
            Class<?> handler = Class.forName(
                    "rbasamoyai.createbigcannons.block_armor_properties.BlockArmorPropertiesHandler");
            Class<?> provider = Class.forName(
                    "rbasamoyai.createbigcannons.block_armor_properties.BlockArmorPropertiesProvider");

            getProperties = handler.getMethod("getProperties", BlockState.class);
            hardness = provider.getMethod(
                    "hardness", Level.class, BlockState.class, BlockPos.class, boolean.class);
            toughness = provider.getMethod(
                    "toughness", Level.class, BlockState.class, BlockPos.class, boolean.class);

            available = true;
            OpacWarfare1201.LOGGER.info(
                    "Using Create Big Cannons block armor hardness/toughness for strategic-city siege mining");
        } catch (ReflectiveOperationException e) {
            available = false;
            OpacWarfare1201.LOGGER.warn(
                    "Could not access Create Big Cannons block armor API; strategic-city siege mining will use a vanilla fallback");
        }
    }

    private static ArmorValues fallback(BlockState state) {
        // CBC's own fallback is hardness=1 and toughness=blast resistance.
        return new ArmorValues(1D, Math.max(0D, state.getBlock().getExplosionResistance()));
    }

    public record ArmorValues(double hardness, double toughness) {}
}
