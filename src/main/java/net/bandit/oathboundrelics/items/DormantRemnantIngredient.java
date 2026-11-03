package net.bandit.oathboundrelics.items;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.bandit.oathboundrelics.OathboundRelicsMod;
import net.bandit.oathboundrelics.curio.items.TitansRemnantItem;
import net.bandit.oathboundrelics.util.TitansRemnantUtil;
import net.fabricmc.fabric.api.recipe.v1.ingredient.CustomIngredient;
import net.fabricmc.fabric.api.recipe.v1.ingredient.CustomIngredientSerializer;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;

import java.util.List;

// Matches the base ingredient, but only Titan's Remnants that are still Dormant.
public record DormantRemnantIngredient(Ingredient base) implements CustomIngredient {

    public static final CustomIngredientSerializer<DormantRemnantIngredient> SERIALIZER = new Serializer();

    @Override
    public boolean test(ItemStack stack) {
        return base.test(stack)
                && stack.getItem() instanceof TitansRemnantItem
                && TitansRemnantUtil.getStage(stack) == TitanRemnantStage.DORMANT;
    }

    @Override
    public List<ItemStack> getMatchingStacks() {
        return List.of(base.getItems());
    }

    @Override
    public boolean requiresTesting() {
        return true;
    }

    @Override
    public CustomIngredientSerializer<?> getSerializer() {
        return SERIALIZER;
    }

    private static final class Serializer implements CustomIngredientSerializer<DormantRemnantIngredient> {
        private static final ResourceLocation ID =
                ResourceLocation.fromNamespaceAndPath(OathboundRelicsMod.MOD_ID, "dormant_remnant");

        private static final MapCodec<DormantRemnantIngredient> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
                Ingredient.CODEC_NONEMPTY.fieldOf("base").forGetter(DormantRemnantIngredient::base)
        ).apply(instance, DormantRemnantIngredient::new));

        private static final StreamCodec<RegistryFriendlyByteBuf, DormantRemnantIngredient> STREAM_CODEC =
                Ingredient.CONTENTS_STREAM_CODEC.map(DormantRemnantIngredient::new, DormantRemnantIngredient::base);

        @Override
        public ResourceLocation getIdentifier() {
            return ID;
        }

        @Override
        public MapCodec<DormantRemnantIngredient> getCodec(boolean allowEmpty) {
            return CODEC;
        }

        @Override
        public StreamCodec<RegistryFriendlyByteBuf, DormantRemnantIngredient> getPacketCodec() {
            return STREAM_CODEC;
        }
    }
}
