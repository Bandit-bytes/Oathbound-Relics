package net.bandit.oathboundrelics.mixin;

import net.bandit.oathboundrelics.events.FabricEventBridge;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.bandit.oathboundrelics.fabricbridge.events.entity.living.LivingDamageEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LivingEntity.class)
public abstract class LivingEntityEventMixin {
    @Shadow
    protected Player lastHurtByPlayer;

    @Unique
    private float oathbound$lastAdjustedDamage;

    @ModifyVariable(method = "hurt", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private float oathbound$modifyDamageAmount(float amount, DamageSource source) {
        LivingEntity self = (LivingEntity) (Object) this;
        float incoming = FabricEventBridge.fireIncomingDamage(self, source, amount);
        LivingDamageEvent.Pre pre = FabricEventBridge.fireLivingDamagePre(self, source, incoming);
        this.oathbound$lastAdjustedDamage = pre.isCanceled() ? 0.0F : pre.getNewDamage();
        return this.oathbound$lastAdjustedDamage;
    }

    @Inject(method = "hurt", at = @At("RETURN"))
    private void oathbound$afterDamage(DamageSource source, float amount, CallbackInfoReturnable<Boolean> cir) {
        if (cir.getReturnValue()) {
            FabricEventBridge.fireLivingDamagePost((LivingEntity) (Object) this, source, this.oathbound$lastAdjustedDamage);
        }
    }

    @ModifyVariable(method = "heal", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private float oathbound$modifyHealAmount(float amount) {
        return FabricEventBridge.fireLivingHeal((LivingEntity) (Object) this, amount);
    }

    @ModifyArg(
            method = "dropExperience",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/ExperienceOrb;award(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/phys/Vec3;I)V"
            ),
            index = 2
    )
    private int oathbound$modifyDroppedExperience(int experience) {
        return FabricEventBridge.fireExperienceDrop((LivingEntity) (Object) this, this.lastHurtByPlayer, experience);
    }
}
