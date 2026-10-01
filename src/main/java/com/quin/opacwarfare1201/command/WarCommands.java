package com.quin.opacwarfare1201.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.quin.opacwarfare1201.config.WarConfig;
import com.quin.opacwarfare1201.network.WarNetwork;
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

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class WarCommands {
    private WarCommands() {}

    public static void register(CommandDispatcher<CommandSourceStack> d) {
        d.register(Commands.literal("war")
                .then(Commands.literal("menu").executes(ctx -> menu(ctx.getSource())))
                .then(Commands.literal("start").executes(ctx -> start(ctx.getSource())))
                .then(Commands.literal("status").executes(ctx -> status(ctx.getSource())))
                .then(Commands.literal("cooldown").executes(ctx -> cooldown(ctx.getSource())))
                .then(Commands.literal("surrender")
                        .executes(ctx -> surrender(ctx.getSource(), null))
                        .then(Commands.argument("warId", StringArgumentType.word())
                                .executes(ctx -> surrender(
                                        ctx.getSource(),
                                        StringArgumentType.getString(ctx, "warId")))))
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
                        .then(Commands.literal("breachable")
                                .requires(src -> src.hasPermission(2))
                                .then(Commands.argument("cityId", StringArgumentType.word())
                                        .then(Commands.argument("radiusBlocks", IntegerArgumentType.integer(1, 64))
                                                .executes(ctx -> editCityProtection(
                                                        ctx.getSource(),
                                                        StringArgumentType.getString(ctx, "cityId"),
                                                        IntegerArgumentType.getInteger(ctx, "radiusBlocks"),
                                                        false)))))
                        .then(Commands.literal("permanent")
                                .requires(src -> src.hasPermission(2))
                                .then(Commands.argument("cityId", StringArgumentType.word())
                                        .then(Commands.argument("radiusBlocks", IntegerArgumentType.integer(1, 64))
                                                .executes(ctx -> editCityProtection(
                                                        ctx.getSource(),
                                                        StringArgumentType.getString(ctx, "cityId"),
                                                        IntegerArgumentType.getInteger(ctx, "radiusBlocks"),
                                                        true)))))
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

    private static int menu(CommandSourceStack src) {
        try {
            ServerPlayer player = src.getPlayerOrException();
            WarManager manager = WarManager.get(src.getServer());
            IServerPartyAPI party = OpenPACServerAPI.get(src.getServer()).getPartyManager().getPartyByMember(player.getUUID());

            List<String> lines = new ArrayList<>();
            lines.add("H|NATION OVERVIEW");

            if (party == null) {
                lines.add("R|No OPaC party");
                lines.add("D|Create or join a party before claiming territory.");
                lines.add("D|Use /oparties create or join an existing party.");
            } else {
                UUID partyId = party.getId();
                boolean owner = party.getOwner().getUUID().equals(player.getUUID());
                CapitalRecord capital = manager.capital(partyId);
                long cooldown = manager.remainingAttackCooldownSeconds(partyId, player.getUUID());
                int claims = manager.claimCountForParty(partyId);
                long controlledCities = manager.cities().stream()
                        .filter(city -> city.isControlledBy(partyId))
                        .count();

                lines.add("G|" + party.getDefaultName() + (owner ? "  [Owner]" : "  [Member]"));
                lines.add("B|Members: " + party.getMemberCount() + "   Claims: " + claims
                        + "   Cities: " + controlledCities);
                if (capital == null) {
                    lines.add("Y|Capital: Not established");
                } else {
                    lines.add("Y|Capital: " + capital.dimension + "  [" + capital.chunkX + ", " + capital.chunkZ + "]");
                }
                lines.add(cooldown > 0
                        ? "R|Attack cooldown: " + WarManager.formatCooldown(cooldown)
                        : "G|Attack cooldown: Ready");

                lines.add("");
                lines.add("H|YOUR WARS");
                boolean hasWar = false;
                for (WarRecord war : manager.wars()) {
                    boolean attacker = partyId.equals(war.attackerPartyId);
                    boolean defender = partyId.equals(war.defenderPartyId);
                    if (!attacker && !defender) continue;
                    hasWar = true;

                    String role = attacker ? "ATTACKING" : "DEFENDING";
                    String target = war.isCityWar()
                            ? "City " + war.cityId
                            : "Chunk [" + war.chunkX + ", " + war.chunkZ + "]";
                    int progress = (int)Math.round(war.progress * 100D);
                    int maxLives = WarConfig.WAR_LIVES.get();
                    String lives;
                    if (!war.isParticipant(player.getUUID())) {
                        lives = "not rostered";
                    } else {
                        lives = maxLives <= 0
                                ? "unlimited"
                                : String.valueOf(war.lives.getOrDefault(player.getUUID(), maxLives));
                    }

                    long remaining = war.phase == com.quin.opacwarfare1201.war.WarPhase.ACTIVE
                            ? manager.remainingBattleSeconds(war)
                            : manager.remainingPreparationSeconds(war);
                    String timer = remaining < 0 ? "" : "   Timer: " + WarManager.formatCooldown(remaining);

                    lines.add((attacker ? "R|" : "B|") + role + " - " + target + " - " + war.phase);
                    lines.add("D|Control: " + progress + "% attacker   Your lives: " + lives + timer);
                    lines.add("D|Frozen rosters: A=" + war.attackerRoster.size() + " D=" + war.defenderRoster.size());
                    lines.add("D|War ID: " + war.id);
                }
                if (!hasWar) lines.add("D|No active or preparing wars.");

                lines.add("");
                lines.add("H|YOUR STRATEGIC CITIES");
                boolean hasCity = false;
                for (StrategicCity city : manager.cities()) {
                    if (!city.isControlledBy(partyId)) continue;
                    hasCity = true;
                    lines.add("P|" + city.id + "  [" + city.captureChunkX + ", " + city.captureChunkZ + "]");
                    lines.add("D|Fortifications: " + manager.fortificationCount(city)
                            + "/" + manager.fortificationBudget(city));
                }
                if (!hasCity) lines.add("D|Your nation controls no strategic cities.");
            }

            lines.add("");
            lines.add("H|WORLD CAPITALS");
            if (manager.capitals().isEmpty()) {
                lines.add("D|No capitals have been established.");
            } else {
                for (CapitalRecord capital : manager.capitals()) {
                    String name = OpacSides.sideName(src.getServer(), capital.partyId, capital.ownerId);
                    lines.add("Y|" + name + "  [" + capital.chunkX + ", " + capital.chunkZ + "]");
                    lines.add("D|" + capital.dimension);
                }
            }

            lines.add("");
            lines.add("H|STRATEGIC CITIES");
            if (manager.cities().isEmpty()) {
                lines.add("D|No strategic cities have been defined.");
            } else {
                for (StrategicCity city : manager.cities()) {
                    lines.add("P|" + city.id + " - " + manager.cityControllerName(city));
                    lines.add("D|Capture [" + city.captureChunkX + ", " + city.captureChunkZ + "] Y=" + city.captureY
                            + "   Fortifications " + manager.fortificationCount(city)
                            + "/" + manager.fortificationBudget(city));
                }
            }

            lines.add("");
            lines.add("H|QUICK COMMANDS");
            lines.add("D|/war start  |  /war city attack <city>  |  /war surrender");
            lines.add("D|/war capital set  |  /war cooldown  |  /war menu");

            WarNetwork.openDashboard(player, lines);
            return 1;
        } catch (Exception e) {
            src.sendFailure(Component.literal("Could not open warfare dashboard: " + e.getMessage()));
            return 0;
        }
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

            long remaining = WarManager.get(src.getServer()).remainingAttackCooldownSeconds(party.getId(), player.getUUID());
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

    private static int editCityProtection(CommandSourceStack src, String cityId,
                                                  int radiusBlocks, boolean permanent) {
        try {
            ServerPlayer admin = src.getPlayerOrException();
            WarManager.CityResult result = WarManager.get(src.getServer())
                    .setCityProtectionAround(admin, cityId, radiusBlocks, permanent);
            if (!result.success()) {
                src.sendFailure(Component.literal(result.message()));
                return 0;
            }
            src.sendSuccess(() -> Component.literal(result.message())
                    .withStyle(permanent ? ChatFormatting.GOLD : ChatFormatting.YELLOW), true);
            return 1;
        } catch (Exception e) {
            src.sendFailure(Component.literal("City protection edit failed: " + e.getMessage()));
            return 0;
        }
    }

    private static int surrender(CommandSourceStack src, String rawWarId) {
        try {
            ServerPlayer player = src.getPlayerOrException();
            IServerPartyAPI party = OpenPACServerAPI.get(src.getServer()).getPartyManager().getPartyByMember(player.getUUID());
            if (party == null) {
                src.sendFailure(Component.literal("You are not in an OPaC party."));
                return 0;
            }
            if (!party.getOwner().getUUID().equals(player.getUUID())) {
                src.sendFailure(Component.literal("Only the party owner can surrender."));
                return 0;
            }

            WarManager manager = WarManager.get(src.getServer());
            List<WarRecord> nationWars = manager.wars().stream()
                    .filter(war -> party.getId().equals(war.attackerPartyId)
                            || party.getId().equals(war.defenderPartyId))
                    .toList();

            if (rawWarId == null) {
                if (nationWars.isEmpty()) {
                    src.sendFailure(Component.literal("Your nation is not in a war."));
                    return 0;
                }
                if (nationWars.size() > 1) {
                    src.sendFailure(Component.literal(
                            "Your nation is in multiple wars. Use /war surrender <warId>; IDs are shown in /war menu."));
                    return 0;
                }
                manager.surrender(player, nationWars.get(0));
                return 1;
            }

            UUID warId;
            try {
                warId = UUID.fromString(rawWarId);
            } catch (IllegalArgumentException e) {
                src.sendFailure(Component.literal("Invalid war ID."));
                return 0;
            }

            WarRecord selected = nationWars.stream()
                    .filter(war -> war.id.equals(warId))
                    .findFirst()
                    .orElse(null);
            if (selected == null) {
                src.sendFailure(Component.literal("That war does not involve your nation."));
                return 0;
            }

            manager.surrender(player, selected);
            return 1;
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
