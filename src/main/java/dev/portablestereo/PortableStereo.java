package dev.portablestereo;

import dev.portablestereo.network.PlayStereoPayload;
import dev.portablestereo.network.StopStereoPayload;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.resources.Identifier;

public class PortableStereo implements ModInitializer {
    public static final String MOD_ID = "portable_stereo";

    public static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(MOD_ID, path);
    }

    @Override
    public void onInitialize() {
        ModBlocks.init();
        ModItems.init();

        PayloadTypeRegistry.clientboundPlay().register(PlayStereoPayload.TYPE, PlayStereoPayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(StopStereoPayload.TYPE, StopStereoPayload.CODEC);

        ServerTickEvents.END_SERVER_TICK.register(StereoPlayback::tick);
    }
}
