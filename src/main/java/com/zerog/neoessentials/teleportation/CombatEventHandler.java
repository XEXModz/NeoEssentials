package com.zerog.neoessentials.teleportation;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.TamableAnimal;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Combat event handler: marks players in combat ONLY when actually fighting.
 * This tracks PvP combat and combat with hostile mobs, but NOT:
 *   - Environmental damage (fall, drowning, fire, lava, hunger, etc.)
 *   - Attacks on passive targets (armor stands, item frames, vehicles, villagers)
 *   - Attacks on or by the player's own tamed pets
 *   - Self-damage (reflected thorns, etc.)
 */
@EventBusSubscriber(modid = "neoessentials")
public class CombatEventHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger(CombatEventHandler.class);

    /**
     * Returns true if attacking the given target should count as combat.
     * Passive entities and owned pets do not trigger combat.
     */
    private static boolean isCombatTarget(ServerPlayer attacker, Entity target) {
        if (target == null) return false;
        // Only LivingEntity targets count; armor stands are LivingEntity but handled specially
        if (!(target instanceof LivingEntity living)) return false;
        // Exclude the player's own tamed pets
        if (living instanceof TamableAnimal tame && tame.isTame()) {
            java.util.UUID ownerId = tame.getOwnerUUID();
            if (ownerId != null && ownerId.equals(attacker.getUUID())) return false;
        }
        // Exclude armor stands explicitly — they are LivingEntity but not a combat target
        String entityType = living.getType().toString();
        if (entityType.contains("armor_stand")) return false;
        // Count hostile mobs and other players
        return living instanceof Mob || living instanceof Player;
    }

    /**
     * Mark player as in combat when they attack a valid combat target.
     * Attacking armor stands, vehicles, item frames, or their own pets does NOT trigger combat.
     */
    @SubscribeEvent
    public static void onAttackEntity(AttackEntityEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (!isCombatTarget(player, event.getTarget())) return;

        CombatTracker.markInCombat(player);
        LOGGER.debug("Player {} entered combat by attacking {}",
            player.getName().getString(),
            event.getTarget().getName().getString());
    }

    /**
     * Mark player as in combat when they take damage from another valid combat entity.
     * Environmental damage and self-damage do NOT trigger combat.
     * This refreshes the combat timer on every valid hit so long fights stay tagged.
     */
    @SubscribeEvent
    public static void onPlayerDamage(LivingDamageEvent.Pre event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;

        DamageSource source = event.getContainer().getSource();
        Entity attacker = source.getEntity();

        // Must be a living attacker
        if (!(attacker instanceof LivingEntity livingAttacker)) return;
        // Ignore self-damage (e.g. thorns reflecting onto yourself)
        if (attacker.getUUID().equals(player.getUUID())) return;
        // Ignore damage from the player's own tamed pets
        if (livingAttacker instanceof TamableAnimal tame && tame.isTame()) {
            java.util.UUID ownerId = tame.getOwnerUUID();
            if (ownerId != null && ownerId.equals(player.getUUID())) return;
        }
        // Only hostile mobs or other players count as combat
        if (!(livingAttacker instanceof Mob) && !(livingAttacker instanceof Player)) return;

        // Refresh combat timer on every valid hit (unlike the old code which only
        // tagged if not-already-in-combat — that let the tag expire mid-fight).
        CombatTracker.markInCombat(player);
        LOGGER.debug("Player {} in combat by receiving damage from {}",
            player.getName().getString(),
            attacker.getName().getString());
    }

    /**
     * Clear combat status when player logs out
     */
    @SubscribeEvent
    public static void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            CombatTracker.clearCombat(player);
            LOGGER.debug("Cleared combat status for logging out player {}", player.getName().getString());
        }
    }
}
