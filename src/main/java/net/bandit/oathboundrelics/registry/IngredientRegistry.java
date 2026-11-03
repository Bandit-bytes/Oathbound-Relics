package net.bandit.oathboundrelics.registry;

import net.bandit.oathboundrelics.items.DormantRemnantIngredient;
import net.fabricmc.fabric.api.recipe.v1.ingredient.CustomIngredientSerializer;

public final class IngredientRegistry {

    private IngredientRegistry() {
    }

    public static void register() {
        CustomIngredientSerializer.register(DormantRemnantIngredient.SERIALIZER);
    }
}
