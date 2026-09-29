package com.quin.opacwarfare1201.data;

import com.quin.opacwarfare1201.war.WarRecord;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

public final class WarSavedData extends SavedData {
    public static final String NAME = "opac_warfare_1201";
    private final Map<UUID, WarRecord> wars = new LinkedHashMap<>();

    public Collection<WarRecord> wars() { return wars.values(); }
    public WarRecord get(UUID id) { return wars.get(id); }
    public void put(WarRecord war) { wars.put(war.id, war); setDirty(); }
    public void remove(UUID id) { wars.remove(id); setDirty(); }
    public void changed() { setDirty(); }

    @Override
    public CompoundTag save(CompoundTag tag) {
        ListTag list = new ListTag();
        for (WarRecord war : wars.values()) list.add(war.save());
        tag.put("wars", list);
        return tag;
    }

    public static WarSavedData load(CompoundTag tag) {
        WarSavedData data = new WarSavedData();
        ListTag list = tag.getList("wars", Tag.TAG_COMPOUND);
        for (Tag e : list) {
            WarRecord war = WarRecord.load((CompoundTag)e);
            data.wars.put(war.id, war);
        }
        return data;
    }
}
