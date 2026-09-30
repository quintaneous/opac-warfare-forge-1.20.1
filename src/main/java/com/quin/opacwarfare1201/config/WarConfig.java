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
