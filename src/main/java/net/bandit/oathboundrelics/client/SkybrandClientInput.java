package net.bandit.oathboundrelics.client;

import net.bandit.oathboundrelics.network.SkybrandGlidePayload;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;

public final class SkybrandClientInput {

    private static Boolean lastJumpHeld = null;

    private SkybrandClientInput() {
    }

    public static void register() {
        ClientTickEvents.END_CLIENT_TICK.register(SkybrandClientInput::onClientTick);
    }

    private static void onClientTick(Minecraft minecraft) {
        if (minecraft.player == null) {
            lastJumpHeld = null;
            return;
        }

        boolean jumpHeld = minecraft.options.keyJump.isDown();

        if ((lastJumpHeld == null || jumpHeld != lastJumpHeld)
                && ClientPlayNetworking.canSend(SkybrandGlidePayload.TYPE)) {
            lastJumpHeld = jumpHeld;
            ClientPlayNetworking.send(new SkybrandGlidePayload(jumpHeld));
        }
    }
}
