package net.bandit.oathboundrelics.client;

import net.bandit.oathboundrelics.items.HeartseekerItem;
import net.bandit.oathboundrelics.registry.ItemRegistry;
import net.minecraft.client.renderer.item.ItemProperties;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;

/**
 * Registers the vanilla bow "pull" and "pulling" model predicates for Heartseeker,
 * scaled to Heartseeker's faster draw.
 */
public final class HeartseekerItemProperties {

    private HeartseekerItemProperties() {
    }

    public static void register() {
        Item bow = ItemRegistry.HEARTSEEKER.get();

        ItemProperties.register(bow, ResourceLocation.withDefaultNamespace("pull"), (stack, level, entity, seed) -> {
            if (entity == null || entity.getUseItem() != stack) {
                return 0.0F;
            }
            return (stack.getUseDuration(entity) - entity.getUseItemRemainingTicks()) / (float) HeartseekerItem.DRAW_TICKS;
        });

        ItemProperties.register(bow, ResourceLocation.withDefaultNamespace("pulling"), (stack, level, entity, seed) ->
                entity != null && entity.isUsingItem() && entity.getUseItem() == stack ? 1.0F : 0.0F
        );
    }
}
