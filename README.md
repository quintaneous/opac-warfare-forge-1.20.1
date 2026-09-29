# OPaC Warfare 1.20.1 Port — v0.1.0-beta

An **unofficial Forge 1.20.1 reimplementation/port** of the core territorial-war ideas from
[SvinPepe's MIT-licensed Open Parties and Claims: Warfare](https://github.com/SvinPepe/customclaims),
targeted at the Create war server stack.

This is **not an official build** of that project.

## Target environment

- Minecraft **1.20.1**
- Forge **47.4.10**
- Java **17**
- Open Parties and Claims **0.31.6** (Forge 1.20.1)
- Create: Big Cannons **5.11.4** optional but specifically supported by the compatibility mixin

## v0.1.0-beta feature set

- `/war start` attacks the OPaC-claimed chunk the player is standing in.
- Attack target must be an accessible border chunk.
- Configurable PREPARING delay before the chunk becomes contested.
- Active contested chunk is temporarily moved to OPaC's special `SERVER` claim owner.
- Attacker and defender gain full chunk access while the battle is active.
- Third parties remain protected from normal OPaC interactions in the contested chunk.
- Capture progress starts at 50%.
- More attackers than defenders moves progress toward attacker victory.
- More defenders than attackers moves progress toward defender victory.
- Empty battlefields decay toward 50%.
- Configurable war lives; deaths inside an active battle chunk reduce the player's remaining lives.
- Attacker victory administratively transfers the claim to the attacking party owner.
- Defender victory restores the original claim.
- War records persist through server restarts using SavedData.
- CBC compatibility mixin blocks CBC terrain damage in ordinary claimed chunks and allows it in ACTIVE contested chunks.
- Admin stop/list commands.

## Commands

```text
/war start
/war status
/war surrender
/war admin list
/war admin stop <war-uuid>
```

For this beta, `/war start` and `/war surrender` are limited to the OPaC party owner.

## Server config

Forge creates `serverconfig/opac-warfare-1201.toml` for a world. Defaults:

```text
preparationSeconds = 60
captureSecondsFromMidpoint = 300
warLives = 3
requireOnlineDefender = true
allowDiagonalBorder = false
oneOffensiveWarPerSide = true
emptyDecaySeconds = 180
protectClaimedTerrainFromCBCOutsideActiveWar = true
```

## Intended first test

Use a disposable test world and two OPaC parties.

1. Create Party Red and Party Blue.
2. Give each party normal OPaC claims that touch wilderness or one another at a border.
3. Confirm CBC cannot damage the other party's normal claimed terrain during peace.
4. Have Red stand inside a border chunk owned by Blue and run `/war start`.
5. Wait for PREPARING to become ACTIVE.
6. Confirm both sides can interact/build/break in the contested chunk while outsiders cannot.
7. Confirm CBC can damage terrain in the ACTIVE contested chunk.
8. Hold the chunk uncontested and verify capture progress reaches 100% and the claim changes owner.
9. Restart the server during PREPARING and during ACTIVE to verify persistence.
10. Test `/war surrender` and `/war admin stop <uuid>`.

## Known v0.1 limitations

This is intentionally a first systems prototype, not the finished server warfare mod.

- **Not runtime-verified yet against the exact live server.**
- CBC terrain protection currently decides from the **target chunk**, not projectile shooter identity.
- No Xaero map-click attack UI or battle markers yet.
- No war scheduling/windows, daily limits, cooldown UI, diplomacy, alliances, or AFK detection yet.
- No Create contraption-specific warfare protection yet beyond OPaC's normal access system.
- No contiguous-claim enforcement yet.
- No strategic city layer, protected server city core, city rewards, or railway controls yet.
- Capture is chunk-by-chunk in v0.1.
- Party-owner-only command restriction is temporary.

## Planned progression

### v0.2
- harden CBC attacker attribution / third-party protection
- verify all autocannon, big-cannon, HE and block-transformation paths
- better battle announcements and status

### v0.3
- contiguous normal claims
- prevent unclaim actions that split territory
- administrative/conquest bypass for special territory transfers

### v0.4
- server-defined strategic cities
- multi-chunk city ownership independent from city identity
- protected city core regions
- city capture integration

### v0.5+
- railway checkpoint control
- claim-cap/city benefits
- victory points / season objectives
- diplomacy and configurable war windows

## Building

This source project is configured for ForgeGradle 6 and Java 17.

```text
gradle build
```

The output JAR will be under `build/libs/`.

## Licensing / attribution

This project is MIT licensed. It is an independent reimplementation inspired by the MIT-licensed
Open Parties and Claims: Warfare project by SvinPepe. See `LICENSE`.
