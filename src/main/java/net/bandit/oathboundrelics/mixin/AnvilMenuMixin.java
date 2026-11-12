package net.bandit.oathboundrelics.mixin;

import net.bandit.oathboundrelics.util.OathboundEnchantingUtil;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.ItemCombinerMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Vanilla's anvil clamps every merged enchantment to its max level, which strips
 * Branded over-enchants (e.g. a Protection V book applied to armor becomes Protection IV).
 * After the result is built, restore any over-level that one of the inputs already carried.
 */
@Mixin(AnvilMenu.class)
public abstract class AnvilMenuMixin extends ItemCombinerMenu {

    protected AnvilMenuMixin(MenuType<?> type, int containerId, Inventory playerInventory, ContainerLevelAccess access) {
        super(type, containerId, playerInventory, access);
    }

    @Inject(method = "createResult", at = @At("RETURN"))
    private void oathbound$keepOverleveledEnchantments(CallbackInfo ci) {
        ItemStack result = this.resultSlots.getItem(0);
        if (result.isEmpty()) {
            return;
        }

        if (OathboundEnchantingUtil.restoreOverLevels(result, this.inputSlots.getItem(0), this.inputSlots.getItem(1))) {
            this.broadcastChanges();
        }
    }
}
