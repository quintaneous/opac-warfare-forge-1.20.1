package com.quin.opacwarfare1201.war;

import com.quin.opacwarfare1201.OpacWarfare1201;
import com.quin.opacwarfare1201.config.WarConfig;
import com.quin.opacwarfare1201.data.WarSavedData;
import com.quin.opacwarfare1201.opac.OpacSides;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.state.BlockState;
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

    public Collection<StrategicCity> cities() {
        return Collections.unmodifiableCollection(data.cities());
    }

    @Nullable
    public CapitalRecord capital(UUID partyId) {
        return data.getCapital(partyId);
    }

    @Nullable
    public StrategicCity city(String id) {
        return data.getCity(id);
    }

    @Nullable
    public StrategicCity cityAtChunk(ResourceLocation dim, int x, int z) {
        for (StrategicCity city : data.cities()) {
            if (city.containsChunk(dim, x, z)) return city;
        }
        return null;
    }

    @Nullable
    public StrategicCity cityAtBlock(ResourceLocation dim, BlockPos pos) {
        return cityAtChunk(dim, pos.getX() >> 4, pos.getZ() >> 4);
    }

    public boolean isProtectedCityBlock(ResourceLocation dim, BlockPos pos) {
        StrategicCity city = cityAtBlock(dim, pos);
        return city != null && city.isProtected(pos);
    }

    /**
     * The balance target discussed for the first city test is 5,000 blocks for
     * a 3x3 city. Scale linearly for other admin-defined city sizes.
     */
    public int fortificationBudget(StrategicCity city) {
        int chunksWide = city.maxChunkX - city.minChunkX + 1;
        int chunksDeep = city.maxChunkZ - city.minChunkZ + 1;
        int chunks = Math.max(1, chunksWide * chunksDeep);
        int base = WarConfig.CITY_FORTIFICATION_BUDGET_3X3.get();
        if (base <= 0) return 0;
        return Math.max(1, (int)Math.ceil(base * (chunks / 9.0D)));
    }

    public int fortificationCount(StrategicCity city) {
        ServerLevel cityLevel = level(city.dimension);
        if (cityLevel != null) {
            ensureFortificationTracking(cityLevel, city);
            pruneFortificationTracking(cityLevel, city);
        }
        return city.fortificationBlocks.size();
    }

    /**
     * Called after Forge observes a placement. Returning false cancels and
     * rolls back the placement. Original city snapshot blocks never consume
     * fortification budget.
     */
    public boolean registerCityFortification(ServerLevel level, BlockPos pos) {
        return registerCityFortification(level, pos, false);
    }

    /**
     * Variant used for defender-only Create/CBC construction during an ACTIVE
     * city siege. The normal peacetime path still rejects any placement once a
     * war exists.
     */
    public boolean registerCityFortificationDuringActiveSiege(ServerLevel level, BlockPos pos) {
        return registerCityFortification(level, pos, true);
    }

    private boolean registerCityFortification(ServerLevel level, BlockPos pos, boolean allowDuringActiveCityWar) {
        StrategicCity city = cityAtBlock(level.dimension().location(), pos);
        if (city == null || city.isProtected(pos)) return true;

        WarRecord war = anyWarForCity(city.id);
        if (war != null && (!allowDuringActiveCityWar || war.phase != WarPhase.ACTIVE)) return false;

        ensureFortificationTracking(level, city);
        pruneFortificationTracking(level, city);

        long packed = pos.asLong();
        if (city.fortificationBlocks.contains(packed)) return true;

        int budget = fortificationBudget(city);
        if (city.fortificationBlocks.size() >= budget) return false;

        city.fortificationBlocks.add(packed);
        data.changed();
        return true;
    }

    public void releaseCityFortification(ServerLevel level, BlockPos pos) {
        StrategicCity city = cityAtBlock(level.dimension().location(), pos);
        if (city == null) return;
        if (city.fortificationBlocks.remove(pos.asLong())) data.changed();
    }

    private void ensureFortificationTracking(ServerLevel level, StrategicCity city) {
        if (city.fortificationTrackingInitialized) return;

        city.fortificationBlocks.clear();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int cx = city.minChunkX; cx <= city.maxChunkX; cx++) {
            int blockMinX = cx << 4;
            for (int cz = city.minChunkZ; cz <= city.maxChunkZ; cz++) {
                int blockMinZ = cz << 4;
                for (int x = blockMinX; x < blockMinX + 16; x++) {
                    for (int z = blockMinZ; z < blockMinZ + 16; z++) {
                        for (int y = level.getMinBuildHeight(); y < level.getMaxBuildHeight(); y++) {
                            pos.set(x, y, z);
                            if (city.isProtected(pos)) continue;
                            BlockState state = level.getBlockState(pos);
                            if (!state.isAir() && state.getFluidState().isEmpty()) {
                                city.fortificationBlocks.add(pos.asLong());
                            }
                        }
                    }
                }
            }
        }

        city.fortificationTrackingInitialized = true;
        data.changed();
        OpacWarfare1201.LOGGER.info("Initialized fortification accounting for city {}: {} / {} blocks",
                city.id, city.fortificationBlocks.size(), fortificationBudget(city));
    }

    private void pruneFortificationTracking(ServerLevel level, StrategicCity city) {
        boolean changed = city.fortificationBlocks.removeIf(packed -> {
            BlockPos pos = BlockPos.of(packed);
            if (!city.containsBlock(city.dimension, pos) || city.isProtected(pos)) return true;
            BlockState state = level.getBlockState(pos);
            return state.isAir() || !state.getFluidState().isEmpty();
        });
        if (changed) data.changed();
    }

    public boolean canPlayerAccessCityChunk(UUID playerId, ResourceLocation dim, int x, int z) {
        StrategicCity city = cityAtChunk(dim, x, z);
        if (city == null) return false;

        WarRecord war = activeWarForCity(city.id);
        if (war != null) return isParticipant(war, playerId, true);

        return city.controllerPartyId != null
                && OpacSides.isMember(server, playerId, city.controllerPartyId, city.controllerOwnerId);
    }

    public void onServerStarted() {
        boolean changed = false;
        for (WarRecord war : data.wars()) {
            ServerLevel level = level(war.dimension);
            if (level != null && !war.capturePointSet) {
                if (war.isCityWar()) {
                    StrategicCity city = data.getCity(war.cityId);
                    if (city != null) setCityWarCapturePoint(war, city);
                } else {
                    setCapturePoint(war, level);
                }
                changed = true;
            }
        }
        for (StrategicCity city : data.cities()) {
            ServerLevel cityLevel = level(city.dimension);
            if (cityLevel != null) {
                ensureFortificationTracking(cityLevel, city);
                pruneFortificationTracking(cityLevel, city);
            }
        }

        if (changed) data.changed();

        OpacWarfare1201.LOGGER.info("Loaded {} persisted war(s), {} capital(s), and {} strategic city/cities",
                data.wars().size(), data.capitals().size(), data.cities().size());
    }

    @Nullable
    public WarRecord activeWarAt(ResourceLocation dim, int x, int z) {
        for (WarRecord w : data.wars()) {
            if (w.phase != WarPhase.ACTIVE || !w.dimension.equals(dim)) continue;
            if (!w.isCityWar() && w.targets(dim, x, z)) return w;
            if (w.isCityWar()) {
                StrategicCity city = data.getCity(w.cityId);
                if (city != null && city.containsChunk(dim, x, z)) return w;
            }
        }
        return null;
    }

    @Nullable
    public WarRecord activeWarForCity(String cityId) {
        for (WarRecord w : data.wars()) {
            if (w.phase == WarPhase.ACTIVE && w.isCityWar() && cityId.equalsIgnoreCase(w.cityId)) return w;
        }
        return null;
    }

    @Nullable
    public WarRecord anyWarForCity(String cityId) {
        for (WarRecord w : data.wars()) {
            if (w.isCityWar() && cityId.equalsIgnoreCase(w.cityId)) return w;
        }
        return null;
    }

    @Nullable
    public WarRecord anyWarAt(ResourceLocation dim, int x, int z) {
        for (WarRecord w : data.wars()) {
            if (w.targets(dim, x, z)) return w;
            if (w.isCityWar()) {
                StrategicCity city = data.getCity(w.cityId);
                if (city != null && city.containsChunk(dim, x, z)) return w;
            }
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
        if (war.defenderPartyId == null && war.isCityWar()) {
            StrategicCity city = data.getCity(war.cityId);
            return city == null ? "Neutral" : "Neutral " + city.id;
        }
        return OpacSides.sideName(server, war.defenderPartyId, war.defenderOwnerId);
    }

    public String cityControllerName(StrategicCity city) {
        return city.controllerPartyId == null
                ? "Neutral"
                : OpacSides.sideName(server, city.controllerPartyId, city.controllerOwnerId);
    }

    public long remainingAttackCooldownSeconds(UUID partyId) {
        long until = data.attackCooldownUntil(partyId);
        if (until <= 0L) return 0L;

        long remainingMillis = until - System.currentTimeMillis();
        if (remainingMillis <= 0L) {
            data.clearAttackCooldown(partyId);
            return 0L;
        }
        return (remainingMillis + 999L) / 1000L;
    }

    private void applyFailedAttackCooldown(WarRecord war) {
        int minutes = WarConfig.FAILED_ATTACK_COOLDOWN_MINUTES.get();
        if (minutes <= 0 || war.attackerPartyId == null) return;

        long until = System.currentTimeMillis() + minutes * 60_000L;
        data.setAttackCooldownUntil(war.attackerPartyId, until);

        broadcast(Component.literal("ATTACK COOLDOWN: " + attackerName(war)
                + " failed to capture its target and cannot start another offensive war for "
                + formatCooldown(minutes * 60L) + ".")
                .withStyle(ChatFormatting.YELLOW));
    }

    public static String formatCooldown(long totalSeconds) {
        long seconds = Math.max(0L, totalSeconds);
        long hours = seconds / 3600L;
        long minutes = (seconds % 3600L) / 60L;
        long secs = seconds % 60L;

        if (hours > 0L) return hours + "h " + minutes + "m";
        if (minutes > 0L) return minutes + "m " + secs + "s";
        return secs + "s";
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
            StrategicCity city = cityAtChunk(dim, target.x, target.z);
            if (city != null) {
                return StartResult.fail("That chunk belongs to strategic city " + city.id + ". Use /war city attack " + city.id + ".");
            }
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

        String commonFailure = validateAttackerCanStartWar(attackerSide);
        if (commonFailure != null) return StartResult.fail(commonFailure);

        if (WarConfig.REQUIRE_ONLINE_DEFENDER.get()
                && !OpacSides.isOnline(server, defenderSide.partyId(), defenderSide.ownerId())) {
            return StartResult.fail("At least one defender must be online.");
        }

        if (!isAccessibleBorder(claims, dim, target.x, target.z, attackerSide)) {
            return StartResult.fail("You can only attack an exposed enemy border chunk.");
        }

        int attackDistance = attackDistanceFromValidTerritory(claims, attackerSide, dim, target);
        if (attackDistance < 0) {
            return StartResult.fail("Your nation has no valid capital- or city-anchored territory in this dimension.");
        }
        if (attackDistance > WarConfig.MAX_ATTACK_DISTANCE_CHUNKS.get()) {
            return StartResult.fail("Target is " + attackDistance + " chunks from your valid territory; Season 1 maximum is "
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

    public StartResult startCityWar(ServerPlayer attacker, String cityId) {
        StrategicCity city = data.getCity(cityId);
        if (city == null) return StartResult.fail("Unknown strategic city: " + cityId + ".");
        if (!attacker.level().dimension().location().equals(city.dimension)) {
            return StartResult.fail("You must be in the same dimension as the strategic city.");
        }
        if (anyWarForCity(city.id) != null) {
            return StartResult.fail("That strategic city is already in a war.");
        }

        OpacSides.Side attackerSide = OpacSides.playerSide(server, attacker.getUUID());
        if (attackerSide == null || attackerSide.partyId() == null) {
            return StartResult.fail("You must be in an OPaC party to attack a strategic city.");
        }
        if (city.isControlledBy(attackerSide.partyId())) {
            return StartResult.fail("Your nation already controls " + city.id + ".");
        }

        String commonFailure = validateAttackerCanStartWar(attackerSide);
        if (commonFailure != null) return StartResult.fail(commonFailure);

        if (city.controllerPartyId != null
                && WarConfig.REQUIRE_ONLINE_DEFENDER.get()
                && !OpacSides.isOnline(server, city.controllerPartyId, city.controllerOwnerId)) {
            return StartResult.fail("At least one player from the city controller must be online.");
        }

        IServerClaimsManagerAPI claims = OpenPACServerAPI.get(server).getServerClaimsManager();
        if (!hasValidTerritoryBorderingCity(claims, attackerSide, city)) {
            return StartResult.fail("To attack " + city.id
                    + ", your capital- or city-connected territory must directly border the strategic city.");
        }

        WarRecord war = new WarRecord(UUID.randomUUID());
        war.dimension = city.dimension;
        war.chunkX = city.captureChunkX;
        war.chunkZ = city.captureChunkZ;
        war.attackerPartyId = attackerSide.partyId();
        war.attackerOwnerId = attackerSide.ownerId();
        war.defenderPartyId = city.controllerPartyId;
        war.defenderOwnerId = city.controllerOwnerId;
        war.cityId = city.id;
        war.phase = WarPhase.PREPARING;
        setCityWarCapturePoint(war, city);

        ServerLevel level = level(city.dimension);
        if (level != null) {
            ensureFortificationTracking(level, city);
            pruneFortificationTracking(level, city);
        }
        long now = level == null ? server.overworld().getGameTime() : level.getGameTime();
        war.activateAtGameTime = now + WarConfig.PREPARATION_SECONDS.get() * 20L;
        war.progress = 0.5D;

        data.put(war);
        broadcast(Component.literal("CITY WAR: " + attackerSide.name() + " is preparing an attack on " + city.id
                + " (" + cityControllerName(city) + ") at capture chunk [" + city.captureChunkX + ", " + city.captureChunkZ + "]"
                + " | fortifications " + fortificationCount(city) + "/" + fortificationBudget(city) + ".")
                .withStyle(ChatFormatting.LIGHT_PURPLE, ChatFormatting.BOLD));
        return StartResult.ok(war);
    }

    @Nullable
    private String validateAttackerCanStartWar(OpacSides.Side attackerSide) {
        if (attackerSide.partyId() == null || data.getCapital(attackerSide.partyId()) == null) {
            return "Your nation must set a capital with /war capital set before starting a war.";
        }

        long cooldownSeconds = remainingAttackCooldownSeconds(attackerSide.partyId());
        if (cooldownSeconds > 0L) {
            return "Your nation is on an offensive-war cooldown after a failed attack. Time remaining: "
                    + formatCooldown(cooldownSeconds) + ".";
        }

        if (WarConfig.ONLY_ONE_OFFENSIVE_WAR_PER_SIDE.get()) {
            for (WarRecord w : data.wars()) {
                if (attackerSide.partyId().equals(w.attackerPartyId)) {
                    return "Your party already has an offensive war.";
                }
            }
        }
        return null;
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

    private void setCityWarCapturePoint(WarRecord war, StrategicCity city) {
        war.captureX = (city.captureChunkX << 4) + 8;
        war.captureY = city.captureY;
        war.captureZ = (city.captureChunkZ << 4) + 8;
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

    public CityResult createCity(ServerPlayer admin, String rawId, int radiusChunks) {
        String id = rawId.toLowerCase(Locale.ROOT);
        if (!id.matches("[a-z0-9_-]{1,32}")) {
            return CityResult.fail("City ID must be 1-32 characters using a-z, 0-9, _ or -.");
        }
        if (radiusChunks < 0 || radiusChunks > 4) {
            return CityResult.fail("City radius must be between 0 and 4 chunks.");
        }
        if (data.hasCity(id)) return CityResult.fail("A strategic city with that ID already exists.");

        ResourceLocation dim = admin.level().dimension().location();
        ServerLevel level = level(dim);
        if (level == null) return CityResult.fail("Could not access that dimension.");

        ChunkPos center = admin.chunkPosition();
        int minX = center.x - radiusChunks;
        int maxX = center.x + radiusChunks;
        int minZ = center.z - radiusChunks;
        int maxZ = center.z + radiusChunks;

        for (StrategicCity existing : data.cities()) {
            if (!existing.dimension.equals(dim)) continue;
            boolean overlaps = minX <= existing.maxChunkX && maxX >= existing.minChunkX
                    && minZ <= existing.maxChunkZ && maxZ >= existing.minChunkZ;
            if (overlaps) return CityResult.fail("That region overlaps strategic city " + existing.id + ".");
        }

        IServerClaimsManagerAPI claims = OpenPACServerAPI.get(server).getServerClaimsManager();
        if (!claims.isClaimable(dim)) return CityResult.fail("OPaC claims are disabled in this dimension.");

        for (int cx = minX; cx <= maxX; cx++) {
            for (int cz = minZ; cz <= maxZ; cz++) {
                if (claims.get(dim, cx, cz) != null) {
                    return CityResult.fail("Every city chunk must be wilderness before creation. Chunk ["
                            + cx + ", " + cz + "] is already claimed.");
                }
            }
        }

        StrategicCity city = new StrategicCity(id);
        city.dimension = dim;
        city.minChunkX = minX;
        city.minChunkZ = minZ;
        city.maxChunkX = maxX;
        city.maxChunkZ = maxZ;
        city.captureChunkX = center.x;
        city.captureChunkZ = center.z;
        int captureX = (center.x << 4) + 8;
        int captureZ = (center.z << 4) + 8;
        city.captureY = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, captureX, captureZ);
        city.fortificationTrackingInitialized = true;

        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int cx = minX; cx <= maxX; cx++) {
            int blockMinX = cx << 4;
            for (int cz = minZ; cz <= maxZ; cz++) {
                int blockMinZ = cz << 4;
                for (int x = blockMinX; x < blockMinX + 16; x++) {
                    for (int z = blockMinZ; z < blockMinZ + 16; z++) {
                        for (int y = level.getMinBuildHeight(); y < level.getMaxBuildHeight(); y++) {
                            pos.set(x, y, z);
                            BlockState state = level.getBlockState(pos);
                            if (!state.isAir() && state.getFluidState().isEmpty()) {
                                city.protectedBlocks.add(pos.asLong());
                            }
                        }
                    }
                }
            }
        }

        List<ChunkPos> claimed = new ArrayList<>();
        for (int cx = minX; cx <= maxX; cx++) {
            for (int cz = minZ; cz <= maxZ; cz++) {
                if (claims.claim(dim, SpecialClaimOwners.SERVER, 0, cx, cz, false) == null) {
                    for (ChunkPos cp : claimed) claims.unclaim(dim, cp.x, cp.z);
                    return CityResult.fail("OPaC failed to reserve the city region. Creation was rolled back.");
                }
                claimed.add(new ChunkPos(cx, cz));
            }
        }

        data.putCity(city);
        broadcast(Component.literal("STRATEGIC CITY CREATED: " + city.id + " | "
                + ((maxX - minX + 1) * (maxZ - minZ + 1)) + " chunks | "
                + city.protectedBlocks.size() + " permanent protected blocks.")
                .withStyle(ChatFormatting.LIGHT_PURPLE));
        return CityResult.ok(city, city.protectedBlocks.size());
    }

    public CityResult deleteCity(String cityId) {
        StrategicCity city = data.getCity(cityId);
        if (city == null) return CityResult.fail("Unknown strategic city: " + cityId + ".");
        if (anyWarForCity(city.id) != null) return CityResult.fail("Stop the city's active/preparing war before deleting it.");

        IServerClaimsManagerAPI claims = OpenPACServerAPI.get(server).getServerClaimsManager();
        for (int cx = city.minChunkX; cx <= city.maxChunkX; cx++) {
            for (int cz = city.minChunkZ; cz <= city.maxChunkZ; cz++) {
                IPlayerChunkClaimAPI claim = claims.get(city.dimension, cx, cz);
                if (claim != null && SpecialClaimOwners.SERVER.equals(claim.getPlayerId())) {
                    claims.unclaim(city.dimension, cx, cz);
                }
            }
        }

        data.removeCity(city.id);
        broadcast(Component.literal("STRATEGIC CITY REMOVED: " + city.id + ".")
                .withStyle(ChatFormatting.YELLOW));
        return CityResult.ok(city, city.protectedBlocks.size());
    }

    public CityResult setCityController(String cityId, @Nullable UUID partyId) {
        StrategicCity city = data.getCity(cityId);
        if (city == null) return CityResult.fail("Unknown strategic city: " + cityId + ".");
        if (anyWarForCity(city.id) != null) return CityResult.fail("Cannot change city controller during a war.");

        if (partyId == null) {
            city.controllerPartyId = null;
            city.controllerOwnerId = null;
            data.changed();
            broadcast(Component.literal("CITY CONTROL: " + city.id + " is now Neutral.")
                    .withStyle(ChatFormatting.LIGHT_PURPLE));
            return CityResult.ok(city, city.protectedBlocks.size());
        }

        IServerPartyAPI party = OpenPACServerAPI.get(server).getPartyManager().getPartyById(partyId);
        if (party == null) return CityResult.fail("No OPaC party exists with UUID " + partyId + ".");

        city.controllerPartyId = party.getId();
        city.controllerOwnerId = party.getOwner().getUUID();
        data.changed();
        broadcast(Component.literal("CITY CONTROL: " + city.id + " is now controlled by " + party.getDefaultName() + ".")
                .withStyle(ChatFormatting.LIGHT_PURPLE));
        return CityResult.ok(city, city.protectedBlocks.size());
    }

    @Nullable
    public String validateNormalClaimAction(UUID playerId, ResourceLocation dim, int x, int z,
                                            ClaimingAction action, IServerClaimsManagerAPI claims) {
        OpacSides.Side side = OpacSides.playerSide(server, playerId);
        if (side == null || side.partyId() == null) return null;

        if (action == ClaimingAction.CLAIM && WarConfig.REQUIRE_CONTIGUOUS_CLAIMS.get()) {
            IPlayerChunkClaimAPI current = claims.get(dim, x, z);
            if (current != null) return null;

            boolean anyClaims = sideHasAnyClaimInDimension(claims, side, dim);
            boolean hasAnchor = hasValidAnchorInDimension(side, dim);

            if (!anyClaims && !hasAnchor) return null;

            Set<ChunkPos> valid = validConnectedClaims(claims, side, dim, null);
            for (int[] d : CARDINAL) {
                if (valid.contains(new ChunkPos(x + d[0], z + d[1]))) return null;
            }
            if (touchesControlledCity(side, dim, x, z)) return null;

            return "New claims must touch territory connected to your capital or a controlled strategic city on a north/south/east/west side.";
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

    private boolean hasValidAnchorInDimension(OpacSides.Side side, ResourceLocation dim) {
        CapitalRecord capital = side.partyId() == null ? null : data.getCapital(side.partyId());
        if (capital != null && capital.dimension.equals(dim)) return true;
        for (StrategicCity city : data.cities()) {
            if (city.dimension.equals(dim) && city.isControlledBy(side.partyId())) return true;
        }
        return false;
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

    private boolean touchesControlledCity(OpacSides.Side side, ResourceLocation dim, int x, int z) {
        for (int[] d : CARDINAL) {
            StrategicCity city = cityAtChunk(dim, x + d[0], z + d[1]);
            if (city != null && city.isControlledBy(side.partyId())) return true;
        }
        return false;
    }

    private Set<ChunkPos> validConnectedClaims(IServerClaimsManagerAPI claims, OpacSides.Side side,
                                               ResourceLocation dim, @Nullable ChunkPos ignored) {
        Set<ChunkPos> visited = new HashSet<>();
        ArrayDeque<ChunkPos> queue = new ArrayDeque<>();

        if (side.partyId() == null) return visited;

        CapitalRecord capital = data.getCapital(side.partyId());
        if (capital != null && capital.dimension.equals(dim)) {
            ChunkPos root = new ChunkPos(capital.chunkX, capital.chunkZ);
            if ((ignored == null || !root.equals(ignored))
                    && isFriendlyClaim(claims, side, dim, root.x, root.z)) {
                visited.add(root);
                queue.add(root);
            }
        }

        for (StrategicCity city : data.cities()) {
            if (!city.dimension.equals(dim) || !city.isControlledBy(side.partyId())) continue;
            addCityAdjacentClaimSeeds(claims, side, city, ignored, visited, queue);
        }

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

    private void addCityAdjacentClaimSeeds(IServerClaimsManagerAPI claims, OpacSides.Side side, StrategicCity city,
                                           @Nullable ChunkPos ignored, Set<ChunkPos> visited, ArrayDeque<ChunkPos> queue) {
        for (int x = city.minChunkX; x <= city.maxChunkX; x++) {
            addClaimSeed(claims, side, city.dimension, new ChunkPos(x, city.minChunkZ - 1), ignored, visited, queue);
            addClaimSeed(claims, side, city.dimension, new ChunkPos(x, city.maxChunkZ + 1), ignored, visited, queue);
        }
        for (int z = city.minChunkZ; z <= city.maxChunkZ; z++) {
            addClaimSeed(claims, side, city.dimension, new ChunkPos(city.minChunkX - 1, z), ignored, visited, queue);
            addClaimSeed(claims, side, city.dimension, new ChunkPos(city.maxChunkX + 1, z), ignored, visited, queue);
        }
    }

    private void addClaimSeed(IServerClaimsManagerAPI claims, OpacSides.Side side, ResourceLocation dim,
                              ChunkPos seed, @Nullable ChunkPos ignored, Set<ChunkPos> visited, ArrayDeque<ChunkPos> queue) {
        if (ignored != null && seed.equals(ignored)) return;
        if (visited.contains(seed)) return;
        if (!isFriendlyClaim(claims, side, dim, seed.x, seed.z)) return;
        visited.add(seed);
        queue.add(seed);
    }

    private int attackDistanceFromValidTerritory(IServerClaimsManagerAPI claims, OpacSides.Side side,
                                                 ResourceLocation dim, ChunkPos target) {
        Set<ChunkPos> connected = validConnectedClaims(claims, side, dim, null);
        int best = Integer.MAX_VALUE;

        for (ChunkPos cp : connected) {
            best = Math.min(best, manhattan(cp.x, cp.z, target.x, target.z));
        }

        for (StrategicCity city : data.cities()) {
            if (!city.dimension.equals(dim) || !city.isControlledBy(side.partyId())) continue;
            best = Math.min(best, distanceChunkToCity(target, city));
        }

        return best == Integer.MAX_VALUE ? -1 : best;
    }

    private boolean hasValidTerritoryBorderingCity(IServerClaimsManagerAPI claims, OpacSides.Side side,
                                                    StrategicCity city) {
        Set<ChunkPos> valid = validConnectedClaims(claims, side, city.dimension, null);
        if (valid.isEmpty()) return false;

        for (int x = city.minChunkX; x <= city.maxChunkX; x++) {
            if (valid.contains(new ChunkPos(x, city.minChunkZ - 1))) return true;
            if (valid.contains(new ChunkPos(x, city.maxChunkZ + 1))) return true;
        }
        for (int z = city.minChunkZ; z <= city.maxChunkZ; z++) {
            if (valid.contains(new ChunkPos(city.minChunkX - 1, z))) return true;
            if (valid.contains(new ChunkPos(city.maxChunkX + 1, z))) return true;
        }
        return false;
    }

    private int attackDistanceToCity(IServerClaimsManagerAPI claims, OpacSides.Side side, StrategicCity targetCity) {
        Set<ChunkPos> connected = validConnectedClaims(claims, side, targetCity.dimension, null);
        int best = Integer.MAX_VALUE;

        for (ChunkPos cp : connected) {
            best = Math.min(best, distanceChunkToCity(cp, targetCity));
        }

        for (StrategicCity anchor : data.cities()) {
            if (!anchor.dimension.equals(targetCity.dimension)
                    || !anchor.isControlledBy(side.partyId())
                    || anchor.id.equalsIgnoreCase(targetCity.id)) {
                continue;
            }
            best = Math.min(best, distanceBetweenCities(anchor, targetCity));
        }

        return best == Integer.MAX_VALUE ? -1 : best;
    }

    private int distanceChunkToCity(ChunkPos cp, StrategicCity city) {
        int dx = cp.x < city.minChunkX ? city.minChunkX - cp.x : Math.max(0, cp.x - city.maxChunkX);
        int dz = cp.z < city.minChunkZ ? city.minChunkZ - cp.z : Math.max(0, cp.z - city.maxChunkZ);
        return dx + dz;
    }

    private int distanceBetweenCities(StrategicCity a, StrategicCity b) {
        int dx = a.maxChunkX < b.minChunkX ? b.minChunkX - a.maxChunkX
                : b.maxChunkX < a.minChunkX ? a.minChunkX - b.maxChunkX : 0;
        int dz = a.maxChunkZ < b.minChunkZ ? b.minChunkZ - a.maxChunkZ
                : b.maxChunkZ < a.minChunkZ ? a.minChunkZ - b.maxChunkZ : 0;
        return dx + dz;
    }

    private int manhattan(int x1, int z1, int x2, int z2) {
        return Math.abs(x1 - x2) + Math.abs(z1 - z2);
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
        if (restoreDefender && !war.isCityWar()) restoreOriginalClaim(war);
        data.remove(war.id);
        broadcast(Component.literal("WAR: battle at [" + war.chunkX + ", " + war.chunkZ + "] was stopped.")
                .withStyle(ChatFormatting.YELLOW));
    }

    public void surrender(ServerPlayer player, WarRecord war) {
        if (isAttacker(war, player.getUUID())) finish(war, false);
        else if (OpacSides.isMember(server, player.getUUID(), war.defenderPartyId, war.defenderOwnerId)) finish(war, true);
    }

    private void activate(WarRecord war) {
        if (war.isCityWar()) {
            StrategicCity city = data.getCity(war.cityId);
            if (city == null
                    || !Objects.equals(city.controllerPartyId, war.defenderPartyId)
                    || !Objects.equals(city.controllerOwnerId, war.defenderOwnerId)) {
                data.remove(war.id);
                broadcast(Component.literal("CITY WAR: battle cancelled because city control changed before activation.")
                        .withStyle(ChatFormatting.RED));
                return;
            }

            war.phase = WarPhase.ACTIVE;
            war.progress = 0.5D;
            data.changed();
            broadcast(Component.literal("CITY WAR ACTIVE: " + attackerName(war) + " is assaulting " + city.id
                    + " (" + defenderName(war) + "). Capture chunk [" + war.chunkX + ", " + war.chunkZ + "] at Y="
                    + war.captureY + " +/-" + WarConfig.CAPTURE_VERTICAL_TOLERANCE.get() + ".")
                    .withStyle(ChatFormatting.DARK_PURPLE, ChatFormatting.BOLD));
            return;
        }

        IServerClaimsManagerAPI claims = OpenPACServerAPI.get(server).getServerClaimsManager();
        IPlayerChunkClaimAPI current = claims.get(war.dimension, war.chunkX, war.chunkZ);
        if (current == null || war.originalClaimOwner == null || !current.getPlayerId().equals(war.originalClaimOwner)) {
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
        if (war.isCityWar()) {
            StrategicCity city = data.getCity(war.cityId);
            if (city == null) {
                data.remove(war.id);
                return;
            }

            if (attackerWon) {
                String previous = cityControllerName(city);
                city.controllerPartyId = war.attackerPartyId;
                city.controllerOwnerId = war.attackerOwnerId;
                data.remove(war.id);
                data.changed();
                broadcast(Component.literal("CITY CAPTURED: " + attackerName(war) + " took " + city.id
                        + " from " + previous + ". The city is now a territorial anchor for " + attackerName(war) + ".")
                        .withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD));
            } else {
                applyFailedAttackCooldown(war);
                data.remove(war.id);
                broadcast(Component.literal("CITY DEFENDED: " + city.id + " remains controlled by " + cityControllerName(city) + ".")
                        .withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD));
            }
            return;
        }

        IServerClaimsManagerAPI claims = OpenPACServerAPI.get(server).getServerClaimsManager();
        UUID newOwner = attackerWon ? war.attackerOwnerId : war.originalClaimOwner;
        if (newOwner == null) {
            data.remove(war.id);
            return;
        }

        int sub = attackerWon ? 0 : war.originalSubConfig;
        boolean forceload = attackerWon ? false : war.originalForceload;
        claims.claim(war.dimension, newOwner, sub, war.chunkX, war.chunkZ, forceload);

        boolean capturedCapital = attackerWon
                && war.defenderPartyId != null
                && isCapitalChunk(war.defenderPartyId, war.dimension, war.chunkX, war.chunkZ);

        if (!attackerWon) applyFailedAttackCooldown(war);
        data.remove(war.id);

        broadcast(Component.literal(attackerWon
                ? "WAR WON: " + attackerName(war) + " captured chunk [" + war.chunkX + ", " + war.chunkZ + "] from " + defenderName(war) + "."
                : "WAR DEFENDED: " + defenderName(war) + " held chunk [" + war.chunkX + ", " + war.chunkZ + "] against " + attackerName(war) + ".")
                .withStyle(attackerWon ? ChatFormatting.GREEN : ChatFormatting.AQUA, ChatFormatting.BOLD));

        if (capturedCapital) collapseNation(war.defenderPartyId, war.defenderOwnerId);
    }

    private boolean isCapitalChunk(UUID partyId, ResourceLocation dim, int x, int z) {
        CapitalRecord capital = data.getCapital(partyId);
        return capital != null && capital.targets(dim, x, z);
    }

    private void collapseNation(UUID partyId, @Nullable UUID ownerId) {
        String name = OpacSides.sideName(server, partyId, ownerId);
        IServerClaimsManagerAPI claims = OpenPACServerAPI.get(server).getServerClaimsManager();
        OpacSides.Side losingSide = new OpacSides.Side(partyId, ownerId == null ? partyId : ownerId, name);

        List<ClaimLocation> ownedClaims = collectClaimsForSide(claims, losingSide);
        for (ClaimLocation location : ownedClaims) {
            claims.unclaim(location.dimension, location.x, location.z);
        }

        for (StrategicCity city : data.cities()) {
            if (partyId.equals(city.controllerPartyId)) {
                city.controllerPartyId = null;
                city.controllerOwnerId = null;
            }
        }

        List<WarRecord> otherWars = new ArrayList<>(data.wars());
        for (WarRecord other : otherWars) {
            boolean losingAttacker = partyId.equals(other.attackerPartyId);
            boolean losingDefender = partyId.equals(other.defenderPartyId);
            if (!losingAttacker && !losingDefender) continue;

            if (!other.isCityWar()) {
                if (losingAttacker && other.phase == WarPhase.ACTIVE) {
                    restoreOriginalClaim(other);
                } else if (losingDefender && other.phase == WarPhase.ACTIVE) {
                    claims.unclaim(other.dimension, other.chunkX, other.chunkZ);
                }
            }
            data.remove(other.id);
        }

        data.removeCapital(partyId);
        data.changed();

        broadcast(Component.literal("CAPITAL FALLEN: " + name
                + " lost its capital. All remaining national claims were removed and its strategic cities became neutral.")
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
        if (war.originalClaimOwner == null) return;
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
        tickCityMarkers();

        List<WarRecord> snapshot = new ArrayList<>(data.wars());
        for (WarRecord war : snapshot) {
            ServerLevel level = level(war.dimension);
            if (level == null) continue;

            if (!war.capturePointSet) {
                if (war.isCityWar()) {
                    StrategicCity city = data.getCity(war.cityId);
                    if (city != null) setCityWarCapturePoint(war, city);
                } else {
                    setCapturePoint(war, level);
                }
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

    private void tickCityMarkers() {
        for (StrategicCity city : new ArrayList<>(data.cities())) {
            ServerLevel level = level(city.dimension);
            if (level == null) continue;

            int x = (city.captureChunkX << 4) + 8;
            int z = (city.captureChunkZ << 4) + 8;
            level.sendParticles(ParticleTypes.ENCHANT, x + 0.5D, city.captureY + 1.0D, z + 0.5D,
                    12, 1.6D, 1.0D, 1.6D, 0.02D);

            String controller = cityControllerName(city);
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                if (!player.level().dimension().location().equals(city.dimension)) continue;
                ChunkPos cp = player.chunkPosition();
                if (city.containsChunk(city.dimension, cp.x, cp.z)) {
                    player.displayClientMessage(
                            Component.literal("◆ STRATEGIC CITY — " + city.id + " — " + controller + " ◆")
                                    .withStyle(ChatFormatting.LIGHT_PURPLE, ChatFormatting.BOLD),
                            true);
                }
            }
        }
    }

    private void notifyPlayersInWar(WarRecord war, int attackers, int defenders) {
        int pct = (int)Math.round(war.progress * 100D);
        String target = war.isCityWar() ? " | CITY:" + war.cityId : "";
        Component msg = Component.literal(attackerName(war) + " vs " + defenderName(war)
                + target
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

    public record StartResult(boolean success, String message, @Nullable WarRecord war) {
        public static StartResult fail(String message) {
            return new StartResult(false, message, null);
        }

        public static StartResult ok(WarRecord war) {
            return new StartResult(true, "War preparation started.", war);
        }
    }

    public record CapitalResult(boolean success, String message, @Nullable CapitalRecord capital) {
        public static CapitalResult fail(String message) {
            return new CapitalResult(false, message, null);
        }

        public static CapitalResult ok(CapitalRecord capital) {
            return new CapitalResult(true, "Capital established.", capital);
        }
    }

    public record CityResult(boolean success, String message, @Nullable StrategicCity city, int protectedBlockCount) {
        public static CityResult fail(String message) {
            return new CityResult(false, message, null, 0);
        }

        public static CityResult ok(StrategicCity city, int protectedBlockCount) {
            return new CityResult(true, "Strategic city updated.", city, protectedBlockCount);
        }
    }

    private record ClaimLocation(ResourceLocation dimension, int x, int z) {}
}
