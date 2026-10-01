package com.quin.opacwarfare1201.compat;

import com.quin.opacwarfare1201.OpacWarfare1201;
import com.quin.opacwarfare1201.config.WarConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.fml.ModList;

/**
 * Runtime-only CBC compatibility helper. We deliberately avoid a compile-time
 * Create Big Cannons dependency so the warfare mod can still load without CBC.
 */
public final class CBCMunitionClassifier {
    private static boolean initialized;
    private static Class<?> munitionBlockClass;
    private static Class<?> bigCannonBlockClass;

    private CBCMunitionClassifier() {}

    public static boolean isBigCannonMunition(Block block) {
        if (!ModList.get().isLoaded("createbigcannons")) return false;
        init();
        return munitionBlockClass != null && munitionBlockClass.isInstance(block);
    }

    public static boolean isBigCannonStructure(Block block) {
        if (!ModList.get().isLoaded("createbigcannons")) return false;
        init();
        return bigCannonBlockClass != null && bigCannonBlockClass.isInstance(block);
    }

    /**
     * Manual loading places shells/propellant in a straight line with the open
     * cannon before ramming. Require that line to reach a real CBC BigCannonBlock
     * rather than allowing loose ammunition blocks anywhere in the battlefield.
     */
    public static boolean isManualLoadingPlacement(Level level, BlockPos pos, Block placedBlock) {
        if (!isBigCannonMunition(placedBlock)) return false;

        int maxDistance = WarConfig.CBC_MANUAL_LOAD_MAX_DISTANCE.get();
        for (Direction direction : Direction.values()) {
            for (int i = 1; i <= maxDistance; i++) {
                BlockPos check = pos.relative(direction, i);
                BlockState state = level.getBlockState(check);

                if (state.isAir() || !state.getFluidState().isEmpty()) continue;
                if (isBigCannonMunition(state.getBlock())) continue;
                if (isBigCannonStructure(state.getBlock())) return true;

                // A solid non-munition obstruction means this is not a usable
                // loading line in this direction.
                break;
            }
        }
        return false;
    }

    private static synchronized void init() {
        if (initialized) return;
        initialized = true;

        try {
            munitionBlockClass = Class.forName(
                    "rbasamoyai.createbigcannons.munitions.big_cannon.BigCannonMunitionBlock");
            bigCannonBlockClass = Class.forName(
                    "rbasamoyai.createbigcannons.cannons.big_cannons.BigCannonBlock");
            OpacWarfare1201.LOGGER.info(
                    "CBC siege reload exception enabled with cannon-line validation");
        } catch (ClassNotFoundException e) {
            munitionBlockClass = null;
            bigCannonBlockClass = null;
            OpacWarfare1201.LOGGER.warn(
                    "Could not resolve CBC cannon/munition interfaces; siege reload exception will remain blocked");
        }
    }
}
