package com.quin.opacwarfare1201.opac;

import com.quin.opacwarfare1201.war.StrategicCity;
import com.quin.opacwarfare1201.war.WarManager;
import com.quin.opacwarfare1201.war.WarRecord;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.Entity;
import xaero.pac.common.server.claims.protection.override.api.ChunkAccessOverride;
import xaero.pac.common.server.claims.protection.override.api.ChunkAccessOverrideType;
import xaero.pac.common.server.claims.protection.override.api.IChunkAccessOverriderAPI;
import xaero.pac.common.server.player.config.api.v2.IPlayerConfigAPI;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.UUID;

public final class WarAccessOverrider implements IChunkAccessOverriderAPI {
    @Override
    public @Nonnull String getName() {
        return "OPaC Warfare 1.20.1 war and strategic city access";
    }

    @Override
    public @Nonnull ChunkAccessOverride overrideChunkAccess(@Nullable ResourceLocation dim,
                                                             int x,
                                                             int z,
                                                             @Nonnull IPlayerConfigAPI claimConfig,
                                                             @Nullable Entity accessor,
                                                             @Nonnull UUID accessorId,
                                                             @Nonnull MinecraftServer server,
                                                             @Nonnull ChunkAccessOverride currentOverride) {
        if (dim == null) return currentOverride;

        WarManager manager = WarManager.get(server);
        WarRecord war = manager.activeWarAt(dim, x, z);
        if (war != null) {
            if (manager.isParticipant(war, accessorId, true)) {
                return new ChunkAccessOverride(ChunkAccessOverrideType.ALLOW);
            }
            return new ChunkAccessOverride(ChunkAccessOverrideType.PROTECT);
        }

        StrategicCity city = manager.cityAtChunk(dim, x, z);
        if (city != null) {
            if (manager.canPlayerAccessCityChunk(accessorId, dim, x, z)) {
                return new ChunkAccessOverride(ChunkAccessOverrideType.ALLOW);
            }
            return new ChunkAccessOverride(ChunkAccessOverrideType.PROTECT);
        }

        return currentOverride;
    }
}
