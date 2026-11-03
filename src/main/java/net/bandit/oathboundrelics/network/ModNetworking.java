package net.bandit.oathboundrelics.network;

import net.bandit.oathboundrelics.util.TitansRemnantUtil;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

public final class ModNetworking {

    private ModNetworking() {
    }

    public static void register() {
        PayloadTypeRegistry.playC2S().register(SkybrandGlidePayload.TYPE, SkybrandGlidePayload.STREAM_CODEC);

        ServerPlayNetworking.registerGlobalReceiver(
                SkybrandGlidePayload.TYPE,
                (payload, context) -> TitansRemnantUtil.setSkybrandGliding(context.player(), payload.gliding())
        );
    }
}
