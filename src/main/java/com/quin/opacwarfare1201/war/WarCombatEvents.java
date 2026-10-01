package com.quin.opacwarfare1201.war;

import com.quin.opacwarfare1201.config.WarConfig;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

public final class WarCombatEvents {
    private WarCombatEvents() {}

    @SubscribeEvent
    public static void onLivingAttack(LivingAttackEvent event) {
        if (!WarConfig.ISOLATE_WAR_COMBAT.get()) return;
        if (!(event.getEntity() instanceof ServerPlayer victim)) return;

        WarManager manager = WarManager.get(victim.server);
        ChunkPos chunk = victim.chunkPosition();
        WarRecord war = manager.activeWarAt(
                victim.level().dimension().location(), chunk.x, chunk.z);
        if (war == null) return;

        Entity sourceEntity = event.getSource().getEntity();
        if (!(sourceEntity instanceof ServerPlayer attacker)) return;

        boolean victimParticipant = manager.isParticipant(war, victim.getUUID(), false);
        boolean attackerParticipant = manager.isParticipant(war, attacker.getUUID(), false);

        // Spectators/third parties cannot interfere with battle participants,
        // and battle participants cannot use the war zone to attack spectators.
        if (!victimParticipant || !attackerParticipant) {
            if (victimParticipant || attackerParticipant) event.setCanceled(true);
            return;
        }

        // Eliminated players are no longer combatants.
        if (!manager.isParticipant(war, victim.getUUID(), true)
                || !manager.isParticipant(war, attacker.getUUID(), true)) {
            event.setCanceled(true);
            return;
        }

        // The warfare layer treats same-side damage as friendly-fire protected.
        if (manager.isAttacker(war, victim.getUUID()) == manager.isAttacker(war, attacker.getUUID())) {
            event.setCanceled(true);
        }
    }
}
