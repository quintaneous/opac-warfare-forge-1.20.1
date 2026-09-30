package com.quin.opacwarfare1201.war;

import com.quin.opacwarfare1201.config.WarConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import xaero.pac.common.claims.player.api.IPlayerChunkClaimAPI;
import xaero.pac.common.server.api.OpenPACServerAPI;

public final class WarPermissions {
    private WarPermissions() {}

    public static boolean canCbcDamageTerrain(Level level, BlockPos pos) {
        if (level.isClientSide()) return true;
        if (!WarConfig.CBC_PROTECT_CLAIMED_TERRAIN.get()) return true;
        if (!(level instanceof ServerLevel serverLevel)) return true;

        MinecraftServer server = serverLevel.getServer();
        WarManager manager = WarManager.get(server);

        if (manager.isProtectedCityBlock(level.dimension().location(), pos)) return false;

        ChunkPos cp = new ChunkPos(pos);
        if (manager.activeWarAt(level.dimension().location(), cp.x, cp.z) != null) return true;

        if (manager.cityAtChunk(level.dimension().location(), cp.x, cp.z) != null) return false;

        IPlayerChunkClaimAPI claim = OpenPACServerAPI.get(server).getServerClaimsManager()
                .get(level.dimension().location(), cp.x, cp.z);
        return claim == null;
    }
}
