package com.quin.opacwarfare1201.war;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.ChunkPos;

import javax.annotation.Nullable;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public final class StrategicCity {
    public final String id;
    public ResourceLocation dimension;
    public int minChunkX;
    public int minChunkZ;
    public int maxChunkX;
    public int maxChunkZ;
    public int captureChunkX;
    public int captureChunkZ;
    public int captureY;
    @Nullable public UUID controllerPartyId;
    @Nullable public UUID controllerOwnerId;
    public final Set<Long> protectedBlocks = new HashSet<>();

    public StrategicCity(String id) {
        this.id = id;
    }

    public boolean containsChunk(ResourceLocation dim, int x, int z) {
        return dimension.equals(dim)
                && x >= minChunkX && x <= maxChunkX
                && z >= minChunkZ && z <= maxChunkZ;
    }

    public boolean containsBlock(ResourceLocation dim, BlockPos pos) {
        ChunkPos cp = new ChunkPos(pos);
        return containsChunk(dim, cp.x, cp.z);
    }

    public boolean isProtected(BlockPos pos) {
        return protectedBlocks.contains(pos.asLong());
    }

    public boolean isControlledBy(@Nullable UUID partyId) {
        return partyId != null && partyId.equals(controllerPartyId);
    }

    public CompoundTag save() {
        CompoundTag t = new CompoundTag();
        t.putString("id", id);
        t.putString("dimension", dimension.toString());
        t.putInt("minChunkX", minChunkX);
        t.putInt("minChunkZ", minChunkZ);
        t.putInt("maxChunkX", maxChunkX);
        t.putInt("maxChunkZ", maxChunkZ);
        t.putInt("captureChunkX", captureChunkX);
        t.putInt("captureChunkZ", captureChunkZ);
        t.putInt("captureY", captureY);
        if (controllerPartyId != null) t.putUUID("controllerPartyId", controllerPartyId);
        if (controllerOwnerId != null) t.putUUID("controllerOwnerId", controllerOwnerId);

        long[] protectedArray = new long[protectedBlocks.size()];
        int i = 0;
        for (long pos : protectedBlocks) protectedArray[i++] = pos;
        t.putLongArray("protectedBlocks", protectedArray);
        return t;
    }

    public static StrategicCity load(CompoundTag t) {
        StrategicCity city = new StrategicCity(t.getString("id"));
        city.dimension = new ResourceLocation(t.getString("dimension"));
        city.minChunkX = t.getInt("minChunkX");
        city.minChunkZ = t.getInt("minChunkZ");
        city.maxChunkX = t.getInt("maxChunkX");
        city.maxChunkZ = t.getInt("maxChunkZ");
        city.captureChunkX = t.getInt("captureChunkX");
        city.captureChunkZ = t.getInt("captureChunkZ");
        city.captureY = t.getInt("captureY");
        city.controllerPartyId = t.hasUUID("controllerPartyId") ? t.getUUID("controllerPartyId") : null;
        city.controllerOwnerId = t.hasUUID("controllerOwnerId") ? t.getUUID("controllerOwnerId") : null;
        for (long pos : t.getLongArray("protectedBlocks")) city.protectedBlocks.add(pos);
        return city;
    }
}
