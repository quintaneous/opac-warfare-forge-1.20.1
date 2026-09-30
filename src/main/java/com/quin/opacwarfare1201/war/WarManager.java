package com.quin.opacwarfare1201.war;

import com.quin.opacwarfare1201.OpacWarfare1201;
import com.quin.opacwarfare1201.config.WarConfig;
import com.quin.opacwarfare1201.data.WarSavedData;
import com.quin.opacwarfare1201.opac.OpacSides;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.server.ServerLifecycleHooks;
import xaero.pac.common.claims.action.api.ClaimingAction;
import xaero.pac.common.claims.api.SpecialClaimOwners;
import xaero.pac.common.claims.player.api.IPlayerChunkClaimAPI;
import xaero.pac.common.server.api.OpenPACServerAPI;
import xaero.pac.common.server.claims.api.IServerClaimsManagerAPI;
import xaero.pac.common.server.parties.party.api.IServerPartyAPI;

import javax.annotation.Nullable;
import java.util.*;

public final class WarManager {
    private static final Map<MinecraftServer, WarManager> INSTANCES = new WeakHashMap<>();
    private static final int[][] CARDINAL = {{1,0},{-1,0},{0,1},{0,-1}};

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

    public Collection<WarRecord> wars() {
        return Collections.unmodifiableCollection(data.wars());
    }

    public Collection<CapitalRecord> capitals() {
        return Collections.unmodifiableCollection(data.capitals());
    }

    @Nullable
    public CapitalRecord capital(UUID partyId) {
        return data.getCapital(partyId);
    }

    public void onServerStarted() {
        boolean changed = false;
        for (WarRecord war : data.wars()) {
            ServerLevel level = level(war.dimension);
            if (level != null && !war.capturePointSet) {
                setCapturePoint(war, level);
                changed = true;
            }
        }
        if (changed) data.changed();
        OpacWarfare1201.LOGGER.info("Loaded {} persisted chunk war(s) and {} capital(s)",
                data.wars().size(), data.capitals().size());
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
        for (WarRecord w : data.wars()) {
            if (w.targets(dim, x, z)) return w;
        }
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

    public String attackerName(WarRecord war) {
        return OpacSides.sideName(server, war.attackerPartyId, war.attackerOwnerId);
    }

    public String defenderName(WarRecord war) {
        return OpacSides.sideName(server, war.defenderPartyId, war.defenderOwnerId);
    }

    public StartResult startWar(ServerPlayer attacker, ChunkPos target) {
        ResourceLocation dim = attacker.level().dimension().location();
        if (anyWarAt(dim, target.x, target.z) != null) {
            return StartResult.fail("That chunk is already in a war.");
        }

        IServerClaimsManagerAPI claims = OpenPACServerAPI.get(server).getServerClaimsManager();
        IPlayerChunkClaimAPI targetClaim = claims.get(dim, target.x, target.z);
        if (targetClaim == null) return StartResult.fail("The target chunk is wilderness.");
        if (SpecialClaimOwners.SERVER.equals(targetClaim.getPlayerId())) {
            return StartResult.fail("Server-owned territory cannot be attacked.");
        }

        OpacSides.Side attackerSide = OpacSides.playerSide(server, attacker.getUUID());
        if (attackerSide == null) {
            return StartResult.fail("You must be in an Open Parties and Claims party to start a war.");
        }

        OpacSides.Side defenderSide = OpacSides.claimSide(server, targetClaim);
        if (OpacSides.sameSide(attackerSide, defenderSide)) {
            return StartResult.fail("You cannot attack your own party's claim.");
        }

        CapitalRecord attackerCapital = data.getCapital(attackerSide.partyId());
        if (attackerCapital == null) {
            return StartResult.fail("Your nation must set a capital with /war capital set before starting a war.");
        }

        if (WarConfig.ONLY_ONE_OFFENSIVE_WAR_PER_SIDE.get()) {
            for (WarRecord w : data.wars()) {
                if (attackerSide.partyId().equals(w.attackerPartyId)) {
                    return StartResult.fail("Your party already has an offensive chunk war.");
                }
            }
        }

        if (WarConfig.REQUIRE_ONLINE_DEFENDER.get()
                && !OpacSides.isOnline(server, defenderSide.partyId(), defenderSide.ownerId())) {
            return StartResult.fail("At least one defender must be online.");
        }

        if (!isAccessibleBorder(claims, dim, target.x, target.z, attackerSide)) {
            return StartResult.fail("You can only attack an exposed enemy border chunk.");
        }

        int attackDistance = attackDistanceFromCapitalNetwork(claims, attackerSide, dim, target);
        if (attackDistance < 0) {
            return StartResult.fail("Your capital is not connected to valid territory in this dimension.");
        }
        if (attackDistance > WarConfig.MAX_ATTACK_DISTANCE_CHUNKS.get()) {
            return StartResult.fail("Target is " + attackDistance + " chunks from your capital-connected territory; Season 1 maximum is "
                    + WarConfig.MAX_ATTACK_DISTANCE_CHUNKS.get() + ".");
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

        ServerLevel level = level(dim);
        long now = level == null ? server.overworld().getGameTime() : level.getGameTime();
        war.activateAtGameTime = now + WarConfig.PREPARATION_SECONDS.get() * 20L;
        war.progress = 0.5D;
        if (level != null) setCapturePoint(war, level);

        data.put(war);
        broadcast(Component.literal("WAR: " + attackerSide.name() + " is preparing an attack on " + defenderSide.name()
                + " at chunk [" + target.x + ", " + target.z + "]"
                + (war.capturePointSet ? " | objective Y=" + war.captureY : ""))
                .withStyle(ChatFormatting.GOLD));
        return StartResult.ok(war);
    }

    private boolean isAccessibleBorder(IServerClaimsManagerAPI claims, ResourceLocation dim, int x, int z, OpacSides.Side attacker) {
        for (int[] d : CARDINAL) {
            if (neighborAccessible(claims, dim, x + d[0], z + d[1], attacker)) return true;
        }
        if (WarConfig.ALLOW_DIAGONAL_BORDER.get()) {
            int[][] diag = {{1,1},{1,-1},{-1,1},{-1,-1}};
            for (int[] d : diag) {
                if (neighborAccessible(claims, dim, x + d[0], z + d[1], attacker)) return true;
            }
        }
        return false;
    }

    private boolean neighborAccessible(IServerClaimsManagerAPI claims, ResourceLocation dim, int x, int z, OpacSides.Side attacker) {
        IPlayerChunkClaimAPI c = claims.get(dim, x, z);
        if (c == null) return true;
        if (SpecialClaimOwners.SERVER.equals(c.getPlayerId())) return false;
        return OpacSides.sameSide(attacker, OpacSides.claimSide(server, c));
    }

    private void setCapturePoint(WarRecord war, ServerLevel level) {
        int x = (war.chunkX << 4) + 8;
        int z = (war.chunkZ << 4) + 8;
        int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
        war.captureX = x;
        war.captureY = y;
        war.captureZ = z;
        war.capturePointSet = true;
    }

    public CaptureCounts countCaptureZone(WarRecord war) {
        int attackers = 0;
        int defenders = 0;
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            if (!isInsideCaptureZone(p, war)) continue;
            if (WarConfig.WAR_LIVES.get() > 0
                    && war.lives.getOrDefault(p.getUUID(), WarConfig.WAR_LIVES.get()) <= 0) {
                continue;
            }
            if (isAttacker(war, p.getUUID())) attackers++;
            else if (OpacSides.isMember(server, p.getUUID(), war.defenderPartyId, war.defenderOwnerId)) defenders++;
        }
        return new CaptureCounts(attackers, defenders);
    }

    private boolean isInsideCaptureZone(ServerPlayer player, WarRecord war) {
        if (!war.capturePointSet) return false;
        if (!player.level().dimension().location().equals(war.dimension)) return false;
        ChunkPos cp = player.chunkPosition();
        if (cp.x != war.chunkX || cp.z != war.chunkZ) return false;
        return Math.abs(player.blockPosition().getY() - war.captureY) <= WarConfig.CAPTURE_VERTICAL_TOLERANCE.get();
    }

    public CapitalResult setCapital(ServerPlayer player) {
        OpacSides.Side side = OpacSides.playerSide(server, player.getUUID());
        if (side == null || side.partyId() == null) {
            return CapitalResult.fail("You must be in an OPaC party to establish a capital.");
        }

        IServerPartyAPI party = OpenPACServerAPI.get(server).getPartyManager().getPartyById(side.partyId());
        if (party == null || !party.getOwner().getUUID().equals(player.getUUID())) {
            return CapitalResult.fail("Only the party owner can establish the capital.");
        }

        CapitalRecord existing = data.getCapital(side.partyId());
        if (existing != null) {
            return CapitalResult.fail("Your nation already has a capital at " + existing.dimension
                    + " [" + existing.chunkX + ", " + existing.chunkZ + "]. An admin must clear it before relocation.");
        }

        ResourceLocation dim = player.level().dimension().location();
        ChunkPos cp = player.chunkPosition();
        if (anyWarAt(dim, cp.x, cp.z) != null) {
            return CapitalResult.fail("A chunk in an active or preparing war cannot become a capital.");
        }

        IServerClaimsManagerAPI claims = OpenPACServerAPI.get(server).getServerClaimsManager();
        IPlayerChunkClaimAPI claim = claims.get(dim, cp.x, cp.z);
        if (claim == null || SpecialClaimOwners.SERVER.equals(claim.getPlayerId())
                || !OpacSides.sameSide(side, OpacSides.claimSide(server, claim))) {
            return CapitalResult.fail("Stand inside a chunk owned by your party to establish the capital.");
        }

        CapitalRecord capital = new CapitalRecord(side.partyId());
        capital.ownerId = side.ownerId();
        capital.dimension = dim;
        capital.chunkX = cp.x;
        capital.chunkZ = cp.z;
        data.putCapital(capital);

        broadcast(Component.literal("CAPITAL: " + side.name() + " established its capital at chunk ["
                + cp.x + ", " + cp.z + "].").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
        return CapitalResult.ok(capital);
    }

    public boolean clearCapital(UUID partyId) {
        return data.removeCapital(partyId) != null;
    }

    @Nullable
    public String validateNormalClaimAction(UUID playerId, ResourceLocation dim, int x, int z,
                                            ClaimingAction action, IServerClaimsManagerAPI claims) {
        OpacSides.Side side = OpacSides.playerSide(server, playerId);
        if (side == null || side.partyId() == null) return null;

        if (action == ClaimingAction.CLAIM && WarConfig.REQUIRE_CONTIGUOUS_CLAIMS.get()) {
            IPlayerChunkClaimAPI current = claims.get(dim, x, z);
            if (current != null) return null;

            if (!sideHasAnyClaimInDimension(claims, side, dim)) return null;

            CapitalRecord capital = data.getCapital(side.partyId());
            if (capital != null && capital.dimension.equals(dim)) {
                Set<ChunkPos> connected = capitalConnectedClaims(claims, side, dim, null);
                for (int[] d : CARDINAL) {
                    if (connected.contains(new ChunkPos(x + d[0], z + d[1]))) return null;
                }
                return "New claims must touch territory connected to your capital on a north/south/east/west side.";
            }

            for (int[] d : CARDINAL) {
                if (isFriendlyClaim(claims, side, dim, x + d[0], z + d[1])) return null;
            }
            return "New claims must touch your nation on a north/south/east/west side.";
        }

        if (action == ClaimingAction.UNCLAIM) {
            IPlayerChunkClaimAPI current = claims.get(dim, x, z);
            if (current == null || SpecialClaimOwners.SERVER.equals(current.getPlayerId())
                    || !OpacSides.sameSide(side, OpacSides.claimSide(server, current))) {
                return null;
            }

            CapitalRecord capital = data.getCapital(side.partyId());
            if (capital != null && capital.targets(dim, x, z)) {
                return "Your capital chunk cannot be unclaimed. An admin must clear or relocate the capital first.";
            }

            if (WarConfig.PREVENT_CLAIM_DISCONNECTION.get()
                    && wouldDisconnectTerritory(claims, side, dim, x, z)) {
                return "That unclaim would split your nation's territory into disconnected sections.";
            }
        }

        return null;
    }

    private boolean sideHasAnyClaimInDimension(IServerClaimsManagerAPI claims, OpacSides.Side side, ResourceLocation dim) {
        return claims.getPlayerInfoStream().anyMatch(info -> {
            if (!OpacSides.isMember(server, info.getPlayerId(), side.partyId(), side.ownerId())) return false;
            var dimensionClaims = info.getDimension(dim);
            if (dimensionClaims == null) return false;
            return dimensionClaims.getStream().anyMatch(list -> list.getCount() > 0);
        });
    }

    private boolean isFriendlyClaim(IServerClaimsManagerAPI claims, OpacSides.Side side,
                                    ResourceLocation dim, int x, int z) {
        IPlayerChunkClaimAPI claim = claims.get(dim, x, z);
        if (claim == null || SpecialClaimOwners.SERVER.equals(claim.getPlayerId())) return false;
        return OpacSides.sameSide(side, OpacSides.claimSide(server, claim));
    }

    private Set<ChunkPos> capitalConnectedClaims(IServerClaimsManagerAPI claims, OpacSides.Side side,
                                                 ResourceLocation dim, @Nullable ChunkPos ignored) {
        Set<ChunkPos> visited = new HashSet<>();
        if (side.partyId() == null) return visited;

        CapitalRecord capital = data.getCapital(side.partyId());
        if (capital == null || !capital.dimension.equals(dim)) return visited;

        ChunkPos root = new ChunkPos(capital.chunkX, capital.chunkZ);
        if (ignored != null && root.equals(ignored)) return visited;
        if (!isFriendlyClaim(claims, side, dim, root.x, root.z)) return visited;

        ArrayDeque<ChunkPos> queue = new ArrayDeque<>();
        visited.add(root);
        queue.add(root);

        while (!queue.isEmpty()) {
            ChunkPos at = queue.removeFirst();
            for (int[] d : CARDINAL) {
                ChunkPos next = new ChunkPos(at.x + d[0], at.z + d[1]);
                if (ignored != null && next.equals(ignored)) continue;
                if (visited.contains(next)) continue;
                if (!isFriendlyClaim(claims, side, dim, next.x, next.z)) continue;
                visited.add(next);
                queue.addLast(next);
            }
        }
        return visited;
    }

    private int attackDistanceFromCapitalNetwork(IServerClaimsManagerAPI claims, OpacSides.Side side,
                                                 ResourceLocation dim, ChunkPos target) {
        Set<ChunkPos> connected = capitalConnectedClaims(claims, side, dim, null);
        if (connected.isEmpty()) return -1;

        int best = Integer.MAX_VALUE;
        for (ChunkPos cp : connected) {
            int distance = Math.abs(cp.x - target.x) + Math.abs(cp.z - target.z);
            if (distance < best) best = distance;
        }
        return best;
    }

    private boolean wouldDisconnectTerritory(IServerClaimsManagerAPI claims, OpacSides.Side side,
                                             ResourceLocation dim, int x, int z) {
        ChunkPos removed = new ChunkPos(x, z);
        List<ChunkPos> neighbors = new ArrayList<>();

        for (int[] d : CARDINAL) {
            int nx = x + d[0];
            int nz = z + d[1];
            if (isFriendlyClaim(claims, side, dim, nx, nz)) {
                neighbors.add(new ChunkPos(nx, nz));
            }
        }

        if (neighbors.size() <= 1) return false;

        Set<ChunkPos> reachable = new HashSet<>();
        ArrayDeque<ChunkPos> queue = new ArrayDeque<>();
        ChunkPos start = neighbors.get(0);
        reachable.add(start);
        queue.add(start);

        while (!queue.isEmpty()) {
            ChunkPos at = queue.removeFirst();
            for (int[] d : CARDINAL) {
                ChunkPos next = new ChunkPos(at.x + d[0], at.z + d[1]);
                if (next.equals(removed) || reachable.contains(next)) continue;
                if (!isFriendlyClaim(claims, side, dim, next.x, next.z)) continue;
                reachable.add(next);
                queue.addLast(next);
            }
        }

        for (int i = 1; i < neighbors.size(); i++) {
            if (!reachable.contains(neighbors.get(i))) return true;
        }
        return false;
    }

    public void adminStop(WarRecord war, boolean restoreDefender) {
        if (restoreDefender) restoreOriginalClaim(war);
        data.remove(war.id);
        broadcast(Component.literal("WAR: battle at [" + war.chunkX + ", " + war.chunkZ + "] was stopped.")
                .withStyle(ChatFormatting.YELLOW));
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
            broadcast(Component.literal("WAR: battle cancelled because target ownership changed before activation.")
                    .withStyle(ChatFormatting.RED));
            return;
        }

        claims.claim(war.dimension, SpecialClaimOwners.SERVER, 0, war.chunkX, war.chunkZ, false);
        war.phase = WarPhase.ACTIVE;
        war.progress = 0.5D;
        data.changed();

        broadcast(Component.literal("WAR ACTIVE: " + attackerName(war) + " vs " + defenderName(war)
                + " at [" + war.chunkX + ", " + war.chunkZ + "]"
                + (war.capturePointSet ? " | capture level Y=" + war.captureY + " +/-"
                + WarConfig.CAPTURE_VERTICAL_TOLERANCE.get() : ""))
                .withStyle(ChatFormatting.RED, ChatFormatting.BOLD));
    }

    private void finish(WarRecord war, boolean attackerWon) {
        IServerClaimsManagerAPI claims = OpenPACServerAPI.get(server).getServerClaimsManager();
        UUID newOwner = attackerWon ? war.attackerOwnerId : war.originalClaimOwner;
        int sub = attackerWon ? 0 : war.originalSubConfig;
        boolean forceload = attackerWon ? false : war.originalForceload;

        claims.claim(war.dimension, newOwner, sub, war.chunkX, war.chunkZ, forceload);
        boolean capturedCapital = attackerWon
                && war.defenderPartyId != null
                && isCapitalChunk(war.defenderPartyId, war.dimension, war.chunkX, war.chunkZ);

        data.remove(war.id);

        broadcast(Component.literal(attackerWon
                ? "WAR WON: " + attackerName(war) + " captured chunk [" + war.chunkX + ", " + war.chunkZ + "] from " + defenderName(war) + "."
                : "WAR DEFENDED: " + defenderName(war) + " held chunk [" + war.chunkX + ", " + war.chunkZ + "] against " + attackerName(war) + ".")
                .withStyle(attackerWon ? ChatFormatting.GREEN : ChatFormatting.AQUA, ChatFormatting.BOLD));

        if (capturedCapital) {
            collapseNation(war.defenderPartyId, war.defenderOwnerId);
        }
    }

    private boolean isCapitalChunk(UUID partyId, ResourceLocation dim, int x, int z) {
        CapitalRecord capital = data.getCapital(partyId);
        return capital != null && capital.targets(dim, x, z);
    }

    private void collapseNation(UUID partyId, UUID ownerId) {
        String name = OpacSides.sideName(server, partyId, ownerId);
        IServerClaimsManagerAPI claims = OpenPACServerAPI.get(server).getServerClaimsManager();
        OpacSides.Side losingSide = new OpacSides.Side(partyId, ownerId, name);

        List<ClaimLocation> ownedClaims = collectClaimsForSide(claims, losingSide);
        for (ClaimLocation location : ownedClaims) {
            claims.unclaim(location.dimension, location.x, location.z);
        }

        List<WarRecord> otherWars = new ArrayList<>(data.wars());
        for (WarRecord other : otherWars) {
            boolean losingAttacker = partyId.equals(other.attackerPartyId);
            boolean losingDefender = partyId.equals(other.defenderPartyId);
            if (!losingAttacker && !losingDefender) continue;

            if (losingAttacker) {
                if (other.phase == WarPhase.ACTIVE) restoreOriginalClaim(other);
            } else if (losingDefender && other.phase == WarPhase.ACTIVE) {
                claims.unclaim(other.dimension, other.chunkX, other.chunkZ);
            }

            data.remove(other.id);
        }

        data.removeCapital(partyId);
        broadcast(Component.literal("CAPITAL FALLEN: " + name
                + " lost its capital. All remaining national claims were removed.")
                .withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD));
    }

    private List<ClaimLocation> collectClaimsForSide(IServerClaimsManagerAPI claims, OpacSides.Side side) {
        List<ClaimLocation> result = new ArrayList<>();
        claims.getPlayerInfoStream().forEach(info -> {
            if (!OpacSides.isMember(server, info.getPlayerId(), side.partyId(), side.ownerId())) return;
            info.getStream().forEach(entry -> {
                ResourceLocation dim = entry.getKey();
                entry.getValue().getStream().forEach(list ->
                        list.getStream().forEach(cp -> result.add(new ClaimLocation(dim, cp.x, cp.z))));
            });
        });
        return result;
    }

    private void restoreOriginalClaim(WarRecord war) {
        OpenPACServerAPI.get(server).getServerClaimsManager().claim(
                war.dimension,
                war.originalClaimOwner,
                war.originalSubConfig,
                war.chunkX,
                war.chunkZ,
                war.originalForceload);
    }

    private void tickSecond() {
        tickCapitalMarkers();

        List<WarRecord> snapshot = new ArrayList<>(data.wars());
        for (WarRecord war : snapshot) {
            ServerLevel level = level(war.dimension);
            if (level == null) continue;

            if (!war.capturePointSet) {
                setCapturePoint(war, level);
                data.changed();
            }
            showCaptureObjective(war, level);

            if (war.phase == WarPhase.PREPARING) {
                if (level.getGameTime() >= war.activateAtGameTime) activate(war);
                continue;
            }

            CaptureCounts counts = countCaptureZone(war);
            int attackers = counts.attackers;
            int defenders = counts.defenders;

            double perSecond = 0.5D / WarConfig.CAPTURE_SECONDS_FROM_MIDPOINT.get();
            if (attackers > defenders) {
                war.progress += perSecond * (attackers - defenders);
            } else if (defenders > attackers) {
                war.progress -= perSecond * (defenders - attackers);
            } else if (attackers == 0 && defenders == 0) {
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

    private void showCaptureObjective(WarRecord war, ServerLevel level) {
        double y = war.captureY + 0.35D;
        int minX = war.chunkX << 4;
        int minZ = war.chunkZ << 4;

        level.sendParticles(ParticleTypes.END_ROD, war.captureX + 0.5D, y, war.captureZ + 0.5D,
                8, 1.4D, 0.25D, 1.4D, 0.01D);

        int[][] corners = {{1,1},{14,1},{1,14},{14,14}};
        for (int[] c : corners) {
            level.sendParticles(ParticleTypes.END_ROD, minX + c[0] + 0.5D, y, minZ + c[1] + 0.5D,
                    2, 0.2D, 0.15D, 0.2D, 0.0D);
        }
    }

    private void tickCapitalMarkers() {
        for (CapitalRecord capital : new ArrayList<>(data.capitals())) {
            ServerLevel level = level(capital.dimension);
            if (level == null) continue;

            int x = (capital.chunkX << 4) + 8;
            int z = (capital.chunkZ << 4) + 8;
            int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
            String name = OpacSides.sideName(server, capital.partyId, capital.ownerId);

            level.sendParticles(ParticleTypes.TOTEM_OF_UNDYING, x + 0.5D, y + 1.0D, z + 0.5D,
                    10, 1.2D, 2.5D, 1.2D, 0.03D);
            level.sendParticles(ParticleTypes.END_ROD, x + 0.5D, y + 5.0D, z + 0.5D,
                    5, 0.35D, 3.0D, 0.35D, 0.01D);

            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                if (!player.level().dimension().location().equals(capital.dimension)) continue;
                ChunkPos cp = player.chunkPosition();
                if (cp.x == capital.chunkX && cp.z == capital.chunkZ) {
                    player.displayClientMessage(
                            Component.literal("★ CAPITAL — " + name + " ★")
                                    .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD),
                            true);
                }
            }
        }
    }

    private void notifyPlayersInWar(WarRecord war, int attackers, int defenders) {
        int pct = (int)Math.round(war.progress * 100D);
        Component msg = Component.literal(attackerName(war) + " vs " + defenderName(war)
                + " | " + pct + "% attacker control"
                + " | A:" + attackers + " D:" + defenders
                + " | Y=" + war.captureY + " +/-" + WarConfig.CAPTURE_VERTICAL_TOLERANCE.get())
                .withStyle(ChatFormatting.YELLOW);

        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            if (isParticipant(war, p.getUUID(), false)) p.displayClientMessage(msg, true);
        }
    }

    @Nullable
    private ServerLevel level(ResourceLocation dimension) {
        return server.getLevel(ResourceKey.create(Registries.DIMENSION, dimension));
    }

    private void broadcast(Component c) {
        server.getPlayerList().broadcastSystemMessage(c, false);
    }

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

        player.sendSystemMessage(Component.literal("War lives remaining: " + left)
                .withStyle(left > 0 ? ChatFormatting.YELLOW : ChatFormatting.RED));
    }

    public record CaptureCounts(int attackers, int defenders) {}

    public record StartResult(boolean success, String message, WarRecord war) {
        public static StartResult fail(String message) {
            return new StartResult(false, message, null);
        }

        public static StartResult ok(WarRecord war) {
            return new StartResult(true, "War preparation started.", war);
        }
    }

    public record CapitalResult(boolean success, String message, CapitalRecord capital) {
        public static CapitalResult fail(String message) {
            return new CapitalResult(false, message, null);
        }

        public static CapitalResult ok(CapitalRecord capital) {
            return new CapitalResult(true, "Capital established.", capital);
        }
    }

    private record ClaimLocation(ResourceLocation dimension, int x, int z) {}
}
