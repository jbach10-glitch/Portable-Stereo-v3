package dev.portablestereo;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import dev.portablestereo.network.PlayStereoPayload;
import dev.portablestereo.network.StopStereoPayload;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.JukeboxSong;
import net.minecraft.world.level.Level;

/**
 * Handles stereos carried by players. The song position lives on the item itself
 * (see {@link Playback}); this class just turns "a player is carrying a playing stereo"
 * into sound for everyone nearby.
 */
public final class StereoPlayback {
    private StereoPlayback() {}

    private static final class Session {
        final Listeners listeners = new Listeners();
        final Holder<JukeboxSong> song;
        final long anchor;
        final int entityId;
        final ResourceKey<Level> dimension;

        Session(Holder<JukeboxSong> song, long anchor, int entityId, ResourceKey<Level> dimension) {
            this.song = song;
            this.anchor = anchor;
            this.entityId = entityId;
            this.dimension = dimension;
        }
    }

    private static final Map<UUID, Session> SESSIONS = new HashMap<>();

    public static Optional<Holder<JukeboxSong>> songOf(ItemStack stereo) {
        ItemStack disc = StereoItem.getDisc(stereo);
        return disc.isEmpty() ? Optional.empty() : JukeboxSong.fromStack(disc);
    }

    /** Re-check a player right away (call after changing one of their stereos). */
    public static void refresh(ServerPlayer player) {
        update(player, true);
    }

    static void tick(MinecraftServer server) {
        long t = server.getTickCount();
        if (t % 5 != 0) {
            return;
        }
        boolean syncListeners = t % 10 == 0;
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            update(player, syncListeners);
        }
        // Players who logged out
        Iterator<Map.Entry<UUID, Session>> it = SESSIONS.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, Session> e = it.next();
            if (server.getPlayerList().getPlayer(e.getKey()) == null) {
                e.getValue().listeners.stopAll(server, stopPayload(e.getValue().entityId));
                it.remove();
            }
        }
    }

    private static void update(ServerPlayer player, boolean syncListeners) {
        MinecraftServer server = player.level().getServer();
        Session session = SESSIONS.get(player.getUUID());
        ItemStack stereo = player.isAlive() ? findPlaying(player) : ItemStack.EMPTY;
        Optional<Holder<JukeboxSong>> song = stereo.isEmpty() ? Optional.empty() : songOf(stereo);
        Playback playback = stereo.isEmpty() ? null : stereo.get(ModItems.PLAYBACK);

        if (song.isEmpty() || playback == null || playback.paused()) {
            if (!stereo.isEmpty()) {
                stereo.remove(ModItems.PLAYING); // flagged as playing but can't play: fix the look
            }
            endSession(player, server);
            return;
        }

        long now = player.level().getGameTime();
        if (playback.elapsed(now) >= song.get().value().lengthInTicks()) {
            // Song finished: back to the start, stopped
            stereo.remove(ModItems.PLAYING);
            stereo.remove(ModItems.PLAYBACK);
            endSession(player, server);
            return;
        }

        boolean changed = session == null
                || !session.song.equals(song.get())
                || session.anchor != playback.value()
                || session.dimension != player.level().dimension();
        if (changed) {
            endSession(player, server);
            session = new Session(song.get(), playback.value(), player.getId(), player.level().dimension());
            SESSIONS.put(player.getUUID(), session);
        }

        if (changed || syncListeners) {
            final Holder<JukeboxSong> s = song.get();
            final Playback p = playback;
            ServerLevel level = (ServerLevel) player.level();
            session.listeners.sync(level, player.position(), player,
                    () -> new PlayStereoPayload(player.getId(), player.blockPosition(), s,
                            (int) p.elapsed(level.getGameTime())),
                    stopPayload(player.getId()));
        }
    }

    private static void endSession(ServerPlayer player, MinecraftServer server) {
        Session old = SESSIONS.remove(player.getUUID());
        if (old != null) {
            old.listeners.stopAll(server, stopPayload(old.entityId));
        }
    }

    private static StopStereoPayload stopPayload(int entityId) {
        return new StopStereoPayload(entityId, net.minecraft.core.BlockPos.ZERO);
    }

    /** Pause every playing stereo the player carries except {@code keep}. */
    public static void pauseOthers(ServerPlayer player, ItemStack keep) {
        long now = player.level().getGameTime();
        forEachStereo(player, s -> {
            if (s != keep && s.has(ModItems.PLAYING)) {
                Playback pb = s.get(ModItems.PLAYBACK);
                if (pb != null) {
                    s.set(ModItems.PLAYBACK, pb.pause(now));
                }
                s.remove(ModItems.PLAYING);
            }
        });
    }

    private static ItemStack findPlaying(ServerPlayer player) {
        ItemStack[] found = {ItemStack.EMPTY};
        forEachStereo(player, s -> {
            if (found[0].isEmpty() && s.has(ModItems.PLAYING)) {
                found[0] = s;
            }
        });
        return found[0];
    }

    private static void forEachStereo(ServerPlayer player, java.util.function.Consumer<ItemStack> action) {
        Inventory inventory = player.getInventory();
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack stack = inventory.getItem(i);
            if (stack.is(ModItems.PORTABLE_STEREO)) {
                action.accept(stack);
            }
        }
        ItemStack carried = player.containerMenu.getCarried();
        if (carried.is(ModItems.PORTABLE_STEREO)) {
            action.accept(carried);
        }
    }
}
