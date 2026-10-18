package dev.portablestereo.network;

import dev.portablestereo.PortableStereo;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.item.JukeboxSong;

/**
 * Start playing {@code song}, {@code offsetTicks} into the track.
 * Source is the entity {@code entityId}, or the block at {@code pos} when entityId is -1.
 */
public record PlayStereoPayload(int entityId, BlockPos pos, Holder<JukeboxSong> song, int offsetTicks)
        implements CustomPacketPayload {
    public static final Type<PlayStereoPayload> TYPE = new Type<>(PortableStereo.id("play"));

    public static final StreamCodec<RegistryFriendlyByteBuf, PlayStereoPayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, PlayStereoPayload::entityId,
            BlockPos.STREAM_CODEC, PlayStereoPayload::pos,
            JukeboxSong.STREAM_CODEC, PlayStereoPayload::song,
            ByteBufCodecs.VAR_INT, PlayStereoPayload::offsetTicks,
            PlayStereoPayload::new);

    public String key() {
        return StopStereoPayload.key(entityId, pos);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
