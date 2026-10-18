package dev.portablestereo.client;

import java.util.HashMap;
import java.util.Map;

import dev.portablestereo.network.PlayStereoPayload;
import dev.portablestereo.network.StopStereoPayload;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.sounds.SoundEvent;

public class PortableStereoClient implements ClientModInitializer {
    /** source ("e<entity id>" or "b<block pos>") -> the song coming out of it */
    private static final Map<String, StereoSoundInstance> ACTIVE = new HashMap<>();

    @Override
    public void onInitializeClient() {
        StereoTooltip.register();

        ClientPlayNetworking.registerGlobalReceiver(PlayStereoPayload.TYPE, (payload, context) -> {
            Minecraft client = context.client();
            stop(client, payload.key());
            SoundEvent sound = payload.song().value().soundEvent().value();
            StereoSoundInstance instance = new StereoSoundInstance(sound, payload.entityId(), payload.pos(), payload.offsetTicks());
            ACTIVE.put(payload.key(), instance);
            client.getSoundManager().play(instance);
        });

        ClientPlayNetworking.registerGlobalReceiver(StopStereoPayload.TYPE,
                (payload, context) -> stop(context.client(), payload.key()));

        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            ACTIVE.values().forEach(StereoSoundInstance::halt);
            ACTIVE.clear();
        });
    }

    private static void stop(Minecraft client, String key) {
        StereoSoundInstance old = ACTIVE.remove(key);
        if (old != null) {
            old.halt();
            client.getSoundManager().stop(old);
        }
    }
}
