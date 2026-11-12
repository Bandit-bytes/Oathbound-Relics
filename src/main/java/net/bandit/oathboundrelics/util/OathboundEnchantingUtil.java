package net.bandit.oathboundrelics.util;

import net.bandit.oathboundrelics.config.OathboundConfig;
import net.minecraft.core.Holder;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.EnchantmentInstance;
import net.minecraft.world.item.enchantment.ItemEnchantments;

import java.util.ArrayList;
import java.util.List;

public final class OathboundEnchantingUtil {

    private static final int OVER_ENCHANT_THRESHOLD = 30;

    private OathboundEnchantingUtil() {
    }

    public static List<EnchantmentInstance> applySingleOvercapRoll(
            List<EnchantmentInstance> original,
            int slot,
            int enchantingPower,
            int enchantmentSeed
    ) {
        if (!OathboundConfig.enableEnchantingBlessing()) {
            return original;
        }

        if (original == null || original.isEmpty()) {
            return original;
        }

        int effectivePower = enchantingPower;
        if (effectivePower <= OVER_ENCHANT_THRESHOLD) {
            return original;
        }

        List<EnchantmentInstance> modified = new ArrayList<>(original.size());

        for (int index = 0; index < original.size(); index++) {
            EnchantmentInstance inst = original.get(index);

            int newLevel = maybeOvercap(
                    inst.enchantment,
                    inst.level,
                    effectivePower,
                    buildSeed(inst.enchantment, slot, effectivePower, enchantmentSeed, index)
            );

            modified.add(new EnchantmentInstance(inst.enchantment, newLevel));
        }

        return modified;
    }

    /**
     * Restores over-max enchantment levels that the anvil clamped away. A result enchantment
     * is raised only up to the highest level already present on one of the inputs, so
     * combining never creates a new over-level (V + V stays V).
     *
     * @return true if the result stack was changed
     */
    public static boolean restoreOverLevels(ItemStack result, ItemStack left, ItemStack right) {
        ItemEnchantments resultEnchantments = EnchantmentHelper.getEnchantmentsForCrafting(result);
        if (resultEnchantments.isEmpty()) {
            return false;
        }

        ItemEnchantments leftEnchantments = EnchantmentHelper.getEnchantmentsForCrafting(left);
        ItemEnchantments rightEnchantments = EnchantmentHelper.getEnchantmentsForCrafting(right);
        ItemEnchantments.Mutable restored = new ItemEnchantments.Mutable(resultEnchantments);
        boolean changed = false;

        for (Holder<Enchantment> enchantment : resultEnchantments.keySet()) {
            int inputLevel = Math.max(leftEnchantments.getLevel(enchantment), rightEnchantments.getLevel(enchantment));
            if (inputLevel > enchantment.value().getMaxLevel()
                    && resultEnchantments.getLevel(enchantment) < inputLevel) {
                restored.set(enchantment, inputLevel);
                changed = true;
            }
        }

        if (changed) {
            EnchantmentHelper.setEnchantments(result, restored.toImmutable());
        }
        return changed;
    }

    private static int maybeOvercap(
            Holder<Enchantment> enchantment,
            int rolledLevel,
            int enchantingPower,
            long seed
    ) {
        int vanillaMax = enchantment.value().getMaxLevel();

        if (rolledLevel < vanillaMax) {
            return rolledLevel;
        }

        RandomSource random = RandomSource.create(seed);

        if (random.nextFloat() < getOverEnchantChance(enchantingPower)) {
            return vanillaMax + 1;
        }

        return vanillaMax;
    }

    private static float getOverEnchantChance(int enchantingPower) {
        return Math.min(0.90F, Math.max(0.0F, (enchantingPower - OVER_ENCHANT_THRESHOLD) * 0.10F));
    }

    private static long buildSeed(
            Holder<Enchantment> enchantment,
            int slot,
            int enchantingPower,
            int enchantmentSeed,
            int index
    ) {
        int enchantHash = enchantment.unwrapKey()
                .map(key -> key.location().hashCode())
                .orElse(0);

        long seed = 1469598103934665603L;
        seed = seed * 31L + enchantmentSeed;
        seed = seed * 31L + slot;
        seed = seed * 31L + enchantingPower;
        seed = seed * 31L + index;
        seed = seed * 31L + enchantHash;
        return seed;
    }
}