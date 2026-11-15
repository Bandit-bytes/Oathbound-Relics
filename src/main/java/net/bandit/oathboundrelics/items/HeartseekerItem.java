package net.bandit.oathboundrelics.items;

import net.bandit.oathboundrelics.config.OathboundConfig;
import net.bandit.oathboundrelics.entity.LustArrowEntity;
import net.bandit.oathboundrelics.util.LustUtil;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.util.Unit;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Lust: Heartseeker. Draws twice as fast as a bow and, by default, needs no arrows.
 * A full draw fires a seeking arrow (three at maximum Desire). A partial draw fires an ordinary arrow.
 */
public class HeartseekerItem extends BowItem {

    /** Ticks for a full draw. A vanilla bow takes 20. */
    public static final int DRAW_TICKS = 10;
    /** Yaw degrees between arrows in a Rapture volley. */
    private static final float RAPTURE_SPREAD_DEGREES = 12.0F;

    /**
     * Set only for the duration of one shoot() call on the server thread, so createProjectile
     * knows whether this volley is empowered. Item instances are singletons, hence the reset.
     */
    private boolean volleyEmpowered;

    public HeartseekerItem(Properties properties) {
        super(properties);
    }

    private static boolean freeArrows() {
        return OathboundConfig.heartseekerFreeArrows();
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);

        if (!LustUtil.canUseHeartseeker(player)) {
            if (!level.isClientSide) {
                player.sendSystemMessage(
                        Component.translatable("message.oathboundrelics.heartseeker.locked")
                                .withStyle(ChatFormatting.RED)
                );
            }
            return InteractionResultHolder.fail(stack);
        }

        if (freeArrows()) {
            player.startUsingItem(hand);
            return InteractionResultHolder.consume(stack);
        }

        return super.use(level, player, hand);
    }

    @Override
    public void releaseUsing(ItemStack stack, Level level, LivingEntity entity, int timeLeft) {
        if (!(entity instanceof Player player)) {
            return;
        }

        // Scale the charge so DRAW_TICKS of drawing counts as a vanilla full draw.
        int charge = (this.getUseDuration(stack, entity) - timeLeft) * 20 / DRAW_TICKS;
        float power = getPowerForTime(charge);
        if (power < 0.1F) {
            return;
        }

        boolean fullDraw = power >= 1.0F;
        boolean empowered = fullDraw
                && !level.isClientSide
                && LustUtil.canUseHeartseeker(player)
                && LustUtil.consumeFullDesire(player);
        int count = empowered ? OathboundConfig.heartseekerEmpoweredArrowCount() : 1;

        List<ItemStack> projectiles = collectProjectiles(stack, player, count);
        if (projectiles.isEmpty()) {
            return;
        }

        if (level instanceof ServerLevel serverLevel) {
            this.volleyEmpowered = empowered;
            try {
                this.shoot(serverLevel, player, player.getUsedItemHand(), stack, projectiles, power * 3.0F, 1.0F, fullDraw, null);
            } finally {
                this.volleyEmpowered = false;
            }
        }

        level.playSound(
                null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.ARROW_SHOOT, SoundSource.PLAYERS,
                1.0F, 1.0F / (level.getRandom().nextFloat() * 0.4F + 1.2F) + power * 0.5F
        );
        if (empowered) {
            level.playSound(
                    null, player.getX(), player.getY(), player.getZ(),
                    SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS,
                    1.0F, 1.3F
            );
        }
        player.awardStat(Stats.ITEM_USED.get(this));
    }

    /**
     * With free arrows, fires phantom copies of whatever arrow the player carries (or a plain
     * arrow) that can't be picked up. Otherwise uses and consumes real ammo like a vanilla bow.
     */
    private List<ItemStack> collectProjectiles(ItemStack bow, Player player, int count) {
        ItemStack ammo = player.getProjectile(bow);

        if (freeArrows()) {
            ItemStack phantom = ammo.isEmpty() ? new ItemStack(Items.ARROW) : ammo.copyWithCount(1);
            phantom.set(DataComponents.INTANGIBLE_PROJECTILE, Unit.INSTANCE);

            List<ItemStack> list = new ArrayList<>(count);
            for (int i = 0; i < count; i++) {
                list.add(phantom.copy());
            }
            return list;
        }

        if (ammo.isEmpty()) {
            return List.of();
        }

        List<ItemStack> drawn = new ArrayList<>(draw(bow, ammo, player));
        if (drawn.isEmpty()) {
            return drawn;
        }
        // Extra Rapture arrows are free even when real ammo is in use.
        while (drawn.size() < count) {
            ItemStack extra = drawn.get(0).copyWithCount(1);
            extra.set(DataComponents.INTANGIBLE_PROJECTILE, Unit.INSTANCE);
            drawn.add(extra);
        }
        return drawn;
    }

    @Override
    protected Projectile createProjectile(Level level, LivingEntity shooter, ItemStack weapon, ItemStack ammo, boolean isCrit) {
        // isCrit is true only for a full draw.
        if (isCrit && shooter instanceof Player player && LustUtil.canUseHeartseeker(player)) {
            return new LustArrowEntity(level, shooter, ammo.copyWithCount(1), weapon, this.volleyEmpowered);
        }

        return super.createProjectile(level, shooter, weapon, ammo, isCrit);
    }

    @Override
    protected void shootProjectile(
            LivingEntity shooter, Projectile projectile, int index, float velocity, float inaccuracy, float angle, @Nullable LivingEntity target
    ) {
        // Fan a Rapture volley out: 0, -12, +12, -24, +24 ...
        float spread = index == 0 ? 0.0F : ((index % 2 == 1) ? -1.0F : 1.0F) * ((index + 1) / 2) * RAPTURE_SPREAD_DEGREES;
        super.shootProjectile(shooter, projectile, index, velocity, inaccuracy, angle + spread, target);
    }

    @Override
    public int getEnchantmentValue() {
        return 15;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.oathboundrelics.heartseeker.flavor")
                .withStyle(ChatFormatting.DARK_PURPLE, ChatFormatting.ITALIC));

        tooltip.add(Component.empty());
        tooltip.add(Component.translatable("tooltip.oathboundrelics.heartseeker.requires_brand")
                .withStyle(ChatFormatting.RED));
        tooltip.add(Component.translatable("tooltip.oathboundrelics.heartseeker.requires_time")
                .withStyle(ChatFormatting.GRAY));

        tooltip.add(Component.empty());
        tooltip.add(Component.translatable("tooltip.oathboundrelics.heartseeker.ability_header")
                .withStyle(ChatFormatting.GOLD));
        tooltip.add(Component.translatable(
                "tooltip.oathboundrelics.heartseeker.ability_seek",
                OathboundConfig.heartseekerMaxHits()
        ).withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable(
                "tooltip.oathboundrelics.heartseeker.ability_insatiable",
                OathboundConfig.heartseekerMaxChainHits()
        ).withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.oathboundrelics.heartseeker.ability_obsession")
                .withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable(
                "tooltip.oathboundrelics.heartseeker.ability_lifesteal",
                Math.round(OathboundConfig.heartseekerLifestealPercent() * 100.0D)
        ).withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.oathboundrelics.heartseeker.ability_enthrall")
                .withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable(
                freeArrows()
                        ? "tooltip.oathboundrelics.heartseeker.ability_draw_free"
                        : "tooltip.oathboundrelics.heartseeker.ability_draw"
        ).withStyle(ChatFormatting.GRAY));

        tooltip.add(Component.empty());
        tooltip.add(Component.translatable("tooltip.oathboundrelics.heartseeker.desire_header")
                .withStyle(ChatFormatting.GOLD));
        tooltip.add(Component.translatable("tooltip.oathboundrelics.heartseeker.desire_kill")
                .withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable(
                "tooltip.oathboundrelics.heartseeker.desire_full",
                OathboundConfig.heartseekerMaxDesireStacks(),
                OathboundConfig.heartseekerEmpoweredArrowCount()
        ).withStyle(ChatFormatting.GRAY));

        tooltip.add(Component.empty());
        tooltip.add(Component.translatable("tooltip.oathboundrelics.heartseeker.drawback_header")
                .withStyle(ChatFormatting.DARK_RED));
        tooltip.add(Component.translatable("tooltip.oathboundrelics.heartseeker.drawback_wander")
                .withStyle(ChatFormatting.DARK_RED));
        tooltip.add(Component.translatable("tooltip.oathboundrelics.heartseeker.drawback_craving")
                .withStyle(ChatFormatting.DARK_RED));
    }
}
