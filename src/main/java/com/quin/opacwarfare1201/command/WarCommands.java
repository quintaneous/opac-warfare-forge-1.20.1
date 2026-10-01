package com.quin.opacwarfare1201.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.quin.opacwarfare1201.opac.OpacSides;
import com.quin.opacwarfare1201.war.CapitalRecord;
import com.quin.opacwarfare1201.war.StrategicCity;
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
                .then(Commands.literal("cooldown").executes(ctx -> cooldown(ctx.getSource())))
                .then(Commands.literal("surrender").executes(ctx -> surrender(ctx.getSource())))
                .then(Commands.literal("capital")
                        .then(Commands.literal("set").executes(ctx -> setCapital(ctx.getSource())))
                        .then(Commands.literal("status").executes(ctx -> capitalStatus(ctx.getSource()))))
                .then(Commands.literal("capitals").executes(ctx -> capitals(ctx.getSource())))
                .then(Commands.literal("city")
                        .then(Commands.literal("attack")
                                .then(Commands.argument("cityId", StringArgumentType.word())
                                        .executes(ctx -> attackCity(ctx.getSource(), StringArgumentType.getString(ctx, "cityId")))))
                        .then(Commands.literal("list").executes(ctx -> cityList(ctx.getSource())))
                        .then(Commands.literal("info")
                                .then(Commands.argument("cityId", StringArgumentType.word())
                                        .executes(ctx -> cityInfo(ctx.getSource(), StringArgumentType.getString(ctx, "cityId")))))
                        .then(Commands.literal("create")
                                .requires(src -> src.hasPermission(2))
                                .then(Commands.argument("cityId", StringArgumentType.word())
                                        .then(Commands.argument("radiusChunks", IntegerArgumentType.integer(0, 4))
                                                .executes(ctx -> createCity(
                                                        ctx.getSource(),
                                                        StringArgumentType.getString(ctx, "cityId"),
                                                        IntegerArgumentType.getInteger(ctx, "radiusChunks"))))))
                        .then(Commands.literal("delete")
                                .requires(src -> src.hasPermission(2))
                                .then(Commands.argument("cityId", StringArgumentType.word())
                                        .executes(ctx -> deleteCity(ctx.getSource(), StringArgumentType.getString(ctx, "cityId")))))
                        .then(Commands.literal("setcontroller")
                                .requires(src -> src.hasPermission(2))
                                .then(Commands.argument("cityId", StringArgumentType.word())
                                        .then(Commands.argument("partyIdOrNeutral", StringArgumentType.word())
                                                .executes(ctx -> setCityController(
                                                        ctx.getSource(),
                                                        StringArgumentType.getString(ctx, "cityId"),
                                                        StringArgumentType.getString(ctx, "partyIdOrNeutral")))))))
                .then(Commands.literal("admin")
                        .requires(src -> src.hasPermission(2))
                        .then(Commands.literal("stop")
                                .then(Commands.argument("warId", StringArgumentType.word())
                                        .executes(ctx -> adminStop(ctx.getSource(), StringArgumentType.getString(ctx, "warId")))))
                        .then(Commands.literal("clearcapital")
                                .then(Commands.argument("partyId", StringArgumentType.word())
                                        .executes(ctx -> clearCapital(ctx.getSource(), StringArgumentType.getString(ctx, "partyId")))))
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

            WarRecord war = result.war();
            String objective = war.capturePointSet
                    ? " Objective=(" + war.captureX + ", " + war.captureY + ", " + war.captureZ + "), full chunk, +/-10 Y."
                    : "";
            src.sendSuccess(() -> Component.literal(result.message() + objective + " ID=" + war.id)
                    .withStyle(ChatFormatting.GREEN), false);
            return 1;
        } catch (Exception e) {
            src.sendFailure(Component.literal("War start failed: " + e.getMessage()));
            return 0;
        }
    }

    private static int attackCity(CommandSourceStack src, String cityId) {
        try {
            ServerPlayer player = src.getPlayerOrException();
            IServerPartyAPI party = OpenPACServerAPI.get(src.getServer()).getPartyManager().getPartyByMember(player.getUUID());
            if (party == null) {
                src.sendFailure(Component.literal("You must be in an OPaC party."));
                return 0;
            }
            if (!party.getOwner().getUUID().equals(player.getUUID())) {
                src.sendFailure(Component.literal("Only the party owner can start a city war in this beta."));
                return 0;
            }

            WarManager.StartResult result = WarManager.get(src.getServer()).startCityWar(player, cityId);
            if (!result.success()) {
                src.sendFailure(Component.literal(result.message()));
                return 0;
            }

            WarRecord war = result.war();
            src.sendSuccess(() -> Component.literal("City war preparation started for " + war.cityId
                    + ". Objective=(" + war.captureX + ", " + war.captureY + ", " + war.captureZ
                    + "), full capture chunk +/-10 Y. ID=" + war.id)
                    .withStyle(ChatFormatting.LIGHT_PURPLE), false);
            return 1;
        } catch (Exception e) {
            src.sendFailure(Component.literal("City war start failed: " + e.getMessage()));
            return 0;
        }
    }

    private static int status(CommandSourceStack src) {
        WarManager m = WarManager.get(src.getServer());
        if (m.wars().isEmpty()) {
            src.sendSuccess(() -> Component.literal("No active/preparing wars."), false);
            return 1;
        }

        for (WarRecord w : m.wars()) {
            int pct = (int)Math.round(w.progress * 100D);
            WarManager.CaptureCounts counts = m.countCaptureZone(w);
            String objective = w.capturePointSet
                    ? " | objective=(" + w.captureX + "," + w.captureY + "," + w.captureZ + "), full chunk +/-10Y"
                    : "";
            String target = w.isCityWar() ? " | CITY:" + w.cityId : "";
            String line = m.attackerName(w) + " -> " + m.defenderName(w)
                    + target
                    + " | " + w.phase
                    + " | " + w.dimension + " [" + w.chunkX + "," + w.chunkZ + "]"
                    + " | " + pct + "% attacker control"
                    + " | zone A:" + counts.attackers() + " D:" + counts.defenders()
                    + objective
                    + " | " + w.id;
            src.sendSuccess(() -> Component.literal(line), false);
        }
        return 1;
    }

    private static int cooldown(CommandSourceStack src) {
        try {
            ServerPlayer player = src.getPlayerOrException();
            IServerPartyAPI party = OpenPACServerAPI.get(src.getServer()).getPartyManager().getPartyByMember(player.getUUID());
            if (party == null) {
                src.sendFailure(Component.literal("You are not in an OPaC party."));
                return 0;
            }

            long remaining = WarManager.get(src.getServer()).remainingAttackCooldownSeconds(party.getId());
            if (remaining <= 0L) {
                src.sendSuccess(() -> Component.literal("Your nation has no offensive-war cooldown.")
                        .withStyle(ChatFormatting.GREEN), false);
                return 1;
            }

            src.sendSuccess(() -> Component.literal("Offensive-war cooldown remaining: "
                    + WarManager.formatCooldown(remaining) + ".")
                    .withStyle(ChatFormatting.YELLOW), false);
            return 1;
        } catch (Exception e) {
            src.sendFailure(Component.literal("Cooldown check failed: " + e.getMessage()));
            return 0;
        }
    }

    private static int setCapital(CommandSourceStack src) {
        try {
            ServerPlayer player = src.getPlayerOrException();
            WarManager.CapitalResult result = WarManager.get(src.getServer()).setCapital(player);
            if (!result.success()) {
                src.sendFailure(Component.literal(result.message()));
                return 0;
            }

            CapitalRecord c = result.capital();
            src.sendSuccess(() -> Component.literal("Capital established at " + c.dimension
                    + " [" + c.chunkX + ", " + c.chunkZ + "].")
                    .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD), false);
            return 1;
        } catch (Exception e) {
            src.sendFailure(Component.literal("Capital setup failed: " + e.getMessage()));
            return 0;
        }
    }

    private static int capitalStatus(CommandSourceStack src) {
        try {
            ServerPlayer player = src.getPlayerOrException();
            IServerPartyAPI party = OpenPACServerAPI.get(src.getServer()).getPartyManager().getPartyByMember(player.getUUID());
            if (party == null) {
                src.sendFailure(Component.literal("You are not in an OPaC party."));
                return 0;
            }

            CapitalRecord c = WarManager.get(src.getServer()).capital(party.getId());
            if (c == null) {
                src.sendFailure(Component.literal("Your nation has no capital. The party owner can use /war capital set."));
                return 0;
            }

            src.sendSuccess(() -> Component.literal("Capital: " + party.getDefaultName() + " | "
                    + c.dimension + " [" + c.chunkX + ", " + c.chunkZ + "]")
                    .withStyle(ChatFormatting.GOLD), false);
            return 1;
        } catch (Exception e) {
            src.sendFailure(Component.literal("Capital status failed: " + e.getMessage()));
            return 0;
        }
    }

    private static int capitals(CommandSourceStack src) {
        WarManager m = WarManager.get(src.getServer());
        if (m.capitals().isEmpty()) {
            src.sendSuccess(() -> Component.literal("No capitals have been established."), false);
            return 1;
        }

        for (CapitalRecord c : m.capitals()) {
            String name = OpacSides.sideName(src.getServer(), c.partyId, c.ownerId);
            String line = "★ " + name + " | " + c.dimension + " [" + c.chunkX + ", " + c.chunkZ + "] | party=" + c.partyId;
            src.sendSuccess(() -> Component.literal(line).withStyle(ChatFormatting.GOLD), false);
        }
        return 1;
    }

    private static int cityList(CommandSourceStack src) {
        WarManager m = WarManager.get(src.getServer());
        if (m.cities().isEmpty()) {
            src.sendSuccess(() -> Component.literal("No strategic cities have been defined."), false);
            return 1;
        }

        for (StrategicCity city : m.cities()) {
            String line = "◆ " + city.id
                    + " | controller=" + m.cityControllerName(city)
                    + " | " + city.dimension
                    + " | chunks [" + city.minChunkX + "," + city.minChunkZ + "] to ["
                    + city.maxChunkX + "," + city.maxChunkZ + "]"
                    + " | capture=[" + city.captureChunkX + "," + city.captureChunkZ + "] Y=" + city.captureY
                    + " | fortifications=" + m.fortificationCount(city) + "/" + m.fortificationBudget(city);
            src.sendSuccess(() -> Component.literal(line).withStyle(ChatFormatting.LIGHT_PURPLE), false);
        }
        return 1;
    }

    private static int cityInfo(CommandSourceStack src, String cityId) {
        WarManager m = WarManager.get(src.getServer());
        StrategicCity city = m.city(cityId);
        if (city == null) {
            src.sendFailure(Component.literal("Unknown strategic city: " + cityId + "."));
            return 0;
        }

        String line = "City " + city.id
                + " | controller=" + m.cityControllerName(city)
                + " | dimension=" + city.dimension
                + " | region=[" + city.minChunkX + "," + city.minChunkZ + "] to ["
                + city.maxChunkX + "," + city.maxChunkZ + "]"
                + " | capture chunk=[" + city.captureChunkX + "," + city.captureChunkZ + "]"
                + " | captureY=" + city.captureY
                + " | permanent blocks=" + city.protectedBlocks.size()
                + " | fortifications=" + m.fortificationCount(city) + "/" + m.fortificationBudget(city);
        src.sendSuccess(() -> Component.literal(line), false);
        return 1;
    }

    private static int createCity(CommandSourceStack src, String cityId, int radiusChunks) {
        try {
            ServerPlayer admin = src.getPlayerOrException();
            WarManager.CityResult result = WarManager.get(src.getServer()).createCity(admin, cityId, radiusChunks);
            if (!result.success()) {
                src.sendFailure(Component.literal(result.message()));
                return 0;
            }

            StrategicCity city = result.city();
            WarManager manager = WarManager.get(src.getServer());
            src.sendSuccess(() -> Component.literal("Created strategic city " + city.id
                    + " with " + result.protectedBlockCount() + " permanent protected blocks. It starts Neutral."
                    + " Fortification budget=" + manager.fortificationBudget(city) + " blocks.")
                    .withStyle(ChatFormatting.LIGHT_PURPLE), true);
            return 1;
        } catch (Exception e) {
            src.sendFailure(Component.literal("City creation failed: " + e.getMessage()));
            return 0;
        }
    }

    private static int deleteCity(CommandSourceStack src, String cityId) {
        WarManager.CityResult result = WarManager.get(src.getServer()).deleteCity(cityId);
        if (!result.success()) {
            src.sendFailure(Component.literal(result.message()));
            return 0;
        }
        src.sendSuccess(() -> Component.literal("Deleted strategic city " + cityId + "."), true);
        return 1;
    }

    private static int setCityController(CommandSourceStack src, String cityId, String rawController) {
        UUID partyId = null;
        if (!"neutral".equalsIgnoreCase(rawController)) {
            try {
                partyId = UUID.fromString(rawController);
            } catch (IllegalArgumentException e) {
                src.sendFailure(Component.literal("Controller must be an OPaC party UUID or 'neutral'."));
                return 0;
            }
        }

        WarManager.CityResult result = WarManager.get(src.getServer()).setCityController(cityId, partyId);
        if (!result.success()) {
            src.sendFailure(Component.literal(result.message()));
            return 0;
        }

        StrategicCity city = result.city();
        src.sendSuccess(() -> Component.literal("City " + city.id + " controller is now "
                + WarManager.get(src.getServer()).cityControllerName(city) + "."), true);
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

            src.sendFailure(Component.literal("Your side is not in a war."));
            return 0;
        } catch (Exception e) {
            src.sendFailure(Component.literal("Surrender failed: " + e.getMessage()));
            return 0;
        }
    }

    private static int adminStop(CommandSourceStack src, String raw) {
        try {
            UUID id = UUID.fromString(raw);
            WarRecord w = WarManager.get(src.getServer()).wars().stream()
                    .filter(x -> x.id.equals(id))
                    .findFirst()
                    .orElse(null);
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

    private static int clearCapital(CommandSourceStack src, String raw) {
        try {
            UUID partyId = UUID.fromString(raw);
            if (!WarManager.get(src.getServer()).clearCapital(partyId)) {
                src.sendFailure(Component.literal("No capital exists for that party ID."));
                return 0;
            }
            src.sendSuccess(() -> Component.literal("Capital cleared for party " + partyId + ".")
                    .withStyle(ChatFormatting.YELLOW), true);
            return 1;
        } catch (IllegalArgumentException e) {
            src.sendFailure(Component.literal("Invalid party UUID."));
            return 0;
        }
    }

    private static int list(CommandSourceStack src) {
        return status(src);
    }
}
