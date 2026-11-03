package net.bandit.oathboundrelics.compat;

import dev.emi.trinkets.api.Trinket;
import dev.emi.trinkets.api.TrinketsApi;
import net.bandit.oathboundrelics.OathboundRelicsMod;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Item;

public final class TrinketsCompatRegistry {

    private TrinketsCompatRegistry() {
    }

    // An unregistered item falls back to Trinkets' default behaviour and loses its equip rules and attributes.
    public static void register() {
        for (Item item : BuiltInRegistries.ITEM) {
            if (item instanceof Trinket trinket
                    && BuiltInRegistries.ITEM.getKey(item).getNamespace().equals(OathboundRelicsMod.MOD_ID)) {
                TrinketsApi.registerTrinket(item, trinket);
            }
        }
    }
}
