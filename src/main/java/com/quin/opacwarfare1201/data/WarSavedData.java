package com.quin.opacwarfare1201.data;

import com.quin.opacwarfare1201.war.CapitalRecord;
import com.quin.opacwarfare1201.war.StrategicCity;
import com.quin.opacwarfare1201.war.WarRecord;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.saveddata.SavedData;

import javax.annotation.Nullable;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class WarSavedData extends SavedData {
    public static final String NAME = "opac_warfare_1201";
    private final Map<UUID, WarRecord> wars = new LinkedHashMap<>();
    private final Map<UUID, CapitalRecord> capitals = new LinkedHashMap<>();
    private final Map<String, StrategicCity> cities = new LinkedHashMap<>();
    private final Map<UUID, Long> attackCooldownUntilEpochMillis = new LinkedHashMap<>();
    private final Map<UUID, Long> playerAttackCooldownUntilEpochMillis = new LinkedHashMap<>();
    private final Map<UUID, Set<UUID>> partyMemberSnapshots = new LinkedHashMap<>();

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

    public Collection<StrategicCity> cities() { return cities.values(); }

    @Nullable
    public StrategicCity getCity(String id) {
        return cities.get(id.toLowerCase());
    }

    public boolean hasCity(String id) {
        return cities.containsKey(id.toLowerCase());
    }

    public void putCity(StrategicCity city) {
        cities.put(city.id.toLowerCase(), city);
        setDirty();
    }

    @Nullable
    public StrategicCity removeCity(String id) {
        StrategicCity removed = cities.remove(id.toLowerCase());
        if (removed != null) setDirty();
        return removed;
    }

    public long attackCooldownUntil(UUID partyId) {
        return attackCooldownUntilEpochMillis.getOrDefault(partyId, 0L);
    }

    public void setAttackCooldownUntil(UUID partyId, long epochMillis) {
        if (epochMillis <= 0L) attackCooldownUntilEpochMillis.remove(partyId);
        else attackCooldownUntilEpochMillis.put(partyId, epochMillis);
        setDirty();
    }

    public void clearAttackCooldown(UUID partyId) {
        if (attackCooldownUntilEpochMillis.remove(partyId) != null) setDirty();
    }

    public long playerAttackCooldownUntil(UUID playerId) {
        return playerAttackCooldownUntilEpochMillis.getOrDefault(playerId, 0L);
    }

    public void setPlayerAttackCooldownUntil(UUID playerId, long epochMillis) {
        if (epochMillis <= 0L) playerAttackCooldownUntilEpochMillis.remove(playerId);
        else playerAttackCooldownUntilEpochMillis.put(playerId, epochMillis);
        setDirty();
    }

    public void clearPlayerAttackCooldown(UUID playerId) {
        if (playerAttackCooldownUntilEpochMillis.remove(playerId) != null) setDirty();
    }

    public Map<UUID, Set<UUID>> partyMemberSnapshots() {
        return partyMemberSnapshots;
    }

    public void setPartyMemberSnapshot(UUID partyId, Collection<UUID> members) {
        Set<UUID> next = new LinkedHashSet<>(members);
        if (next.equals(partyMemberSnapshots.get(partyId))) return;
        partyMemberSnapshots.put(partyId, next);
        setDirty();
    }

    public Set<UUID> removePartyMemberSnapshot(UUID partyId) {
        Set<UUID> removed = partyMemberSnapshots.remove(partyId);
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

        ListTag cityList = new ListTag();
        for (StrategicCity city : cities.values()) cityList.add(city.save());
        tag.put("cities", cityList);

        ListTag cooldownList = new ListTag();
        for (Map.Entry<UUID, Long> entry : attackCooldownUntilEpochMillis.entrySet()) {
            CompoundTag cooldown = new CompoundTag();
            cooldown.putUUID("partyId", entry.getKey());
            cooldown.putLong("until", entry.getValue());
            cooldownList.add(cooldown);
        }
        tag.put("attackCooldowns", cooldownList);

        ListTag playerCooldownList = new ListTag();
        for (Map.Entry<UUID, Long> entry : playerAttackCooldownUntilEpochMillis.entrySet()) {
            CompoundTag cooldown = new CompoundTag();
            cooldown.putUUID("playerId", entry.getKey());
            cooldown.putLong("until", entry.getValue());
            playerCooldownList.add(cooldown);
        }
        tag.put("playerAttackCooldowns", playerCooldownList);

        ListTag partySnapshots = new ListTag();
        for (Map.Entry<UUID, Set<UUID>> entry : partyMemberSnapshots.entrySet()) {
            CompoundTag snapshot = new CompoundTag();
            snapshot.putUUID("partyId", entry.getKey());
            ListTag members = new ListTag();
            for (UUID memberId : entry.getValue()) {
                CompoundTag member = new CompoundTag();
                member.putUUID("playerId", memberId);
                members.add(member);
            }
            snapshot.put("members", members);
            partySnapshots.add(snapshot);
        }
        tag.put("partyMemberSnapshots", partySnapshots);
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

        ListTag cityList = tag.getList("cities", Tag.TAG_COMPOUND);
        for (Tag e : cityList) {
            StrategicCity city = StrategicCity.load((CompoundTag)e);
            data.cities.put(city.id.toLowerCase(), city);
        }

        ListTag cooldownList = tag.getList("attackCooldowns", Tag.TAG_COMPOUND);
        for (Tag e : cooldownList) {
            CompoundTag cooldown = (CompoundTag)e;
            if (!cooldown.hasUUID("partyId")) continue;
            long until = cooldown.getLong("until");
            if (until > 0L) data.attackCooldownUntilEpochMillis.put(cooldown.getUUID("partyId"), until);
        }

        ListTag playerCooldownList = tag.getList("playerAttackCooldowns", Tag.TAG_COMPOUND);
        for (Tag e : playerCooldownList) {
            CompoundTag cooldown = (CompoundTag)e;
            if (!cooldown.hasUUID("playerId")) continue;
            long until = cooldown.getLong("until");
            if (until > 0L) data.playerAttackCooldownUntilEpochMillis.put(cooldown.getUUID("playerId"), until);
        }

        ListTag partySnapshots = tag.getList("partyMemberSnapshots", Tag.TAG_COMPOUND);
        for (Tag e : partySnapshots) {
            CompoundTag snapshot = (CompoundTag)e;
            if (!snapshot.hasUUID("partyId")) continue;
            Set<UUID> members = new LinkedHashSet<>();
            ListTag memberList = snapshot.getList("members", Tag.TAG_COMPOUND);
            for (Tag memberTag : memberList) {
                CompoundTag member = (CompoundTag)memberTag;
                if (member.hasUUID("playerId")) members.add(member.getUUID("playerId"));
            }
            data.partyMemberSnapshots.put(snapshot.getUUID("partyId"), members);
        }

        return data;
    }
}
