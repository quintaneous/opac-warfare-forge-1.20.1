package com.quin.opacwarfare1201.data;

import com.quin.opacwarfare1201.war.CapitalRecord;
import com.quin.opacwarfare1201.war.WarRecord;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.saveddata.SavedData;

import javax.annotation.Nullable;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

public final class WarSavedData extends SavedData {
    public static final String NAME = "opac_warfare_1201";
    private final Map<UUID, WarRecord> wars = new LinkedHashMap<>();
    private final Map<UUID, CapitalRecord> capitals = new LinkedHashMap<>();

    public Collection<WarRecord> wars() { return wars.values(); }
    public WarRecord get(UUID id) { return wars.get(id); }
    public void put(WarRecord war) { wars.put(war.id, war); setDirty(); }
    public void remove(UUID id) { wars.remove(id); setDirty(); }

    public Collection<CapitalRecord> capitals() { return capitals.values(); }

    @Nullable
    public CapitalRecord getCapital(UUID partyId) {
        return capitals.get(partyId);
    }

    public void putCapital(CapitalRecord capital) {
        capitals.put(capital.partyId, capital);
        setDirty();
    }

    @Nullable
    public CapitalRecord removeCapital(UUID partyId) {
        CapitalRecord removed = capitals.remove(partyId);
        if (removed != null) setDirty();
        return removed;
    }

    public void changed() { setDirty(); }

    @Override
    public CompoundTag save(CompoundTag tag) {
        ListTag warList = new ListTag();
        for (WarRecord war : wars.values()) warList.add(war.save());
        tag.put("wars", warList);

        ListTag capitalList = new ListTag();
        for (CapitalRecord capital : capitals.values()) capitalList.add(capital.save());
        tag.put("capitals", capitalList);
        return tag;
    }

    public static WarSavedData load(CompoundTag tag) {
        WarSavedData data = new WarSavedData();

        ListTag warList = tag.getList("wars", Tag.TAG_COMPOUND);
        for (Tag e : warList) {
            WarRecord war = WarRecord.load((CompoundTag)e);
            data.wars.put(war.id, war);
        }

        ListTag capitalList = tag.getList("capitals", Tag.TAG_COMPOUND);
        for (Tag e : capitalList) {
            CapitalRecord capital = CapitalRecord.load((CompoundTag)e);
            data.capitals.put(capital.partyId, capital);
        }

        return data;
    }
}
