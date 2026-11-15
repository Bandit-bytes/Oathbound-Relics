package net.bandit.oathboundrelics.events;

import net.bandit.oathboundrelics.OathboundRelicsMod;
import net.bandit.oathboundrelics.config.OathboundConfig;
import net.bandit.oathboundrelics.registry.EffectRegistry;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingHealEvent;

@EventBusSubscriber(modid = OathboundRelicsMod.MOD_ID)
public final class LustEvents {

    private LustEvents() {
    }

    @SubscribeEvent
    public static void onHeal(LivingHealEvent event) {
        LivingEntity entity = event.getEntity();

        if (!entity.level().isClientSide() && entity.hasEffect(EffectRegistry.CRAVING)) {
            event.setAmount((float) (event.getAmount() * OathboundConfig.heartseekerCravingHealingMultiplier()));
        }
    }
}
