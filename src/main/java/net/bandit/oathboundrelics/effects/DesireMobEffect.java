package net.bandit.oathboundrelics.effects;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;

/**
 * Lust's reward stack. Each Heartseeker kill raises the amplifier by one; at the configured
 * maximum the next fully drawn shot is empowered and the effect is consumed.
 */
public class DesireMobEffect extends MobEffect {

    public DesireMobEffect() {
        super(MobEffectCategory.BENEFICIAL, 0xFF6F9C);
    }
}
