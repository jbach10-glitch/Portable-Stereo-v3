package dev.portablestereo.network;

import dev.portablestereo.PortableStereo;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Stop the stereo on entity {@code entityId}, or at block {@code pos} when entityId is -1. */
public record StopStereoPayload(int entityId, BlockPos pos) implements CustomPacketPayload {
    public static final Type<StopStereoPayload> TYPE = new Type<>(PortableStereo.id("stop"));

    public static final StreamCodec<RegistryFriendlyByteBuf, StopStereoPayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, StopStereoPayload::entityId,
            BlockPos.STREAM_CODEC, StopStereoPayload::pos,
            StopStereoPayload::new);

    public static String key(int entityId, BlockPos pos) {
        return entityId >= 0 ? "e" + entityId : "b" + pos.asLong();
    }

    public String key() {
        return key(entityId, pos);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
