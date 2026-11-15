package net.bandit.oathboundrelics.effects;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;

import java.util.Comparator;

/**
 * Applied by Lust: Heartseeker's arrows. An Enthralled mob will not target players and turns
 * on the nearest other monster instead. Goal-based mobs are also blocked from picking a player
 * target in MobTargetMixin; brain-based mobs (piglins, hoglins) have their attack memory cleared here.
 */
public class EnthralledMobEffect extends MobEffect {

    private static final double RETARGET_RANGE = 12.0D;

    public EnthralledMobEffect() {
        super(MobEffectCategory.HARMFUL, 0xE0448A);
    }

    @Override
    public boolean applyEffectTick(LivingEntity entity, int amplifier) {
        if (entity.level().isClientSide() || !(entity instanceof Mob mob)) {
            return true;
        }

        if (mob.getTarget() instanceof Player) {
            mob.setTarget(null);
        }

        if (mob.getBrain().hasMemoryValue(MemoryModuleType.ATTACK_TARGET)
                && mob.getBrain().getMemory(MemoryModuleType.ATTACK_TARGET).orElse(null) instanceof Player) {
            mob.getBrain().eraseMemory(MemoryModuleType.ATTACK_TARGET);
        }

        if (mob.getTarget() == null && mob.tickCount % 10 == 0) {
            AABB area = mob.getBoundingBox().inflate(RETARGET_RANGE);
            mob.level().getEntitiesOfClass(
                            LivingEntity.class,
                            area,
                            other -> other != mob && other.isAlive() && other instanceof Enemy && !(other instanceof Player)
                    ).stream()
                    .min(Comparator.comparingDouble(other -> other.distanceToSqr(mob)))
                    .ifPresent(mob::setTarget);
        }

        return true;
    }

    @Override
    public boolean shouldApplyEffectTickThisTick(int duration, int amplifier) {
        return true;
    }
}
