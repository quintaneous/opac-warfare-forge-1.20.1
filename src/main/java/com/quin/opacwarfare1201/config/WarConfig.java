package com.quin.opacwarfare1201.config;

import net.minecraftforge.common.ForgeConfigSpec;

public final class WarConfig {
    public static final ForgeConfigSpec SPEC;
    public static final ForgeConfigSpec.IntValue PREPARATION_SECONDS;
    public static final ForgeConfigSpec.IntValue CAPTURE_SECONDS_FROM_MIDPOINT;
    public static final ForgeConfigSpec.IntValue CAPTURE_RADIUS_BLOCKS;
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

    static {
        ForgeConfigSpec.Builder b = new ForgeConfigSpec.Builder();
        b.push("war");
        PREPARATION_SECONDS = b.comment("Seconds between /war start and the chunk becoming contested.")
                .defineInRange("preparationSeconds", 60, 0, 3600);
        CAPTURE_SECONDS_FROM_MIDPOINT = b.comment("Seconds for one uncontested attacker to move progress from 50% to 100%.")
                .defineInRange("captureSecondsFromMidpoint", 300, 10, 7200);
        CAPTURE_RADIUS_BLOCKS = b.comment("Horizontal radius, in blocks, of the surface capture zone.")
                .defineInRange("captureRadiusBlocks", 6, 2, 16);
        CAPTURE_VERTICAL_TOLERANCE = b.comment("How far above or below the capture point a player may be and still count.")
                .defineInRange("captureVerticalTolerance", 3, 1, 16);
        WAR_LIVES = b.comment("Lives per participant for each chunk battle. 0 disables lives.")
                .defineInRange("warLives", 3, 0, 100);
        REQUIRE_ONLINE_DEFENDER = b.comment("Require at least one defender-side player online to start a war.")
                .define("requireOnlineDefender", true);
        ALLOW_DIAGONAL_BORDER = b.comment("Allow diagonal adjacency to qualify a target as a border chunk.")
                .define("allowDiagonalBorder", false);
        MAX_ATTACK_DISTANCE_CHUNKS = b.comment("Maximum Manhattan chunk distance from attacker-owned territory to a target. 1 means attacks must touch attacker territory.")
                .defineInRange("maxAttackDistanceChunks", 2, 1, 32);
        ONLY_ONE_OFFENSIVE_WAR_PER_SIDE = b.comment("Prevent one side from attacking multiple chunks at once.")
                .define("oneOffensiveWarPerSide", true);
        EMPTY_DECAY_SECONDS = b.comment("Seconds with nobody in the capture zone for progress to decay from an extreme back to 50%.")
                .defineInRange("emptyDecaySeconds", 180, 10, 7200);
        b.pop();

        b.push("territory");
        REQUIRE_CONTIGUOUS_CLAIMS = b.comment("Require new normal claims to touch existing same-party territory on a cardinal side. The first claim in a dimension is exempt.")
                .define("requireContiguousClaims", true);
        PREVENT_CLAIM_DISCONNECTION = b.comment("Prevent normal unclaims that would increase the number of disconnected territory components.")
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
