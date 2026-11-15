package net.bandit.oathboundrelics.client.renderer;

import net.bandit.oathboundrelics.entity.LustArrowEntity;
import net.minecraft.client.renderer.entity.ArrowRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;

/**
 * Placeholder look: the vanilla spectral arrow texture plus the crimson particle trail
 * spawned by the entity.
 */
public class LustArrowRenderer extends ArrowRenderer<LustArrowEntity> {

    private static final ResourceLocation TEXTURE =
            ResourceLocation.withDefaultNamespace("textures/entity/projectiles/spectral_arrow.png");

    public LustArrowRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public ResourceLocation getTextureLocation(LustArrowEntity entity) {
        return TEXTURE;
    }
}
