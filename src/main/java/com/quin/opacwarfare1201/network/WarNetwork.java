package com.quin.opacwarfare1201.network;

import com.quin.opacwarfare1201.OpacWarfare1201;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

import java.util.List;

public final class WarNetwork {
    private static final String PROTOCOL = "1";
    public static final SimpleChannel CHANNEL = NetworkRegistry.ChannelBuilder
            .named(new ResourceLocation(OpacWarfare1201.MODID, "main"))
            .networkProtocolVersion(() -> PROTOCOL)
            .clientAcceptedVersions(PROTOCOL::equals)
            .serverAcceptedVersions(PROTOCOL::equals)
            .simpleChannel();

    private static int nextId;

    private WarNetwork() {}

    public static void register() {
        CHANNEL.registerMessage(nextId++, WarDashboardPacket.class,
                WarDashboardPacket::encode,
                WarDashboardPacket::decode,
                WarDashboardPacket::handle);
    }

    public static void openDashboard(ServerPlayer player, List<String> lines) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new WarDashboardPacket(lines));
    }
}
