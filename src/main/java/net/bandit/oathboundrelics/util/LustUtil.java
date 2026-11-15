package net.bandit.oathboundrelics.util;

import it.unimi.dsi.fastutil.ints.IntSet;
import net.bandit.oathboundrelics.OathboundRelicsMod;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.bandit.oathboundrelics.config.OathboundConfig;
import net.bandit.oathboundrelics.registry.EffectRegistry;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import org.jetbrains.annotations.Nullable;
import java.util.List;

/**
 * Shared rules for Lust: Heartseeker and its seeking arrow.
 */
public final class LustUtil {

    /** Entity types that can never be Enthralled. Includes the c:bosses convention tag. */
    public static final TagKey<EntityType<?>> ENTHRALL_IMMUNE = TagKey.create(
            Registries.ENTITY_TYPE,
            ResourceLocation.fromNamespaceAndPath(OathboundRelicsMod.MOD_ID, "enthrall_immune")
    );

    private LustUtil() {
    }

    public static boolean canUseHeartseeker(Player player) {
        if (!OathboundConfig.enableHeartseeker()) {
            return false;
        }

        if (player.isCreative() && OathboundConfig.enableCreativeBrandedTimeBypass()) {
            return true;
        }

        // Same 99.5% Branded Progress gate as the other sin weapons.
        return OathboundUtil.isBranded(player) && SlothWeaponUtil.qualifiesForLethargicFlail(player);
    }

    /**
     * Entities the arrow must never chase or hit: the wielder, any player, armor stands,
     * the wielder's own pets, and anything on the wielder's team.
     */
    public static boolean isProtected(@Nullable Entity owner, LivingEntity target) {
        if (target instanceof Player || target instanceof ArmorStand) {
            return true;
        }

        if (owner == null) {
            return false;
        }

        if (target == owner) {
            return true;
        }

        if (target instanceof OwnableEntity ownable) {
            LivingEntity petOwner = ownable.getOwner();
            if (petOwner != null && petOwner.getUUID().equals(owner.getUUID())) {
                return true;
            }
        }

        return owner.isAlliedTo(target) || target.isAlliedTo(owner);
    }

    /**
     * Picks the next target for a seeking arrow. Hostile mobs are always preferred. If none is
     * in range and {@code allowNonHostile} is true (Wandering Eye), any other living thing is
     * chased instead. Targets roughly ahead of the arrow score better than ones behind it.
     */
    @Nullable
    public static LivingEntity findTarget(Entity arrow, @Nullable Entity owner, IntSet alreadyHit, boolean allowNonHostile) {
        double range = OathboundConfig.heartseekerSeekRange();
        double rangeSqr = range * range;

        List<LivingEntity> candidates = arrow.level().getEntitiesOfClass(
                LivingEntity.class,
                arrow.getBoundingBox().inflate(range),
                target -> target.isAlive()
                        && !target.isSpectator()
                        && !alreadyHit.contains(target.getId())
                        && !isProtected(owner, target)
                        && target.distanceToSqr(arrow) <= rangeSqr
        );

        Vec3 heading = arrow.getDeltaMovement().lengthSqr() > 1.0E-6
                ? arrow.getDeltaMovement().normalize()
                : Vec3.ZERO;

        LivingEntity bestHostile = null;
        double bestHostileScore = Double.MAX_VALUE;
        LivingEntity bestOther = null;
        double bestOtherScore = Double.MAX_VALUE;

        for (LivingEntity candidate : candidates) {
            Vec3 toTarget = aimPoint(candidate).subtract(arrow.position());
            double distance = toTarget.length();
            double alignment = distance > 1.0E-4 ? heading.dot(toTarget.scale(1.0D / distance)) : 1.0D;
            double score = distance * (1.5D - 0.5D * alignment);

            boolean hostile = candidate instanceof Enemy;
            if (hostile ? score >= bestHostileScore : score >= bestOtherScore) {
                continue;
            }

            if (!hasLineOfSight(arrow, candidate)) {
                continue;
            }

            if (hostile) {
                bestHostile = candidate;
                bestHostileScore = score;
            } else {
                bestOther = candidate;
                bestOtherScore = score;
            }
        }

        if (bestHostile != null) {
            return bestHostile;
        }

        return allowNonHostile ? bestOther : null;
    }

    public static boolean hasLineOfSightTo(Entity arrow, Entity target) {
        return hasLineOfSight(arrow, target);
    }

    /**
     * Enthralls a target that survived a hit, unless it is a boss or in the immune tag.
     */
    public static void enthrall(LivingEntity target) {
        int duration = OathboundConfig.heartseekerEnthrallDurationTicks();
        if (duration <= 0 || !(target instanceof Mob) || target.getType().is(ENTHRALL_IMMUNE)) {
            return;
        }

        target.addEffect(new MobEffectInstance(EffectRegistry.ENTHRALLED, duration, 0, false, true, true));
    }

    public static Vec3 aimPoint(Entity target) {
        return target.position().add(0.0D, target.getBbHeight() * 0.5D, 0.0D);
    }

    private static boolean hasLineOfSight(Entity arrow, Entity target) {
        HitResult hit = arrow.level().clip(new ClipContext(
                arrow.position(),
                aimPoint(target),
                ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE,
                arrow
        ));
        return hit.getType() == HitResult.Type.MISS;
    }

    /**
     * Damage multiplier for a hit, given how many targets the arrow has already hit.
     */
    public static float chainMultiplier(int previousHits) {
        return (float) Math.max(
                OathboundConfig.heartseekerMinimumDamageMultiplier(),
                1.0D - OathboundConfig.heartseekerChainDamageFalloff() * previousHits
        );
    }

    public static int getDesire(Player player) {
        MobEffectInstance desire = player.getEffect(EffectRegistry.DESIRE);
        return desire == null ? 0 : desire.getAmplifier() + 1;
    }

    /**
     * If the wielder is at maximum Desire, consumes it and returns true so the next arrow is empowered.
     */
    public static boolean consumeFullDesire(Player player) {
        if (getDesire(player) < OathboundConfig.heartseekerMaxDesireStacks()) {
            return false;
        }

        player.removeEffect(EffectRegistry.DESIRE);
        return true;
    }

    /**
     * Called when a Heartseeker arrow kills something: ends Craving, heals, and adds a Desire stack.
     */
    public static void onArrowKill(Player player) {
        player.removeEffect(EffectRegistry.CRAVING);

        float heal = (float) OathboundConfig.heartseekerHealOnKill();
        if (heal > 0.0F) {
            player.heal(heal);
        }

        int stacks = Math.min(OathboundConfig.heartseekerMaxDesireStacks(), getDesire(player) + 1);
        player.addEffect(new MobEffectInstance(
                EffectRegistry.DESIRE,
                OathboundConfig.heartseekerDesireDurationTicks(),
                stacks - 1,
                false,
                true,
                true
        ));
    }

    /**
     * Called when a Heartseeker arrow vanishes without killing anything.
     */
    public static void applyCraving(Player player) {
        int duration = OathboundConfig.heartseekerCravingDurationTicks();
        if (duration <= 0) {
            return;
        }

        player.addEffect(new MobEffectInstance(
                EffectRegistry.CRAVING,
                duration,
                0,
                false,
                true,
                true
        ));
    }
}
