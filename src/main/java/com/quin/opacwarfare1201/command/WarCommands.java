package com.quin.opacwarfare1201.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.quin.opacwarfare1201.war.WarManager;
import com.quin.opacwarfare1201.war.WarRecord;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import xaero.pac.common.server.api.OpenPACServerAPI;
import xaero.pac.common.server.parties.party.api.IServerPartyAPI;

import java.util.UUID;

public final class WarCommands {
    private WarCommands() {}

    public static void register(CommandDispatcher<CommandSourceStack> d) {
        d.register(Commands.literal("war")
                .then(Commands.literal("start").executes(ctx -> start(ctx.getSource())))
                .then(Commands.literal("status").executes(ctx -> status(ctx.getSource())))
                .then(Commands.literal("surrender").executes(ctx -> surrender(ctx.getSource())))
                .then(Commands.literal("admin")
                        .requires(src -> src.hasPermission(2))
                        .then(Commands.literal("stop")
                                .then(Commands.argument("warId", StringArgumentType.word())
                                        .executes(ctx -> adminStop(ctx.getSource(), StringArgumentType.getString(ctx, "warId")))))
                        .then(Commands.literal("list").executes(ctx -> list(ctx.getSource()))))
        );
    }

    private static int start(CommandSourceStack src) {
        try {
            ServerPlayer player = src.getPlayerOrException();
            IServerPartyAPI party = OpenPACServerAPI.get(src.getServer()).getPartyManager().getPartyByMember(player.getUUID());
            if (party == null) {
                src.sendFailure(Component.literal("You must be in an OPaC party."));
                return 0;
            }
            if (!party.getOwner().getUUID().equals(player.getUUID())) {
                src.sendFailure(Component.literal("Only the party owner can start a war in this beta."));
                return 0;
            }
            ChunkPos target = player.chunkPosition();
            WarManager.StartResult result = WarManager.get(src.getServer()).startWar(player, target);
            if (!result.success()) {
                src.sendFailure(Component.literal(result.message()));
                return 0;
            }
            src.sendSuccess(() -> Component.literal(result.message() + " ID=" + result.war().id).withStyle(ChatFormatting.GREEN), false);
            return 1;
        } catch (Exception e) {
            src.sendFailure(Component.literal("War start failed: " + e.getMessage()));
            return 0;
        }
    }

    private static int status(CommandSourceStack src) {
        WarManager m = WarManager.get(src.getServer());
        if (m.wars().isEmpty()) {
            src.sendSuccess(() -> Component.literal("No active/preparing chunk wars."), false);
            return 1;
        }
        for (WarRecord w : m.wars()) {
            int pct = (int)Math.round(w.progress * 100D);
            src.sendSuccess(() -> Component.literal(w.id + " | " + w.phase + " | " + w.dimension + " [" + w.chunkX + "," + w.chunkZ + "] | " + pct + "%"), false);
        }
        return 1;
    }

    private static int surrender(CommandSourceStack src) {
        try {
            ServerPlayer p = src.getPlayerOrException();
            IServerPartyAPI party = OpenPACServerAPI.get(src.getServer()).getPartyManager().getPartyByMember(p.getUUID());
            if (party != null && !party.getOwner().getUUID().equals(p.getUUID())) {
                src.sendFailure(Component.literal("Only the party owner can surrender in this beta."));
                return 0;
            }
            WarManager m = WarManager.get(src.getServer());
            for (WarRecord w : m.wars()) {
                if (m.isParticipant(w, p.getUUID(), false)) {
                    m.surrender(p, w);
                    return 1;
                }
            }
            src.sendFailure(Component.literal("Your side is not in a chunk war."));
            return 0;
        } catch (Exception e) {
            src.sendFailure(Component.literal("Surrender failed: " + e.getMessage()));
            return 0;
        }
    }

    private static int adminStop(CommandSourceStack src, String raw) {
        try {
            UUID id = UUID.fromString(raw);
            WarRecord w = WarManager.get(src.getServer()).wars().stream().filter(x -> x.id.equals(id)).findFirst().orElse(null);
            if (w == null) {
                src.sendFailure(Component.literal("Unknown war ID."));
                return 0;
            }
            WarManager.get(src.getServer()).adminStop(w, true);
            return 1;
        } catch (IllegalArgumentException e) {
            src.sendFailure(Component.literal("Invalid UUID."));
            return 0;
        }
    }

    private static int list(CommandSourceStack src) { return status(src); }
}
