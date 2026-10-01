package com.quin.opacwarfare1201.client;

import net.minecraft.client.Minecraft;

import java.util.List;

public final class ClientDashboardHandler {
    private ClientDashboardHandler() {}

    public static void open(List<String> lines) {
        Minecraft.getInstance().setScreen(new WarDashboardScreen(lines));
    }
}
