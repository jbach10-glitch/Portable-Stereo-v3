package dev.portablestereo.client;

import java.util.concurrent.CompletableFuture;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.AudioStream;
import net.minecraft.client.sounds.SoundBufferLibrary;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;

/**
 * A record that sounds exactly like a jukebox (same volume, 64-block falloff, Jukebox volume slider),
 * but can follow a player around and can start partway through the song.
 */
public class StereoSoundInstance extends AbstractTickableSoundInstance {
    private final int entityId;      // -1 = placed block, stays put
    private final double offsetSeconds;
    private int ticksMissing;

    public StereoSoundInstance(SoundEvent sound, int entityId, BlockPos pos, int offsetTicks) {
        super(sound, SoundSource.RECORDS, SoundInstance.createUnseededRandom());
        this.entityId = entityId;
        this.offsetSeconds = offsetTicks / 20.0;
        this.volume = 4.0F;
        this.pitch = 1.0F;
        this.looping = false;
        this.delay = 0;
        this.attenuation = SoundInstance.Attenuation.LINEAR;
        this.relative = false;
        this.x = pos.getX() + 0.5;
        this.y = pos.getY() + 0.5;
        this.z = pos.getZ() + 0.5;
        followEntity();
    }

    @Override
    public CompletableFuture<AudioStream> getAudioStream(SoundBufferLibrary loader, Identifier id, boolean repeatInstantly) {
        CompletableFuture<AudioStream> stream = loader.getStream(id, repeatInstantly);
        if (offsetSeconds <= 0) {
            return stream;
        }
        long created = System.nanoTime();
        return stream.thenApply(s -> new SkippingAudioStream(s, offsetSeconds, created));
    }

    private boolean followEntity() {
        if (entityId < 0) {
            return true;
        }
        ClientLevel level = Minecraft.getInstance().level;
        Entity holder = level == null ? null : level.getEntity(entityId);
        if (holder == null || holder.isRemoved()) {
            return false;
        }
        this.x = holder.getX();
        this.y = holder.getY() + holder.getBbHeight() * 0.5;
        this.z = holder.getZ();
        return true;
    }

    @Override
    public void tick() {
        if (followEntity()) {
            ticksMissing = 0;
        } else if (++ticksMissing > 40) {
            stop(); // holder gone for 2 seconds
        }
    }

    public void halt() {
        stop();
    }
}
