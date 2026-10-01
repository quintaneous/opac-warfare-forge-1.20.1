package com.quin.opacwarfare1201.war;

import com.quin.opacwarfare1201.config.WarConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import xaero.pac.common.claims.player.api.IPlayerChunkClaimAPI;
import xaero.pac.common.server.api.OpenPACServerAPI;

import javax.annotation.Nullable;
import java.util.UUID;

public final class WarPermissions {
    private static final String CBC_LAUNCH_PARTY_TAG = "opacWarfareLaunchParty";
    private static final ThreadLocal<UUID> CBC_SOURCE_PARTY = new ThreadLocal<>();

    private WarPermissions() {}

    public static void tagCbcProjectile(Entity projectile) {
        if (projectile.level().isClientSide()) return;

        CompoundTag persistent = projectile.getPersistentData();
        if (persistent.hasUUID(CBC_LAUNCH_PARTY_TAG)) return;

        if (!(projectile.level() instanceof ServerLevel level)) return;
        WarManager manager = WarManager.get(level.getServer());
        ChunkPos chunk = projectile.chunkPosition();
        UUID partyId = manager.battlefieldPartyAt(
                level.dimension().location(), chunk.x, chunk.z);
        if (partyId != null) persistent.putUUID(CBC_LAUNCH_PARTY_TAG, partyId);
    }

    public static void enterCbcProjectile(Entity projectile) {
        tagCbcProjectile(projectile);
        CompoundTag persistent = projectile.getPersistentData();
        CBC_SOURCE_PARTY.set(persistent.hasUUID(CBC_LAUNCH_PARTY_TAG)
                ? persistent.getUUID(CBC_LAUNCH_PARTY_TAG)
                : null);
    }

    public static void exitCbcProjectile() {
        CBC_SOURCE_PARTY.remove();
    }

    @Nullable
    public static UUID currentCbcSourceParty() {
        return CBC_SOURCE_PARTY.get();
    }

    public static boolean canCbcDamageTerrain(Level level, BlockPos pos) {
        if (level.isClientSide()) return true;
        if (!WarConfig.CBC_PROTECT_CLAIMED_TERRAIN.get()) return true;
        if (!(level instanceof ServerLevel serverLevel)) return true;

        MinecraftServer server = serverLevel.getServer();
        WarManager manager = WarManager.get(server);

        if (manager.isProtectedCityBlock(level.dimension().location(), pos)) return false;

        ChunkPos cp = new ChunkPos(pos);
        WarRecord war = manager.activeWarAt(level.dimension().location(), cp.x, cp.z);
        if (war != null) {
            UUID sourceParty = currentCbcSourceParty();
            if (sourceParty == null) return false;
            return sourceParty.equals(war.attackerPartyId)
                    || sourceParty.equals(war.defenderPartyId);
        }

        if (manager.cityAtChunk(level.dimension().location(), cp.x, cp.z) != null) return false;

        IPlayerChunkClaimAPI claim = OpenPACServerAPI.get(server).getServerClaimsManager()
                .get(level.dimension().location(), cp.x, cp.z);
        return claim == null;
    }

    public static boolean isCbcSourceAllowedForWar(WarRecord war) {
        UUID sourceParty = currentCbcSourceParty();
        return sourceParty != null
                && (sourceParty.equals(war.attackerPartyId)
                || sourceParty.equals(war.defenderPartyId));
    }

    public static ResourceLocation dimension(Level level) {
        return level.dimension().location();
    }
}
