package dev.portablestereo;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

/** Keeps track of which players can hear one stereo, and tells them when to start/stop. */
public final class Listeners {
    private static final double RANGE = 64;   // same as a jukebox
    private static final double KEEP = 72;    // a little extra so walking at the edge doesn't flicker

    private Set<UUID> current = new HashSet<>();

    public void sync(ServerLevel level, Vec3 pos, ServerPlayer holder,
                     Supplier<CustomPacketPayload> play, CustomPacketPayload stop) {
        Set<UUID> next = new HashSet<>();
        CustomPacketPayload playPayload = null;
        for (ServerPlayer p : level.players()) {
            boolean already = current.contains(p.getUUID());
            double range = already ? KEEP : RANGE;
            if (p == holder || p.position().distanceToSqr(pos) <= range * range) {
                next.add(p.getUUID());
                if (!already) {
                    if (playPayload == null) {
                        playPayload = play.get();
                    }
                    ServerPlayNetworking.send(p, playPayload);
                }
            }
        }
        for (UUID id : current) {
            if (!next.contains(id)) {
                ServerPlayer p = level.getServer().getPlayerList().getPlayer(id);
                if (p != null) {
                    ServerPlayNetworking.send(p, stop);
                }
            }
        }
        current = next;
    }

    public void stopAll(MinecraftServer server, CustomPacketPayload stop) {
        for (UUID id : current) {
            ServerPlayer p = server.getPlayerList().getPlayer(id);
            if (p != null) {
                ServerPlayNetworking.send(p, stop);
            }
        }
        current = new HashSet<>();
    }
}
