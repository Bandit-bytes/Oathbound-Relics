package net.bandit.oathboundrelics.entity;

import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import net.bandit.oathboundrelics.config.OathboundConfig;
import net.bandit.oathboundrelics.registry.EntityRegistry;
import net.bandit.oathboundrelics.util.LustUtil;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.boss.EnderDragonPart;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

/**
 * Lust: Heartseeker's fully drawn arrow. It flies without gravity, steers toward the nearest
 * valid target, and after each hit picks a new one it has not hit yet.
 * <ul>
 *   <li>Insatiable: each kill adds a hit (up to a cap) and extends its lifetime.</li>
 *   <li>Obsession: with no new hostile in range, it loops back and strikes its last target again.</li>
 *   <li>Every hit heals the wielder for part of the damage dealt; survivors are Enthralled.</li>
 * </ul>
 * It vanishes after its hit budget, its lifetime, touching a block, or running out of targets.
 * If it vanishes without a kill, the wielder gains Craving.
 */
public class LustArrowEntity extends AbstractArrow {

    private static final EntityDataAccessor<Integer> TARGET_ID =
            SynchedEntityData.defineId(LustArrowEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Float> SPEED =
            SynchedEntityData.defineId(LustArrowEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> TURN_RATE =
            SynchedEntityData.defineId(LustArrowEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Boolean> EMPOWERED =
            SynchedEntityData.defineId(LustArrowEntity.class, EntityDataSerializers.BOOLEAN);

    /** Ticks the arrow flies straight before it starts seeking, so aim still matters. */
    private static final int SEEK_DELAY_TICKS = 2;
    /** Vanilla arrow speed on a full bow draw, used to scale enchantment damage bonuses. */
    private static final float FULL_DRAW_SPEED = 3.0F;
    /** Within this distance the arrow turns straight at its target instead of curving. */
    private static final double DIRECT_AIM_DISTANCE = 3.0D;
    /** Ticks before Obsession lets the arrow strike the same target again (outlasts hurt i-frames). */
    private static final int REHIT_DELAY_TICKS = 10;

    private static final DustParticleOptions TRAIL =
            new DustParticleOptions(new Vector3f(0.85F, 0.08F, 0.25F), 1.0F);
    private static final DustParticleOptions EMPOWERED_TRAIL =
            new DustParticleOptions(new Vector3f(1.0F, 0.45F, 0.70F), 1.3F);

    private final IntOpenHashSet hitIds = new IntOpenHashSet();
    private int hits;
    private int maxHits = 1;
    private int age;
    private boolean killedAny;
    private boolean finished;

    // Obsession bookkeeping. Not saved: entity ids are not stable across reloads.
    private int lastTargetId = -1;
    private int lastHitEntityId = -1;
    private int lastHitAge;

    public LustArrowEntity(EntityType<? extends LustArrowEntity> type, Level level) {
        super(type, level);
        this.setNoGravity(true);
    }

    public LustArrowEntity(Level level, LivingEntity owner, ItemStack ammo, ItemStack weapon, boolean empowered) {
        super(EntityRegistry.LUST_ARROW.get(), owner, level, ammo, weapon);
        this.setNoGravity(true);
        this.pickup = Pickup.DISALLOWED;

        double speed = OathboundConfig.heartseekerArrowSpeed();
        int hitBudget = OathboundConfig.heartseekerMaxHits();
        if (empowered) {
            speed *= OathboundConfig.heartseekerEmpoweredSpeedMultiplier();
            hitBudget += OathboundConfig.heartseekerEmpoweredBonusHits();
        }

        this.maxHits = hitBudget;
        this.entityData.set(SPEED, (float) speed);
        this.entityData.set(TURN_RATE, (float) OathboundConfig.heartseekerTurnRate());
        this.entityData.set(EMPOWERED, empowered);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(TARGET_ID, -1);
        builder.define(SPEED, 1.5F);
        builder.define(TURN_RATE, 0.3F);
        builder.define(EMPOWERED, false);
    }

    @Override
    protected ItemStack getDefaultPickupItem() {
        return new ItemStack(Items.ARROW);
    }

    public boolean isEmpowered() {
        return this.entityData.get(EMPOWERED);
    }

    @Nullable
    private Entity getTarget() {
        int id = this.entityData.get(TARGET_ID);
        return id < 0 ? null : this.level().getEntity(id);
    }

    private void setTarget(@Nullable Entity target) {
        this.entityData.set(TARGET_ID, target == null ? -1 : target.getId());
    }

    private double maxChaseDistanceSqr() {
        double range = OathboundConfig.heartseekerSeekRange() * 1.5D;
        return range * range;
    }

    private boolean isValidTarget(@Nullable Entity target) {
        return target instanceof LivingEntity living
                && living.isAlive()
                && !this.hitIds.contains(living.getId())
                && living.distanceToSqr(this) <= maxChaseDistanceSqr();
    }

    @Override
    public void tick() {
        if (!this.level().isClientSide) {
            updateTarget();
        }

        if (!this.isRemoved()) {
            steer();
            super.tick();
        }

        if (this.level().isClientSide) {
            spawnTrail();
            return;
        }

        this.age++;
        if (!this.isRemoved() && this.age >= OathboundConfig.heartseekerLifetimeTicks()) {
            finish();
        }
    }

    private void updateTarget() {
        if (this.age < SEEK_DELAY_TICKS || isValidTarget(getTarget())) {
            return;
        }

        // Searching is the expensive part; while idle before the first hit, look every other tick.
        if (this.hits == 0 && getTarget() == null && (this.age & 1) == 1) {
            return;
        }

        Entity owner = this.getOwner();

        // 1. A new hostile.
        LivingEntity next = LustUtil.findTarget(this, owner, this.hitIds, false);

        // 2. Obsession: circle back to the last target. While waiting out the re-hit delay,
        //    keep flying rather than wandering off to something passive.
        if (next == null) {
            if (isObsessionPending()) {
                setTarget(null);
                return;
            }
            next = takeObsessionTarget();
        }

        // 3. Wandering Eye: anything else alive.
        if (next == null && OathboundConfig.heartseekerWanderingEye()) {
            next = LustUtil.findTarget(this, owner, this.hitIds, true);
        }

        setTarget(next);

        if (next == null && this.hits > 0) {
            finish();
        }
    }

    @Nullable
    private LivingEntity getObsessionTarget() {
        if (!OathboundConfig.heartseekerObsession() || this.lastTargetId < 0) {
            return null;
        }

        Entity entity = this.level().getEntity(this.lastTargetId);
        if (entity instanceof LivingEntity living
                && living.isAlive()
                && living.distanceToSqr(this) <= maxChaseDistanceSqr()) {
            return living;
        }
        return null;
    }

    private boolean isObsessionPending() {
        return getObsessionTarget() != null && this.age - this.lastHitAge < REHIT_DELAY_TICKS;
    }

    @Nullable
    private LivingEntity takeObsessionTarget() {
        LivingEntity last = getObsessionTarget();
        if (last == null || !LustUtil.hasLineOfSightTo(this, last)) {
            return null;
        }

        this.hitIds.remove(last.getId());
        this.hitIds.remove(this.lastHitEntityId);
        return last;
    }

    /**
     * Runs on both sides with the synced target so the client's prediction matches the server.
     */
    private void steer() {
        Vec3 velocity = this.getDeltaMovement();
        double speed = this.entityData.get(SPEED);
        Entity target = getTarget();

        if (target == null || !target.isAlive()) {
            if (velocity.lengthSqr() > 1.0E-6) {
                this.setDeltaMovement(velocity.normalize().scale(speed));
            }
            return;
        }

        Vec3 toTarget = LustUtil.aimPoint(target).subtract(this.position());
        double distance = toTarget.length();
        if (distance < 1.0E-4) {
            return;
        }

        Vec3 desired = toTarget.scale(1.0D / distance);
        double turn = distance < DIRECT_AIM_DISTANCE ? 1.0D : this.entityData.get(TURN_RATE);
        Vec3 direction = velocity.lengthSqr() > 1.0E-6
                ? velocity.normalize().lerp(desired, turn)
                : desired;

        if (direction.lengthSqr() < 1.0E-6) {
            direction = desired;
        }

        this.setDeltaMovement(direction.normalize().scale(speed));
        this.hasImpulse = true;
    }

    @Override
    protected boolean canHitEntity(Entity entity) {
        if (!super.canHitEntity(entity) || this.hitIds.contains(entity.getId())) {
            return false;
        }

        LivingEntity living = resolveLiving(entity);
        return living != null
                && !this.hitIds.contains(living.getId())
                && !LustUtil.isProtected(this.getOwner(), living);
    }

    @Nullable
    private static LivingEntity resolveLiving(Entity entity) {
        if (entity instanceof LivingEntity living) {
            return living;
        }
        if (entity instanceof EnderDragonPart part) {
            return part.parentMob;
        }
        return null;
    }

    @Override
    protected void onHitEntity(EntityHitResult result) {
        if (this.level().isClientSide || this.finished) {
            return;
        }

        Entity hitEntity = result.getEntity();
        LivingEntity target = resolveLiving(hitEntity);
        if (target == null) {
            return;
        }

        this.hitIds.add(hitEntity.getId());
        this.hitIds.add(target.getId());

        Entity owner = this.getOwner();
        Player player = owner instanceof Player p ? p : null;
        DamageSource source = this.damageSources().arrow(this, owner != null ? owner : this);

        float damage = (float) OathboundConfig.heartseekerArrowDamage();
        if (this.getWeaponItem() != null && this.level() instanceof ServerLevel serverLevel) {
            // Bow enchantments like Power add to an arrow's base damage, which vanilla then
            // multiplies by the arrow's speed (about x3 on a full draw). Scale the bonus the same way.
            float enchantBonus = EnchantmentHelper.modifyDamage(serverLevel, this.getWeaponItem(), target, source, 0.0F);
            damage += enchantBonus * FULL_DRAW_SPEED;
        }
        damage *= LustUtil.chainMultiplier(this.hits);

        if (owner instanceof LivingEntity livingOwner) {
            livingOwner.setLastHurtMob(target);
        }

        if (this.isOnFire() && target.getType() != EntityType.ENDERMAN) {
            target.igniteForSeconds(5.0F);
        }

        this.hits++;
        float healthBefore = target.getHealth();

        if (hitEntity.hurt(source, damage)) {
            this.doKnockback(target, source);
            if (this.level() instanceof ServerLevel serverLevel) {
                EnchantmentHelper.doPostAttackEffectsWithItemSource(serverLevel, target, source, this.getWeaponItem());
            }

            float dealt = Math.max(0.0F, healthBefore - target.getHealth());

            if (!target.isAlive()) {
                onKill(player);
            } else {
                LustUtil.enthrall(target);
            }

            // After onKill, which clears Craving, so the killing blow's lifesteal isn't halved.
            if (player != null && dealt > 0.0F) {
                float lifesteal = (float) (dealt * OathboundConfig.heartseekerLifestealPercent());
                if (lifesteal > 0.0F) {
                    player.heal(lifesteal);
                }
            }
        }

        this.lastTargetId = target.isAlive() ? target.getId() : -1;
        this.lastHitEntityId = hitEntity.getId();
        this.lastHitAge = this.age;

        spawnHitEffects(target);

        if (this.hits >= this.maxHits) {
            finish();
            return;
        }

        // Pick the next target on the next tick.
        setTarget(null);
    }

    private void onKill(@Nullable Player player) {
        this.killedAny = true;

        if (player != null) {
            LustUtil.onArrowKill(player);
        }

        if (OathboundConfig.heartseekerKillsExtendChain()) {
            if (this.maxHits < OathboundConfig.heartseekerMaxChainHits()) {
                this.maxHits++;
            }
            this.age = Math.max(0, this.age - OathboundConfig.heartseekerKillLifetimeRefundTicks());
        }
    }

    @Override
    protected void onHitBlock(BlockHitResult result) {
        if (this.level().isClientSide) {
            return;
        }

        BlockState state = this.level().getBlockState(result.getBlockPos());
        state.onProjectileHit(this.level(), state, result, this);
        finish();
    }

    /**
     * Ends the arrow. Applies Craving to the wielder if it never killed anything.
     */
    private void finish() {
        if (this.finished || this.level().isClientSide) {
            return;
        }
        this.finished = true;

        if (!this.killedAny && this.getOwner() instanceof Player player && LustUtil.canUseHeartseeker(player)) {
            LustUtil.applyCraving(player);
        }

        if (this.level() instanceof ServerLevel serverLevel) {
            serverLevel.sendParticles(
                    isEmpowered() ? EMPOWERED_TRAIL : TRAIL,
                    this.getX(), this.getY(), this.getZ(),
                    14, 0.2D, 0.2D, 0.2D, 0.02D
            );
            if (this.killedAny) {
                serverLevel.sendParticles(
                        ParticleTypes.HEART,
                        this.getX(), this.getY(), this.getZ(),
                        3, 0.2D, 0.2D, 0.2D, 0.0D
                );
            }
            serverLevel.playSound(
                    null, this.getX(), this.getY(), this.getZ(),
                    SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS,
                    0.8F, this.killedAny ? 1.4F : 0.6F
            );
        }

        this.discard();
    }

    private void spawnHitEffects(LivingEntity target) {
        if (!(this.level() instanceof ServerLevel serverLevel)) {
            return;
        }

        serverLevel.sendParticles(
                ParticleTypes.DAMAGE_INDICATOR,
                target.getX(), target.getY(0.6D), target.getZ(),
                4, 0.25D, 0.25D, 0.25D, 0.1D
        );
        serverLevel.sendParticles(
                isEmpowered() ? EMPOWERED_TRAIL : TRAIL,
                target.getX(), target.getY(0.5D), target.getZ(),
                10, 0.3D, 0.4D, 0.3D, 0.02D
        );
        serverLevel.playSound(
                null, target.getX(), target.getY(), target.getZ(),
                SoundEvents.ARROW_HIT, SoundSource.PLAYERS,
                1.0F, Math.min(2.0F, 1.1F + this.hits * 0.08F)
        );
    }

    private void spawnTrail() {
        Vec3 velocity = this.getDeltaMovement();
        DustParticleOptions particle = isEmpowered() ? EMPOWERED_TRAIL : TRAIL;
        for (int i = 0; i < 2; i++) {
            double step = i / 2.0D;
            this.level().addParticle(
                    particle,
                    this.getX() - velocity.x * step,
                    this.getY() - velocity.y * step,
                    this.getZ() - velocity.z * step,
                    0.0D, 0.0D, 0.0D
            );
        }
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putInt("LustHits", this.hits);
        tag.putInt("LustMaxHits", this.maxHits);
        tag.putInt("LustAge", this.age);
        tag.putBoolean("LustKilled", this.killedAny);
        tag.putBoolean("LustEmpowered", isEmpowered());
        tag.putFloat("LustSpeed", this.entityData.get(SPEED));
        tag.putFloat("LustTurnRate", this.entityData.get(TURN_RATE));
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        this.hits = tag.getInt("LustHits");
        this.maxHits = Math.max(1, tag.getInt("LustMaxHits"));
        this.age = tag.getInt("LustAge");
        this.killedAny = tag.getBoolean("LustKilled");
        this.entityData.set(EMPOWERED, tag.getBoolean("LustEmpowered"));
        if (tag.contains("LustSpeed")) {
            this.entityData.set(SPEED, tag.getFloat("LustSpeed"));
        }
        if (tag.contains("LustTurnRate")) {
            this.entityData.set(TURN_RATE, tag.getFloat("LustTurnRate"));
        }
        // Entity ids are not stable across reloads, so previously hit targets are forgotten.
        this.hitIds.clear();
        this.setNoGravity(true);
    }
}
