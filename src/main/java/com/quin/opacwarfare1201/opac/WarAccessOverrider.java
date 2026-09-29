package com.quin.opacwarfare1201.opac;

import com.quin.opacwarfare1201.war.WarManager;
import com.quin.opacwarfare1201.war.WarRecord;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.Entity;
import xaero.pac.common.server.claims.protection.override.api.ChunkAccessOverride;
import xaero.pac.common.server.claims.protection.override.api.ChunkAccessOverrideType;
import xaero.pac.common.server.claims.protection.override.api.IChunkAccessOverriderAPI;
import xaero.pac.common.server.player.config.api.IPlayerConfigAPI;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.UUID;

public final class WarAccessOverrider implements IChunkAccessOverriderAPI {
    @Override
    public @Nonnull String getName() {
        return "OPaC Warfare 1.20.1 contested access";
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
        WarRecord war = WarManager.get(server).activeWarAt(dim, x, z);
        if (war == null) return currentOverride;
        if (WarManager.get(server).isParticipant(war, accessorId, true)) {
            return new ChunkAccessOverride(ChunkAccessOverrideType.ALLOW);
        }
        return new ChunkAccessOverride(ChunkAccessOverrideType.PROTECT);
    }
}
