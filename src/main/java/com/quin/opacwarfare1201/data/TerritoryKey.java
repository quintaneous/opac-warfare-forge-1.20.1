package com.quin.opacwarfare1201.data;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;

public record TerritoryKey(ResourceLocation dimension, int chunkX, int chunkZ) {
    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putString("dimension", dimension.toString());
        tag.putInt("x", chunkX);
        tag.putInt("z", chunkZ);
        return tag;
    }

    public static TerritoryKey load(CompoundTag tag) {
        return new TerritoryKey(
                new ResourceLocation(tag.getString("dimension")),
                tag.getInt("x"),
                tag.getInt("z"));
    }
}
