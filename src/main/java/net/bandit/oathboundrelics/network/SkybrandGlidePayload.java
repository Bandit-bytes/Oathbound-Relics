package net.bandit.oathboundrelics.network;

import io.netty.buffer.ByteBuf;
import net.bandit.oathboundrelics.OathboundRelicsMod;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record SkybrandGlidePayload(boolean gliding) implements CustomPacketPayload {

    public static final Type<SkybrandGlidePayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(
                    OathboundRelicsMod.MOD_ID,
                    "skybrand_glide"
            ));

    public static final StreamCodec<ByteBuf, SkybrandGlidePayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.BOOL,
                    SkybrandGlidePayload::gliding,
                    SkybrandGlidePayload::new
            );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
