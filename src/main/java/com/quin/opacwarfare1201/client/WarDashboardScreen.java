package com.quin.opacwarfare1201.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public final class WarDashboardScreen extends Screen {
    private static final int PANEL_WIDTH = 440;
    private static final int TOP = 22;
    private static final int BOTTOM_MARGIN = 24;
    private static final int LINE_HEIGHT = 12;

    private final List<String> lines;
    private double scroll;

    public WarDashboardScreen(List<String> lines) {
        super(Component.literal("Warfare Dashboard"));
        this.lines = List.copyOf(lines);
    }

    @Override
    public void render(@NotNull GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);

        int width = Math.min(PANEL_WIDTH, this.width - 24);
        int left = (this.width - width) / 2;
        int right = left + width;
        int bottom = this.height - BOTTOM_MARGIN;
        int contentTop = TOP + 28;

        graphics.fill(left, TOP, right, bottom, 0xE010141C);
        graphics.fill(left, TOP, right, TOP + 24, 0xE0202B3A);
        graphics.drawCenteredString(font, "OPaC WARFARE DASHBOARD", this.width / 2, TOP + 8, 0xFFFFFF);

        graphics.enableScissor(left + 6, contentTop, right - 6, bottom - 8);

        int y = contentTop + 2 - (int)scroll;
        for (String encoded : lines) {
            if (encoded.isEmpty()) {
                y += 6;
                continue;
            }

            String text = encoded;
            int color = 0xD7DEE8;
            int x = left + 14;

            if (encoded.length() > 2 && encoded.charAt(1) == '|') {
                char type = encoded.charAt(0);
                text = encoded.substring(2);
                switch (type) {
                    case 'H' -> {
                        color = 0xFFD166;
                        x = left + 12;
                        y += 4;
                    }
                    case 'G' -> color = 0x7EE787;
                    case 'Y' -> color = 0xFFD166;
                    case 'R' -> color = 0xFF7B72;
                    case 'P' -> color = 0xD2A8FF;
                    case 'B' -> color = 0x79C0FF;
                    case 'D' -> color = 0x8B949E;
                    default -> { }
                }
            }

            graphics.drawString(font, text, x, y, color, false);
            y += LINE_HEIGHT;
        }

        graphics.disableScissor();

        int contentHeight = measuredContentHeight();
        int visibleHeight = Math.max(1, bottom - 8 - contentTop);
        if (contentHeight > visibleHeight) {
            double ratio = visibleHeight / (double)contentHeight;
            int barHeight = Math.max(18, (int)(visibleHeight * ratio));
            int track = visibleHeight - barHeight;
            int maxScroll = Math.max(1, contentHeight - visibleHeight);
            int barY = contentTop + (int)(track * (scroll / maxScroll));
            graphics.fill(right - 5, contentTop, right - 3, bottom - 8, 0x70404A58);
            graphics.fill(right - 5, barY, right - 3, barY + barHeight, 0xFFD7DEE8);
        }

        graphics.drawCenteredString(font, "Mouse wheel to scroll  •  Esc to close", this.width / 2,
                bottom + 6, 0x8B949E);
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        int visibleHeight = Math.max(1, (this.height - BOTTOM_MARGIN - 8) - (TOP + 28));
        int maxScroll = Math.max(0, measuredContentHeight() - visibleHeight);
        scroll = Math.max(0, Math.min(maxScroll, scroll - delta * 24));
        return true;
    }

    private int measuredContentHeight() {
        int height = 4;
        for (String line : lines) {
            height += line.isEmpty() ? 6 : LINE_HEIGHT;
            if (line.startsWith("H|")) height += 4;
        }
        return height;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
