package net.bandit.oathboundrelics.mixin;

import net.bandit.oathboundrelics.registry.EffectRegistry;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Enthralled mobs (from Lust: Heartseeker) refuse to target players.
 */
@Mixin(Mob.class)
public abstract class MobTargetMixin {

    @Inject(method = "setTarget", at = @At("HEAD"), cancellable = true)
    private void oathbound$enthralledIgnorePlayers(@Nullable LivingEntity target, CallbackInfo ci) {
        if (target instanceof Player && ((Mob) (Object) this).hasEffect(EffectRegistry.ENTHRALLED)) {
            ci.cancel();
        }
    }
}
