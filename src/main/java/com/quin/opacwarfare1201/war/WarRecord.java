package com.quin.opacwarfare1201.war;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class WarRecord {
    public final UUID id;
    public ResourceLocation dimension;
    public int chunkX;
    public int chunkZ;
    public UUID attackerPartyId;
    public UUID attackerOwnerId;
    @Nullable public UUID defenderPartyId;
    @Nullable public UUID defenderOwnerId;
    @Nullable public UUID originalClaimOwner;
    public int originalSubConfig;
    public boolean originalForceload;
    @Nullable public String cityId;
    public WarPhase phase;
    public long activateAtGameTime;
    public long onlineGraceDeadlineGameTime;
    public long activeEndsAtGameTime;
    public double progress;
    public boolean capturePointSet;
    public int captureX;
    public int captureY;
    public int captureZ;

    /**
     * Rosters are frozen at declaration time. Joining/leaving a party during
     * the battle does not add/remove participants from this specific war.
     */
    public final Set<UUID> attackerRoster = new LinkedHashSet<>();
    public final Set<UUID> defenderRoster = new LinkedHashSet<>();

    public final Map<UUID, Integer> lives = new HashMap<>();

    /**
     * Defender Create/CBC blocks placed during ACTIVE, keyed by block position
     * and the defender who placed them. These can be repositioned by that same
     * player without opening pre-war fortifications to repair cheese.
     */
    public final Map<Long, UUID> siegePlacements = new HashMap<>();

    public WarRecord(UUID id) {
        this.id = id;
    }

    public boolean targets(ResourceLocation dim, int x, int z) {
        return dimension.equals(dim) && chunkX == x && chunkZ == z;
    }

    public boolean isCityWar() {
        return cityId != null && !cityId.isBlank();
    }

    public boolean isAttacker(UUID playerId) {
        return attackerRoster.contains(playerId);
    }

    public boolean isDefender(UUID playerId) {
        return defenderRoster.contains(playerId);
    }

    public boolean isParticipant(UUID playerId) {
        return isAttacker(playerId) || isDefender(playerId);
    }

    public CompoundTag save() {
        CompoundTag t = new CompoundTag();
        t.putUUID("id", id);
        t.putString("dimension", dimension.toString());
        t.putInt("x", chunkX);
        t.putInt("z", chunkZ);
        t.putUUID("attackerParty", attackerPartyId);
        t.putUUID("attackerOwner", attackerOwnerId);
        if (defenderPartyId != null) t.putUUID("defenderParty", defenderPartyId);
        if (defenderOwnerId != null) t.putUUID("defenderOwner", defenderOwnerId);
        if (originalClaimOwner != null) t.putUUID("originalOwner", originalClaimOwner);
        if (cityId != null) t.putString("cityId", cityId);
        t.putInt("originalSub", originalSubConfig);
        t.putBoolean("originalForceload", originalForceload);
        t.putString("phase", phase.name());
        t.putLong("activateAt", activateAtGameTime);
        t.putLong("onlineGraceDeadline", onlineGraceDeadlineGameTime);
        t.putLong("activeEndsAt", activeEndsAtGameTime);
        t.putDouble("progress", progress);
        t.putBoolean("capturePointSet", capturePointSet);
        if (capturePointSet) {
            t.putInt("captureX", captureX);
            t.putInt("captureY", captureY);
            t.putInt("captureZ", captureZ);
        }

        t.put("attackerRoster", saveUuidSet(attackerRoster));
        t.put("defenderRoster", saveUuidSet(defenderRoster));

        CompoundTag lt = new CompoundTag();
        lives.forEach((uuid, n) -> lt.putInt(uuid.toString(), n));
        t.put("lives", lt);

        ListTag placements = new ListTag();
        for (Map.Entry<Long, UUID> entry : siegePlacements.entrySet()) {
            CompoundTag placement = new CompoundTag();
            placement.putLong("pos", entry.getKey());
            placement.putUUID("playerId", entry.getValue());
            placements.add(placement);
        }
        t.put("siegePlacements", placements);
        return t;
    }

    public static WarRecord load(CompoundTag t) {
        WarRecord w = new WarRecord(t.getUUID("id"));
        w.dimension = new ResourceLocation(t.getString("dimension"));
        w.chunkX = t.getInt("x");
        w.chunkZ = t.getInt("z");
        w.attackerPartyId = t.getUUID("attackerParty");
        w.attackerOwnerId = t.getUUID("attackerOwner");
        w.defenderPartyId = t.hasUUID("defenderParty") ? t.getUUID("defenderParty") : null;
        w.defenderOwnerId = t.hasUUID("defenderOwner") ? t.getUUID("defenderOwner") : null;
        w.originalClaimOwner = t.hasUUID("originalOwner") ? t.getUUID("originalOwner") : null;
        w.cityId = t.contains("cityId") ? t.getString("cityId") : null;
        w.originalSubConfig = t.getInt("originalSub");
        w.originalForceload = t.getBoolean("originalForceload");
        try { w.phase = WarPhase.valueOf(t.getString("phase")); }
        catch (Exception e) { w.phase = WarPhase.PREPARING; }
        w.activateAtGameTime = t.getLong("activateAt");
        w.onlineGraceDeadlineGameTime = t.getLong("onlineGraceDeadline");
        w.activeEndsAtGameTime = t.getLong("activeEndsAt");
        w.progress = t.getDouble("progress");
        w.capturePointSet = t.getBoolean("capturePointSet");
        if (w.capturePointSet) {
            w.captureX = t.getInt("captureX");
            w.captureY = t.getInt("captureY");
            w.captureZ = t.getInt("captureZ");
        }

        loadUuidSet(t.getList("attackerRoster", Tag.TAG_COMPOUND), w.attackerRoster);
        loadUuidSet(t.getList("defenderRoster", Tag.TAG_COMPOUND), w.defenderRoster);

        CompoundTag lt = t.getCompound("lives");
        for (String k : lt.getAllKeys()) {
            try { w.lives.put(UUID.fromString(k), lt.getInt(k)); }
            catch (IllegalArgumentException ignored) {}
        }

        ListTag placements = t.getList("siegePlacements", Tag.TAG_COMPOUND);
        for (Tag raw : placements) {
            CompoundTag placement = (CompoundTag)raw;
            if (!placement.hasUUID("playerId")) continue;
            w.siegePlacements.put(placement.getLong("pos"), placement.getUUID("playerId"));
        }
        return w;
    }

    private static ListTag saveUuidSet(Set<UUID> values) {
        ListTag list = new ListTag();
        for (UUID value : values) {
            CompoundTag entry = new CompoundTag();
            entry.putUUID("id", value);
            list.add(entry);
        }
        return list;
    }

    private static void loadUuidSet(ListTag list, Set<UUID> output) {
        for (Tag raw : list) {
            CompoundTag entry = (CompoundTag)raw;
            if (entry.hasUUID("id")) output.add(entry.getUUID("id"));
        }
    }
}
