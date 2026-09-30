package com.quin.opacwarfare1201.war;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class WarRecord {
    public final UUID id;
    public ResourceLocation dimension;
    public int chunkX;
    public int chunkZ;
    public UUID attackerPartyId;
    public UUID attackerOwnerId;
    public UUID defenderPartyId;
    public UUID defenderOwnerId;
    public UUID originalClaimOwner;
    public int originalSubConfig;
    public boolean originalForceload;
    public WarPhase phase;
    public long activateAtGameTime;
    public double progress;
    public boolean capturePointSet;
    public int captureX;
    public int captureY;
    public int captureZ;
    public final Map<UUID, Integer> lives = new HashMap<>();

    public WarRecord(UUID id) {
        this.id = id;
    }

    public boolean targets(ResourceLocation dim, int x, int z) {
        return dimension.equals(dim) && chunkX == x && chunkZ == z;
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
        t.putUUID("defenderOwner", defenderOwnerId);
        t.putUUID("originalOwner", originalClaimOwner);
        t.putInt("originalSub", originalSubConfig);
        t.putBoolean("originalForceload", originalForceload);
        t.putString("phase", phase.name());
        t.putLong("activateAt", activateAtGameTime);
        t.putDouble("progress", progress);
        t.putBoolean("capturePointSet", capturePointSet);
        if (capturePointSet) {
            t.putInt("captureX", captureX);
            t.putInt("captureY", captureY);
            t.putInt("captureZ", captureZ);
        }
        CompoundTag lt = new CompoundTag();
        lives.forEach((uuid, n) -> lt.putInt(uuid.toString(), n));
        t.put("lives", lt);
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
        w.defenderOwnerId = t.getUUID("defenderOwner");
        w.originalClaimOwner = t.getUUID("originalOwner");
        w.originalSubConfig = t.getInt("originalSub");
        w.originalForceload = t.getBoolean("originalForceload");
        try { w.phase = WarPhase.valueOf(t.getString("phase")); } catch (Exception e) { w.phase = WarPhase.PREPARING; }
        w.activateAtGameTime = t.getLong("activateAt");
        w.progress = t.getDouble("progress");
        w.capturePointSet = t.getBoolean("capturePointSet");
        if (w.capturePointSet) {
            w.captureX = t.getInt("captureX");
            w.captureY = t.getInt("captureY");
            w.captureZ = t.getInt("captureZ");
        }
        CompoundTag lt = t.getCompound("lives");
        for (String k : lt.getAllKeys()) {
            try { w.lives.put(UUID.fromString(k), lt.getInt(k)); } catch (IllegalArgumentException ignored) {}
        }
        return w;
    }
}
