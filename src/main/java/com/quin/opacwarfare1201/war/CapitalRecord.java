package com.quin.opacwarfare1201.war;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;

import java.util.UUID;

public final class CapitalRecord {
    public final UUID partyId;
    public UUID ownerId;
    public ResourceLocation dimension;
    public int chunkX;
    public int chunkZ;

    public CapitalRecord(UUID partyId) {
        this.partyId = partyId;
    }

    public boolean targets(ResourceLocation dim, int x, int z) {
        return dimension.equals(dim) && chunkX == x && chunkZ == z;
    }

    public CompoundTag save() {
        CompoundTag t = new CompoundTag();
        t.putUUID("partyId", partyId);
        t.putUUID("ownerId", ownerId);
        t.putString("dimension", dimension.toString());
        t.putInt("x", chunkX);
        t.putInt("z", chunkZ);
        return t;
    }

    public static CapitalRecord load(CompoundTag t) {
        CapitalRecord c = new CapitalRecord(t.getUUID("partyId"));
        c.ownerId = t.getUUID("ownerId");
        c.dimension = new ResourceLocation(t.getString("dimension"));
        c.chunkX = t.getInt("x");
        c.chunkZ = t.getInt("z");
        return c;
    }
}
