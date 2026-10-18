package dev.portablestereo;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * Where a stereo is in its song, measured with the world's game clock.
 *   playing: value = the game tick the song would have started at
 *   paused:  value = how many ticks into the song it was paused
 */
public record Playback(boolean paused, long value) {
    public static final Codec<Playback> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.BOOL.fieldOf("paused").forGetter(Playback::paused),
            Codec.LONG.fieldOf("value").forGetter(Playback::value)
    ).apply(i, Playback::new));

    public static final StreamCodec<RegistryFriendlyByteBuf, Playback> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.BOOL, Playback::paused,
            ByteBufCodecs.VAR_LONG, Playback::value,
            Playback::new);

    public static Playback start(long now) {
        return new Playback(false, now);
    }

    public long elapsed(long now) {
        return paused ? value : Math.max(0, now - value);
    }

    public Playback pause(long now) {
        return paused ? this : new Playback(true, elapsed(now));
    }

    public Playback resume(long now) {
        return paused ? new Playback(false, now - value) : this;
    }
}
