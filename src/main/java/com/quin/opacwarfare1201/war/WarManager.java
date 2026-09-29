package com.quin.opacwarfare1201.war;

import com.quin.opacwarfare1201.OpacWarfare1201;
import com.quin.opacwarfare1201.config.WarConfig;
import com.quin.opacwarfare1201.data.WarSavedData;
import com.quin.opacwarfare1201.opac.OpacSides;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.server.ServerLifecycleHooks;
import xaero.pac.common.claims.api.SpecialClaimOwners;
import xaero.pac.common.claims.player.api.IPlayerChunkClaimAPI;
import xaero.pac.common.server.api.OpenPACServerAPI;
import xaero.pac.common.server.claims.api.IServerClaimsManagerAPI;

import javax.annotation.Nullable;
import java.util.*;

public final class WarManager {
    private static final Map<MinecraftServer, WarManager> INSTANCES = new WeakHashMap<>();

    private final MinecraftServer server;
    private final WarSavedData data;
    private int secondTicker;

    private WarManager(MinecraftServer server) {
        this.server = server;
        this.data = server.overworld().getDataStorage().computeIfAbsent(WarSavedData::load, WarSavedData::new, WarSavedData.NAME);
    }

    public static synchronized WarManager get(MinecraftServer server) {
        return INSTANCES.computeIfAbsent(server, WarManager::new);
    }

    public static synchronized void clear(MinecraftServer server) {
        INSTANCES.remove(server);
    }

    public Collection<WarRecord> wars() { return Collections.unmodifiableCollection(data.wars()); }

    public void onServerStarted() {
        OpacWarfare1201.LOGGER.info("Loaded {} persisted chunk war(s)", data.wars().size());
    }

    @Nullable
    public WarRecord activeWarAt(ResourceLocation dim, int x, int z) {
        for (WarRecord w : data.wars()) {
            if (w.phase == WarPhase.ACTIVE && w.targets(dim, x, z)) return w;
        }
        return null;
    }

    @Nullable
    public WarRecord anyWarAt(ResourceLocation dim, int x, int z) {
        for (WarRecord w : data.wars()) if (w.targets(dim, x, z)) return w;
        return null;
    }

    public boolean isParticipant(WarRecord war, UUID playerId, boolean requireLives) {
        boolean member = OpacSides.isMember(server, playerId, war.attackerPartyId, war.attackerOwnerId)
                || OpacSides.isMember(server, playerId, war.defenderPartyId, war.defenderOwnerId);
        if (!member) return false;
        if (!requireLives || WarConfig.WAR_LIVES.get() <= 0) return true;
        return war.lives.getOrDefault(playerId, WarConfig.WAR_LIVES.get()) > 0;
    }

    public boolean isAttacker(WarRecord war, UUID playerId) {
        return OpacSides.isMember(server, playerId, war.attackerPartyId, war.attackerOwnerId);
    }

    public StartResult startWar(ServerPlayer attacker, ChunkPos target) {
        ResourceLocation dim = attacker.level().dimension().location();
        if (anyWarAt(dim, target.x, target.z) != null) return StartResult.fail("That chunk is already in a war.");

        OpenPACServerAPI api = OpenPACServerAPI.get(server);
        IServerClaimsManagerAPI claims = api.getServerClaimsManager();
        IPlayerChunkClaimAPI targetClaim = claims.get(dim, target.x, target.z);
        if (targetClaim == null) return StartResult.fail("The target chunk is wilderness.");
        if (SpecialClaimOwners.SERVER.equals(targetClaim.getPlayerId())) return StartResult.fail("Server-owned territory cannot be attacked.");

        OpacSides.Side attackerSide = OpacSides.playerSide(server, attacker.getUUID());
        if (attackerSide == null) return StartResult.fail("You must be in an Open Parties and Claims party to start a war.");
        OpacSides.Side defenderSide = OpacSides.claimSide(server, targetClaim);
        if (attackerSide.partyId().equals(defenderSide.partyId())) return StartResult.fail("You cannot attack your own party's claim.");

        if (WarConfig.ONLY_ONE_OFFENSIVE_WAR_PER_SIDE.get()) {
            for (WarRecord w : data.wars()) {
                if (w.attackerPartyId.equals(attackerSide.partyId())) return StartResult.fail("Your party already has an offensive chunk war.");
            }
        }
        if (WarConfig.REQUIRE_ONLINE_DEFENDER.get() && !OpacSides.isOnline(server, defenderSide.partyId(), defenderSide.ownerId())) {
            return StartResult.fail("At least one defender must be online.");
        }
        if (!isAccessibleBorder(claims, dim, target.x, target.z, attackerSide)) {
            return StartResult.fail("You can only attack a border chunk adjacent to wilderness or your own territory.");
        }

        WarRecord war = new WarRecord(UUID.randomUUID());
        war.dimension = dim;
        war.chunkX = target.x;
        war.chunkZ = target.z;
        war.attackerPartyId = attackerSide.partyId();
        war.attackerOwnerId = attackerSide.ownerId();
        war.defenderPartyId = defenderSide.partyId();
        war.defenderOwnerId = defenderSide.ownerId();
        war.originalClaimOwner = targetClaim.getPlayerId();
        war.originalSubConfig = targetClaim.getSubConfigIndex();
        war.originalForceload = targetClaim.isForceloadable();
        war.phase = WarPhase.PREPARING;
        ServerLevel level = server.getLevel(ResourceKey.create(Registries.DIMENSION, dim));
        long now = level == null ? server.overworld().getGameTime() : level.getGameTime();
        war.activateAtGameTime = now + WarConfig.PREPARATION_SECONDS.get() * 20L;
        war.progress = 0.5D;
        data.put(war);
        broadcast(Component.literal("WAR: " + attackerSide.name() + " is preparing an attack on " + defenderSide.name()
                + " at chunk [" + target.x + ", " + target.z + "]").withStyle(ChatFormatting.GOLD));
        return StartResult.ok(war);
    }

    private boolean isAccessibleBorder(IServerClaimsManagerAPI claims, ResourceLocation dim, int x, int z, OpacSides.Side attacker) {
        int[][] cardinal = {{1,0},{-1,0},{0,1},{0,-1}};
        for (int[] d : cardinal) if (neighborAccessible(claims, dim, x + d[0], z + d[1], attacker)) return true;
        if (WarConfig.ALLOW_DIAGONAL_BORDER.get()) {
            int[][] diag = {{1,1},{1,-1},{-1,1},{-1,-1}};
            for (int[] d : diag) if (neighborAccessible(claims, dim, x + d[0], z + d[1], attacker)) return true;
        }
        return false;
    }

    private boolean neighborAccessible(IServerClaimsManagerAPI claims, ResourceLocation dim, int x, int z, OpacSides.Side attacker) {
        IPlayerChunkClaimAPI c = claims.get(dim, x, z);
        if (c == null) return true;
        if (SpecialClaimOwners.SERVER.equals(c.getPlayerId())) return false;
        OpacSides.Side side = OpacSides.claimSide(server, c);
        return side.partyId() != null && side.partyId().equals(attacker.partyId());
    }

    public void adminStop(WarRecord war, boolean restoreDefender) {
        if (restoreDefender) restoreOriginalClaim(war);
        data.remove(war.id);
        broadcast(Component.literal("WAR: battle at [" + war.chunkX + ", " + war.chunkZ + "] was stopped.").withStyle(ChatFormatting.YELLOW));
    }

    public void surrender(ServerPlayer player, WarRecord war) {
        if (isAttacker(war, player.getUUID())) finish(war, false);
        else if (OpacSides.isMember(server, player.getUUID(), war.defenderPartyId, war.defenderOwnerId)) finish(war, true);
    }

    private void activate(WarRecord war) {
        IServerClaimsManagerAPI claims = OpenPACServerAPI.get(server).getServerClaimsManager();
        IPlayerChunkClaimAPI current = claims.get(war.dimension, war.chunkX, war.chunkZ);
        if (current == null || !current.getPlayerId().equals(war.originalClaimOwner)) {
            data.remove(war.id);
            broadcast(Component.literal("WAR: battle cancelled because target ownership changed before activation.").withStyle(ChatFormatting.RED));
            return;
        }
        claims.claim(war.dimension, SpecialClaimOwners.SERVER, 0, war.chunkX, war.chunkZ, false);
        war.phase = WarPhase.ACTIVE;
        war.progress = 0.5D;
        data.changed();
        broadcast(Component.literal("WAR ACTIVE: chunk [" + war.chunkX + ", " + war.chunkZ + "] is now contested.").withStyle(ChatFormatting.RED, ChatFormatting.BOLD));
    }

    private void finish(WarRecord war, boolean attackerWon) {
        IServerClaimsManagerAPI claims = OpenPACServerAPI.get(server).getServerClaimsManager();
        UUID newOwner = attackerWon ? war.attackerOwnerId : war.originalClaimOwner;
        int sub = attackerWon ? 0 : war.originalSubConfig;
        boolean forceload = attackerWon ? false : war.originalForceload;
        claims.claim(war.dimension, newOwner, sub, war.chunkX, war.chunkZ, forceload);
        data.remove(war.id);
        broadcast(Component.literal(attackerWon
                ? "WAR WON: attackers captured chunk [" + war.chunkX + ", " + war.chunkZ + "]."
                : "WAR DEFENDED: defenders held chunk [" + war.chunkX + ", " + war.chunkZ + "].")
                .withStyle(attackerWon ? ChatFormatting.GREEN : ChatFormatting.AQUA, ChatFormatting.BOLD));
    }

    private void restoreOriginalClaim(WarRecord war) {
        OpenPACServerAPI.get(server).getServerClaimsManager().claim(war.dimension, war.originalClaimOwner,
                war.originalSubConfig, war.chunkX, war.chunkZ, war.originalForceload);
    }

    private void tickSecond() {
        List<WarRecord> snapshot = new ArrayList<>(data.wars());
        for (WarRecord war : snapshot) {
            ServerLevel level = server.getLevel(ResourceKey.create(Registries.DIMENSION, war.dimension));
            if (level == null) continue;
            if (war.phase == WarPhase.PREPARING) {
                if (level.getGameTime() >= war.activateAtGameTime) activate(war);
                continue;
            }
            int attackers = 0;
            int defenders = 0;
            for (ServerPlayer p : server.getPlayerList().getPlayers()) {
                if (!p.level().dimension().location().equals(war.dimension)) continue;
                ChunkPos cp = p.chunkPosition();
                if (cp.x != war.chunkX || cp.z != war.chunkZ) continue;
                if (WarConfig.WAR_LIVES.get() > 0 && war.lives.getOrDefault(p.getUUID(), WarConfig.WAR_LIVES.get()) <= 0) continue;
                if (isAttacker(war, p.getUUID())) attackers++;
                else if (OpacSides.isMember(server, p.getUUID(), war.defenderPartyId, war.defenderOwnerId)) defenders++;
            }
            double perSecond = 0.5D / WarConfig.CAPTURE_SECONDS_FROM_MIDPOINT.get();
            if (attackers > defenders) war.progress += perSecond * (attackers - defenders);
            else if (defenders > attackers) war.progress -= perSecond * (defenders - attackers);
            else if (attackers == 0 && defenders == 0) {
                double decay = 0.5D / WarConfig.EMPTY_DECAY_SECONDS.get();
                if (war.progress > 0.5D) war.progress = Math.max(0.5D, war.progress - decay);
                else if (war.progress < 0.5D) war.progress = Math.min(0.5D, war.progress + decay);
            }
            war.progress = Math.max(0D, Math.min(1D, war.progress));
            data.changed();
            notifyPlayersInWar(war, attackers, defenders);
            if (war.progress >= 1D) finish(war, true);
            else if (war.progress <= 0D) finish(war, false);
        }
    }

    private void notifyPlayersInWar(WarRecord war, int attackers, int defenders) {
        int pct = (int)Math.round(war.progress * 100D);
        Component msg = Component.literal("Battle " + pct + "%  A:" + attackers + " D:" + defenders).withStyle(ChatFormatting.YELLOW);
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            if (isParticipant(war, p.getUUID(), false)) p.displayClientMessage(msg, true);
        }
    }

    private void broadcast(Component c) { server.getPlayerList().broadcastSystemMessage(c, false); }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        MinecraftServer currentServer = ServerLifecycleHooks.getCurrentServer();
        if (currentServer == null) return;
        WarManager m = get(currentServer);
        if (++m.secondTicker >= 20) {
            m.secondTicker = 0;
            m.tickSecond();
        }
    }

    @SubscribeEvent
    public static void onDeath(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        MinecraftServer server = player.getServer();
        if (server == null) return;
        WarManager m = get(server);
        ChunkPos cp = player.chunkPosition();
        WarRecord war = m.activeWarAt(player.level().dimension().location(), cp.x, cp.z);
        if (war == null || !m.isParticipant(war, player.getUUID(), false) || WarConfig.WAR_LIVES.get() <= 0) return;
        int left = Math.max(0, war.lives.getOrDefault(player.getUUID(), WarConfig.WAR_LIVES.get()) - 1);
        war.lives.put(player.getUUID(), left);
        m.data.changed();
        player.sendSystemMessage(Component.literal("War lives remaining: " + left).withStyle(left > 0 ? ChatFormatting.YELLOW : ChatFormatting.RED));
    }

    public record StartResult(boolean success, String message, WarRecord war) {
        public static StartResult fail(String message) { return new StartResult(false, message, null); }
        public static StartResult ok(WarRecord war) { return new StartResult(true, "War preparation started.", war); }
    }
}
