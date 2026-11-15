package net.bandit.oathboundrelics.events;

import net.bandit.oathboundrelics.config.OathboundConfig;
import net.bandit.oathboundrelics.fabricbridge.events.entity.living.LivingHealEvent;
import net.bandit.oathboundrelics.registry.EffectRegistry;
import net.minecraft.world.entity.LivingEntity;

/**
 * Called from FabricEventBridge.fireLivingHeal.
 */
public final class LustEvents {

    private LustEvents() {
    }

    public static void onHeal(LivingHealEvent event) {
        LivingEntity entity = event.getEntity();

        if (!entity.level().isClientSide() && entity.hasEffect(EffectRegistry.CRAVING)) {
            event.setAmount((float) (event.getAmount() * OathboundConfig.heartseekerCravingHealingMultiplier()));
        }
    }
}
