package com.quin.opacwarfare1201.opac;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import xaero.pac.common.claims.player.api.IPlayerChunkClaimAPI;
import xaero.pac.common.server.api.OpenPACServerAPI;
import xaero.pac.common.server.parties.party.api.IPartyManagerAPI;
import xaero.pac.common.server.parties.party.api.IServerPartyAPI;

import javax.annotation.Nullable;
import java.util.UUID;

public final class OpacSides {
    public record Side(@Nullable UUID partyId, UUID ownerId, String name) {}

    private OpacSides() {}

    @Nullable
    public static Side playerSide(MinecraftServer server, UUID playerId) {
        IPartyManagerAPI pm = OpenPACServerAPI.get(server).getPartyManager();
        IServerPartyAPI party = pm.getPartyByMember(playerId);
        if (party == null) return null;
        return new Side(party.getId(), party.getOwner().getUUID(), party.getDefaultName());
    }

    public static Side claimSide(MinecraftServer server, IPlayerChunkClaimAPI claim) {
        UUID owner = claim.getPlayerId();
        IPartyManagerAPI pm = OpenPACServerAPI.get(server).getPartyManager();
        IServerPartyAPI party = pm.getPartyByOwner(owner);
        if (party == null) party = pm.getPartyByMember(owner);
        if (party != null) return new Side(party.getId(), party.getOwner().getUUID(), party.getDefaultName());
        return new Side(null, owner, owner.toString());
    }

    public static boolean sameSide(@Nullable Side a, @Nullable Side b) {
        if (a == null || b == null) return false;
        if (a.partyId() != null || b.partyId() != null) {
            return a.partyId() != null && a.partyId().equals(b.partyId());
        }
        return a.ownerId().equals(b.ownerId());
    }

    public static String sideName(MinecraftServer server, @Nullable UUID partyId, @Nullable UUID ownerId) {
        if (partyId != null) {
            IServerPartyAPI party = OpenPACServerAPI.get(server).getPartyManager().getPartyById(partyId);
            if (party != null) return party.getDefaultName();
        }
        if (ownerId == null) return "Neutral";
        ServerPlayer player = server.getPlayerList().getPlayer(ownerId);
        return player != null ? player.getGameProfile().getName() : ownerId.toString();
    }

    public static boolean isMember(MinecraftServer server, UUID playerId, @Nullable UUID partyId, @Nullable UUID soloOwnerId) {
        if (partyId != null) {
            IServerPartyAPI p = OpenPACServerAPI.get(server).getPartyManager().getPartyByMember(playerId);
            return p != null && p.getId().equals(partyId);
        }
        return soloOwnerId != null && playerId.equals(soloOwnerId);
    }

    public static boolean isOnline(MinecraftServer server, @Nullable UUID partyId, @Nullable UUID soloOwnerId) {
        if (partyId != null) {
            IServerPartyAPI p = OpenPACServerAPI.get(server).getPartyManager().getPartyById(partyId);
            return p != null && p.getOnlineMemberStream().findAny().isPresent();
        }
        if (soloOwnerId == null) return false;
        ServerPlayer player = server.getPlayerList().getPlayer(soloOwnerId);
        return player != null;
    }
}
