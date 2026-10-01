package com.quin.opacwarfare1201.data;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

import java.util.BitSet;
import java.util.HashMap;
import java.util.Map;

/**
 * Compact exact block-position set backed by 16x16x16 section bitsets.
 * A populated section costs 4096 bits instead of storing one long per block.
 */
public final class CompressedBlockSet {
    private final Map<SectionKey, BitSet> sections = new HashMap<>();
    private int size;

    public boolean contains(BlockPos pos) {
        return contains(pos.asLong());
    }

    public boolean contains(long packedPos) {
        BlockPos pos = BlockPos.of(packedPos);
        SectionKey key = key(pos);
        BitSet bits = sections.get(key);
        return bits != null && bits.get(index(pos));
    }

    public boolean add(BlockPos pos) {
        return add(pos.asLong());
    }

    public boolean add(long packedPos) {
        BlockPos pos = BlockPos.of(packedPos);
        SectionKey key = key(pos);
        BitSet bits = sections.computeIfAbsent(key, ignored -> new BitSet(4096));
        int index = index(pos);
        if (bits.get(index)) return false;
        bits.set(index);
        size++;
        return true;
    }

    public boolean remove(BlockPos pos) {
        return remove(pos.asLong());
    }

    public boolean remove(long packedPos) {
        BlockPos pos = BlockPos.of(packedPos);
        SectionKey key = key(pos);
        BitSet bits = sections.get(key);
        if (bits == null) return false;

        int index = index(pos);
        if (!bits.get(index)) return false;

        bits.clear(index);
        size--;
        if (bits.isEmpty()) sections.remove(key);
        return true;
    }

    public void clear() {
        sections.clear();
        size = 0;
    }

    public int size() {
        return size;
    }

    public int sectionCount() {
        return sections.size();
    }

    public ListTag save() {
        ListTag list = new ListTag();
        for (Map.Entry<SectionKey, BitSet> entry : sections.entrySet()) {
            CompoundTag section = new CompoundTag();
            section.putInt("x", entry.getKey().x);
            section.putInt("y", entry.getKey().y);
            section.putInt("z", entry.getKey().z);
            section.putLongArray("bits", entry.getValue().toLongArray());
            list.add(section);
        }
        return list;
    }

    public void load(ListTag list) {
        clear();
        for (Tag raw : list) {
            CompoundTag section = (CompoundTag)raw;
            BitSet bits = BitSet.valueOf(section.getLongArray("bits"));
            if (bits.isEmpty()) continue;

            SectionKey key = new SectionKey(
                    section.getInt("x"),
                    section.getInt("y"),
                    section.getInt("z"));
            sections.put(key, bits);
            size += bits.cardinality();
        }
    }

    public void loadLegacy(long[] positions) {
        clear();
        for (long packed : positions) add(packed);
    }

    private static SectionKey key(BlockPos pos) {
        return new SectionKey(pos.getX() >> 4, pos.getY() >> 4, pos.getZ() >> 4);
    }

    private static int index(BlockPos pos) {
        return ((pos.getY() & 15) << 8) | ((pos.getZ() & 15) << 4) | (pos.getX() & 15);
    }

    private record SectionKey(int x, int y, int z) {}
}
