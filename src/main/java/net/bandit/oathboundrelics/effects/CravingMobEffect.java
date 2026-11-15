package net.bandit.oathboundrelics.effects;

import net.bandit.oathboundrelics.config.OathboundConfig;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

/**
 * Lust's drawback. Drains hunger every tick; healing is reduced separately in LustEvents.
 * A Heartseeker kill removes it.
 */
public class CravingMobEffect extends MobEffect {

    public CravingMobEffect() {
        super(MobEffectCategory.HARMFUL, 0x7A0C1C);
    }

    @Override
    public boolean applyEffectTick(LivingEntity entity, int amplifier) {
        if (!entity.level().isClientSide() && entity instanceof Player player) {
            player.causeFoodExhaustion((float) (OathboundConfig.heartseekerCravingExhaustionPerTick() * (amplifier + 1)));
        }
        return true;
    }

    @Override
    public boolean shouldApplyEffectTickThisTick(int duration, int amplifier) {
        return true;
    }
}
