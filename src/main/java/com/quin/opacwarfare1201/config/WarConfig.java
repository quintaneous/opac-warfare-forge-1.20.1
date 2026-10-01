package com.quin.opacwarfare1201.config;

import net.minecraftforge.common.ForgeConfigSpec;

public final class WarConfig {
    public static final ForgeConfigSpec SPEC;
    public static final ForgeConfigSpec.IntValue PREPARATION_SECONDS;
    public static final ForgeConfigSpec.IntValue CAPTURE_SECONDS_FROM_MIDPOINT;
    public static final ForgeConfigSpec.IntValue CAPTURE_VERTICAL_TOLERANCE;
    public static final ForgeConfigSpec.IntValue WAR_LIVES;
    public static final ForgeConfigSpec.BooleanValue REQUIRE_ONLINE_DEFENDER;
    public static final ForgeConfigSpec.BooleanValue ALLOW_DIAGONAL_BORDER;
    public static final ForgeConfigSpec.IntValue MAX_ATTACK_DISTANCE_CHUNKS;
    public static final ForgeConfigSpec.BooleanValue REQUIRE_CONTIGUOUS_CLAIMS;
    public static final ForgeConfigSpec.BooleanValue PREVENT_CLAIM_DISCONNECTION;
    public static final ForgeConfigSpec.BooleanValue CBC_PROTECT_CLAIMED_TERRAIN;
    public static final ForgeConfigSpec.BooleanValue ONLY_ONE_OFFENSIVE_WAR_PER_SIDE;
    public static final ForgeConfigSpec.IntValue EMPTY_DECAY_SECONDS;
    public static final ForgeConfigSpec.IntValue CITY_FORTIFICATION_BUDGET_3X3;
    public static final ForgeConfigSpec.IntValue FAILED_ATTACK_COOLDOWN_MINUTES;
    public static final ForgeConfigSpec.IntValue ACTIVE_WAR_MAX_MINUTES;
    public static final ForgeConfigSpec.IntValue ACTIVATION_ONLINE_GRACE_MINUTES;
    public static final ForgeConfigSpec.IntValue MAX_CONCURRENT_DEFENSIVE_WARS;
    public static final ForgeConfigSpec.DoubleValue CAPTURE_MAX_MULTIPLIER;
    public static final ForgeConfigSpec.IntValue CITY_CAPTURE_NO_BUILD_RADIUS;
    public static final ForgeConfigSpec.IntValue CBC_MANUAL_LOAD_MAX_DISTANCE;
    public static final ForgeConfigSpec.BooleanValue BLOCK_FLUIDS_DURING_CITY_SIEGE;
    public static final ForgeConfigSpec.BooleanValue ISOLATE_WAR_COMBAT;

    static {
        ForgeConfigSpec.Builder b = new ForgeConfigSpec.Builder();

        b.push("war");
        PREPARATION_SECONDS = b.comment("Seconds between /war start and the chunk becoming contested.")
                .defineInRange("preparationSeconds", 60, 0, 3600);
        CAPTURE_SECONDS_FROM_MIDPOINT = b.comment("Seconds for one uncontested attacker to move progress from 50% to 100%.")
                .defineInRange("captureSecondsFromMidpoint", 300, 10, 7200);
        CAPTURE_VERTICAL_TOLERANCE = b.comment("Players anywhere horizontally inside the contested chunk count only when within this many blocks vertically of the surface objective.")
                .defineInRange("captureVerticalTolerance", 10, 1, 32);
        WAR_LIVES = b.comment("Lives per participant for each chunk battle. 0 disables lives.")
                .defineInRange("warLives", 3, 0, 100);
        REQUIRE_ONLINE_DEFENDER = b.comment("Require at least one defender-side player online to start a war.")
                .define("requireOnlineDefender", true);
        ALLOW_DIAGONAL_BORDER = b.comment("Allow diagonal adjacency to qualify a target as a border chunk.")
                .define("allowDiagonalBorder", false);
        MAX_ATTACK_DISTANCE_CHUNKS = b.comment("Season 1 maximum Manhattan distance from capital-connected attacker territory to an enemy target chunk.")
                .defineInRange("maxAttackDistanceChunks", 10, 1, 64);
        ONLY_ONE_OFFENSIVE_WAR_PER_SIDE = b.comment("Prevent one side from attacking multiple chunks at once.")
                .define("oneOffensiveWarPerSide", true);
        EMPTY_DECAY_SECONDS = b.comment("Seconds with nobody in the capture zone for progress to decay from an extreme back to 50%.")
                .defineInRange("emptyDecaySeconds", 180, 10, 7200);
        CITY_FORTIFICATION_BUDGET_3X3 = b.comment(
                        "Fortification block budget for a 3x3-chunk strategic city. Other city sizes scale linearly by chunk count.",
                        "Only blocks added after city creation count; the original city snapshot remains permanent infrastructure.")
                .defineInRange("cityFortificationBudget3x3", 5000, 0, 100000);
        FAILED_ATTACK_COOLDOWN_MINUTES = b.comment(
                        "Minutes an attacking nation must wait before starting another offensive war after it fails to capture a normal chunk or strategic city.",
                        "Attacker surrender also counts as a failed attack. Successful captures and admin-stopped wars do not apply a cooldown. 0 disables this rule.")
                .defineInRange("failedAttackCooldownMinutes", 30, 0, 1440);
        ACTIVE_WAR_MAX_MINUTES = b.comment(
                        "Maximum ACTIVE battle duration. If time expires before attackers capture the objective, defenders win. 0 disables the timeout.")
                .defineInRange("activeWarMaxMinutes", 60, 0, 1440);
        ACTIVATION_ONLINE_GRACE_MINUTES = b.comment(
                        "When PREPARING ends, both frozen rosters must have an online player (defender requirement obeys requireOnlineDefender).",
                        "If a side is missing, activation waits this many minutes. At expiry, an absent attacker loses; otherwise an absent defender forfeits.")
                .defineInRange("activationOnlineGraceMinutes", 5, 0, 60);
        MAX_CONCURRENT_DEFENSIVE_WARS = b.comment(
                        "Maximum simultaneous wars in which one nation may be the defender. 0 means unlimited.")
                .defineInRange("maxConcurrentDefensiveWars", 2, 0, 16);
        CAPTURE_MAX_MULTIPLIER = b.comment(
                        "Maximum capture-speed multiplier from numerical advantage. Scaling uses sqrt(player advantage), capped here.")
                .defineInRange("captureMaxMultiplier", 2.0D, 1.0D, 5.0D);
        CITY_CAPTURE_NO_BUILD_RADIUS = b.comment(
                        "Horizontal block radius around a strategic city's capture point where structural defender construction is blocked during ACTIVE.",
                        "CBC ammunition loading is still allowed when it qualifies as actual cannon loading.")
                .defineInRange("cityCaptureNoBuildRadius", 4, 0, 16);
        CBC_MANUAL_LOAD_MAX_DISTANCE = b.comment(
                        "Maximum straight-line distance from a placed CBC munition to an existing CBC cannon structure for the siege reload exception.")
                .defineInRange("cbcManualLoadMaxDistance", 8, 1, 16);
        BLOCK_FLUIDS_DURING_CITY_SIEGE = b.comment(
                        "Block player/world fluid placement and fluid-generated block changes inside strategic cities while a siege is PREPARING or ACTIVE.")
                .define("blockFluidsDuringCitySiege", true);
        ISOLATE_WAR_COMBAT = b.comment(
                        "Inside ACTIVE battlefields, only frozen-roster opponents with remaining lives may damage each other.")
                .define("isolateWarCombat", true);
        b.pop();

        b.push("territory");
        REQUIRE_CONTIGUOUS_CLAIMS = b.comment("Require new normal claims to touch same-party territory on a cardinal side. Once a capital exists, the touched territory must be capital-connected.")
                .define("requireContiguousClaims", true);
        PREVENT_CLAIM_DISCONNECTION = b.comment("Prevent normal unclaims that would split a connected territory component. Capital chunks can never be normally unclaimed.")
                .define("preventClaimDisconnection", true);
        b.pop();

        b.push("compatibility");
        CBC_PROTECT_CLAIMED_TERRAIN = b.comment("If CBC is installed, block CBC terrain damage in ordinary claimed chunks; allow it only in ACTIVE contested chunks.")
                .define("protectClaimedTerrainFromCBCOutsideActiveWar", true);
        b.pop();

        SPEC = b.build();
    }

    private WarConfig() {}
}
