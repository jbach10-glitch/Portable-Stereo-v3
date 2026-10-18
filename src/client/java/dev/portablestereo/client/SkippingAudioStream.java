package dev.portablestereo.client;

import java.io.IOException;
import java.nio.ByteBuffer;

import javax.sound.sampled.AudioFormat;

import net.minecraft.client.sounds.AudioStream;

/**
 * Wraps a song's audio and throws away the beginning, so playback starts partway in.
 * Also skips however long the skipping itself took, so everyone stays roughly in sync.
 */
final class SkippingAudioStream implements AudioStream {
    private final AudioStream inner;
    private final double skipSeconds;
    private final long createdNanos;
    private boolean skipped;

    SkippingAudioStream(AudioStream inner, double skipSeconds, long createdNanos) {
        this.inner = inner;
        this.skipSeconds = skipSeconds;
        this.createdNanos = createdNanos;
    }

    @Override
    public AudioFormat getFormat() {
        return inner.getFormat();
    }

    @Override
    public ByteBuffer read(int size) throws IOException {
        if (!skipped) {
            skipped = true;
            ByteBuffer rest = skip();
            if (rest != null) {
                return rest;
            }
        }
        return inner.read(size);
    }

    private ByteBuffer skip() throws IOException {
        AudioFormat format = inner.getFormat();
        int frame = Math.max(1, format.getFrameSize());
        double bytesPerSecond = format.getSampleRate() * frame;
        long done = 0;
        while (true) {
            double seconds = skipSeconds + (System.nanoTime() - createdNanos) / 1.0e9;
            long target = (long) (seconds * bytesPerSecond);
            target -= target % frame;
            if (done >= target) {
                return null;
            }
            ByteBuffer buffer = inner.read(65536);
            if (buffer == null || !buffer.hasRemaining()) {
                return buffer; // song ended while skipping
            }
            long need = target - done;
            if (buffer.remaining() > need) {
                buffer.position(buffer.position() + (int) need);
                return buffer;
            }
            done += buffer.remaining();
        }
    }

    @Override
    public void close() throws IOException {
        inner.close();
    }
}
