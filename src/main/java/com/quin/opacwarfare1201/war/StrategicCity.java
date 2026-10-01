package com.quin.opacwarfare1201.war;

import com.quin.opacwarfare1201.data.CompressedBlockSet;
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
    public final CompressedBlockSet protectedBlocks = new CompressedBlockSet();
    /**
     * Blocks added after the city was created. These are siege fortifications:
     * budget-limited in peacetime and destructible during an ACTIVE city siege.
     */
    public final Set<Long> fortificationBlocks = new HashSet<>();
    /**
     * False only for cities saved before fortification accounting existed.
     * The first server load performs a one-time scan to migrate those cities.
     */
    public boolean fortificationTrackingInitialized;

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
        return protectedBlocks.contains(pos);
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

        t.put("protectedSections", protectedBlocks.save());

        long[] fortificationArray = new long[fortificationBlocks.size()];
        int i = 0;
        for (long pos : fortificationBlocks) fortificationArray[i++] = pos;
        t.putLongArray("fortificationBlocks", fortificationArray);
        t.putBoolean("fortificationTrackingInitialized", fortificationTrackingInitialized);
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
        if (t.contains("protectedSections", net.minecraft.nbt.Tag.TAG_LIST)) {
            city.protectedBlocks.load(t.getList("protectedSections", net.minecraft.nbt.Tag.TAG_COMPOUND));
        } else {
            // 0.3.x migration path.
            city.protectedBlocks.loadLegacy(t.getLongArray("protectedBlocks"));
        }
        for (long pos : t.getLongArray("fortificationBlocks")) city.fortificationBlocks.add(pos);
        city.fortificationTrackingInitialized = t.getBoolean("fortificationTrackingInitialized");
        return city;
    }
}
