package dev.portablestereo;

import java.util.Optional;

import dev.portablestereo.network.PlayStereoPayload;
import dev.portablestereo.network.StopStereoPayload;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Containers;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.JukeboxSong;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;

public class StereoBlockEntity extends BlockEntity {
    private LoadedDisc disc;        // null = empty
    private Playback playback;      // null = at the start, stopped

    // Not saved: who is currently hearing this stereo
    private final Listeners listeners = new Listeners();
    private Holder<JukeboxSong> sessionSong;
    private long sessionAnchor;
    private boolean pickedUp;

    public StereoBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlocks.STEREO_BLOCK_ENTITY, pos, state);
    }

    public boolean hasDisc() {
        return disc != null;
    }

    private boolean isPlaying() {
        return disc != null && playback != null && !playback.paused();
    }

    private Optional<Holder<JukeboxSong>> song() {
        return disc == null ? Optional.empty() : JukeboxSong.fromStack(disc.toStack());
    }

    // ---------- moving between item and block ----------

    public void loadFromItem(ItemStack stereo) {
        disc = stereo.get(ModItems.LOADED_DISC);
        Playback pb = stereo.get(ModItems.PLAYBACK);
        // A stereo without the PLAYING flag counts as paused
        playback = pb != null && !stereo.has(ModItems.PLAYING) && level != null ? pb.pause(level.getGameTime()) : pb;
        changed();
    }

    private ItemStack toItem(boolean keepPlaying) {
        ItemStack stack = new ItemStack(ModItems.PORTABLE_STEREO);
        if (disc != null) {
            stack.set(ModItems.LOADED_DISC, disc);
            if (playback != null) {
                boolean playingNow = keepPlaying && isPlaying();
                stack.set(ModItems.PLAYBACK, playingNow || level == null ? playback : playback.pause(level.getGameTime()));
                if (playingNow) {
                    stack.set(ModItems.PLAYING, true);
                }
            }
        }
        return stack;
    }

    /** Sneak-pickup: returns the stereo (still playing if it was) and empties this block so nothing drops. */
    public ItemStack pickUp() {
        ItemStack stack = toItem(true);
        pickedUp = true;
        if (level instanceof ServerLevel serverLevel) {
            endSession(serverLevel);
        }
        disc = null;
        playback = null;
        return stack;
    }

    // ---------- controls ----------

    public void insertDisc(ItemStack discStack, ServerPlayer actor) {
        disc = LoadedDisc.of(discStack);
        playback = Playback.start(level.getGameTime());
        changed();
        song().ifPresent(s -> StereoItem.tell(actor,
                Component.translatable("message.portable_stereo.now_playing", s.value().description())));
    }

    public void togglePlay(ServerPlayer actor) {
        if (disc == null) {
            StereoItem.tell(actor, Component.translatable("message.portable_stereo.block_no_disc"));
            return;
        }
        long now = level.getGameTime();
        if (isPlaying()) {
            playback = playback.pause(now);
            StereoItem.tell(actor, Component.translatable("message.portable_stereo.paused"));
        } else {
            String key = playback == null ? "message.portable_stereo.now_playing" : "message.portable_stereo.resumed";
            playback = playback == null ? Playback.start(now) : playback.resume(now);
            song().ifPresent(s -> StereoItem.tell(actor, Component.translatable(key, s.value().description())));
        }
        changed();
    }

    private void changed() {
        setChanged();
        if (level != null && !level.isClientSide()) {
            BlockState state = getBlockState();
            BlockState updated = state
                    .setValue(StereoBlock.HAS_RECORD, disc != null)
                    .setValue(StereoBlock.PLAYING, isPlaying());
            if (updated != state) {
                level.setBlock(worldPosition, updated, Block.UPDATE_CLIENTS);
            }
            if (level instanceof ServerLevel serverLevel) {
                serverTick(serverLevel, true);
            }
        }
    }

    // ---------- ticking / sound ----------

    public static void tick(Level level, BlockPos pos, BlockState state, StereoBlockEntity stereo) {
        if (level instanceof ServerLevel serverLevel) {
            stereo.serverTick(serverLevel, serverLevel.getGameTime() % 10 == 0);
        }
    }

    private void serverTick(ServerLevel level, boolean syncListeners) {
        Optional<Holder<JukeboxSong>> song = song();
        if (!isPlaying() || song.isEmpty()) {
            endSession(level);
            return;
        }
        long now = level.getGameTime();
        if (playback.elapsed(now) >= song.get().value().lengthInTicks()) {
            playback = null; // finished: back to the start, stopped
            endSession(level);
            changed();
            return;
        }
        boolean restart = sessionSong == null || !sessionSong.equals(song.get()) || sessionAnchor != playback.value();
        if (restart) {
            endSession(level);
            sessionSong = song.get();
            sessionAnchor = playback.value();
        }
        if (restart || syncListeners) {
            final Holder<JukeboxSong> s = song.get();
            final Playback p = playback;
            listeners.sync(level, Vec3.atCenterOf(worldPosition), null,
                    () -> new PlayStereoPayload(-1, worldPosition, s, (int) p.elapsed(level.getGameTime())),
                    new StopStereoPayload(-1, worldPosition));
        }
    }

    private void endSession(ServerLevel level) {
        if (sessionSong != null) {
            listeners.stopAll(level.getServer(), new StopStereoPayload(-1, worldPosition));
            sessionSong = null;
        }
    }

    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        if (level instanceof ServerLevel serverLevel) {
            endSession(serverLevel);
            if (!pickedUp) {
                // Broken: drop the stereo, paused, with its disc still inside
                Containers.dropItemStack(level, pos.getX() + 0.5, pos.getY() + 0.3, pos.getZ() + 0.5, toItem(false));
            }
        }
        super.preRemoveSideEffects(pos, state);
    }

    @Override
    public void setRemoved() {
        if (level instanceof ServerLevel serverLevel) {
            endSession(serverLevel); // chunk unloaded
        }
        super.setRemoved();
    }

    // ---------- saving ----------

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.storeNullable("disc", LoadedDisc.CODEC, disc);
        output.storeNullable("playback", Playback.CODEC, playback);
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        disc = input.read("disc", LoadedDisc.CODEC).orElse(null);
        playback = input.read("playback", Playback.CODEC).orElse(null);
    }
}
