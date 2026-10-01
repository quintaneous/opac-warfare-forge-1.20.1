package com.quin.opacwarfare1201.opac;

import com.quin.opacwarfare1201.war.WarManager;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import xaero.pac.common.claims.action.api.ClaimingAction;
import xaero.pac.common.server.claims.action.listener.api.IClaimActionListenerAPI;
import xaero.pac.common.server.claims.action.listener.override.api.ClaimActionPermissionOverride;
import xaero.pac.common.server.claims.action.listener.override.api.ClaimActionPermissionOverrideType;
import xaero.pac.common.server.claims.api.IServerClaimsManagerAPI;

import javax.annotation.Nonnull;
import java.util.UUID;

public final class TerritoryClaimActionListener implements IClaimActionListenerAPI {

    @Nonnull
    @Override
    public String getName() {
        return "OPaC Warfare territory rules";
    }

    @Nonnull
    @Override
    public ClaimActionPermissionOverride overrideClaimingActionPermission(
            @Nonnull UUID playerId,
            @Nonnull ResourceLocation dim,
            int x,
            int z,
            @Nonnull ClaimingAction action,
            @Nonnull IServerClaimsManagerAPI claimsManagerAPI,
            @Nonnull ClaimActionPermissionOverride currentOverride,
            @Nonnull MinecraftServer server) {

        String reason = WarManager.get(server)
                .validateNormalClaimAction(playerId, dim, x, z, action, claimsManagerAPI);

        if (reason == null) return currentOverride;
        return new ClaimActionPermissionOverride(
                ClaimActionPermissionOverrideType.FORBID,
                Component.literal(reason));
    }

    @Override
    public void handleSuccessfulClaimingAction(
            @Nonnull UUID playerId,
            @Nonnull ResourceLocation dim,
            int x,
            int z,
            @Nonnull ClaimingAction action,
            @Nonnull IServerClaimsManagerAPI claimsManagerAPI,
            @Nonnull MinecraftServer server) {
        WarManager.get(server).handleSuccessfulClaimAction(playerId, dim, x, z, action);
    }
}
