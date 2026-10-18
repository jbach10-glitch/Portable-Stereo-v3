package dev.portablestereo;

import java.util.Optional;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.JukeboxSong;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Held controls:
 *   - Right-click with a disc in your other hand (stereo empty) -> load it and play
 *   - Right-click                                              -> play / pause (keeps its place)
 *   - Sneak + right-click a block                              -> place it (keeps playing)
 *   - Sneak + right-click the air                              -> eject the disc
 */
public class StereoItem extends Item {
    public StereoItem(Properties properties) {
        super(properties);
    }

    public static ItemStack getDisc(ItemStack stereo) {
        LoadedDisc disc = stereo.get(ModItems.LOADED_DISC);
        return disc == null ? ItemStack.EMPTY : disc.toStack();
    }

    // ---------- placing ----------

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Player player = context.getPlayer();
        if (player == null || !player.isShiftKeyDown()) {
            return InteractionResult.PASS; // not sneaking: fall through to play/pause
        }
        Level level = context.getLevel();
        BlockPos clicked = context.getClickedPos();
        BlockPos pos = level.getBlockState(clicked).canBeReplaced() ? clicked : clicked.relative(context.getClickedFace());
        ItemStack stack = context.getItemInHand();
        if (!level.getBlockState(pos).canBeReplaced() || !player.mayUseItemAt(pos, context.getClickedFace(), stack)) {
            return InteractionResult.FAIL;
        }
        if (player instanceof ServerPlayer serverPlayer) {
            BlockState state = ModBlocks.STEREO.defaultBlockState()
                    .setValue(StereoBlock.FACING, context.getHorizontalDirection().getOpposite());
            level.setBlock(pos, state, Block.UPDATE_ALL);
            if (level.getBlockEntity(pos) instanceof StereoBlockEntity stereo) {
                stereo.loadFromItem(stack);
            }
            player.setItemInHand(context.getHand(), ItemStack.EMPTY);
            level.playSound(null, pos, SoundEvents.METAL_PLACE, SoundSource.BLOCKS, 1.0F, 1.0F);
            StereoPlayback.refresh(serverPlayer); // stops the "carried" sound; the block takes over
        }
        return InteractionResult.SUCCESS;
    }

    // ---------- play / pause / load / eject ----------

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        if (!(player instanceof ServerPlayer serverPlayer)) {
            return InteractionResult.SUCCESS; // client: just swing the arm
        }

        ItemStack stereo = player.getItemInHand(hand);
        ItemStack loaded = getDisc(stereo);
        InteractionHand otherHand = hand == InteractionHand.MAIN_HAND ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
        ItemStack other = player.getItemInHand(otherHand);
        long now = level.getGameTime();

        // Sneak + right-click air: eject
        if (player.isShiftKeyDown()) {
            if (loaded.isEmpty()) {
                return InteractionResult.PASS;
            }
            stereo.remove(ModItems.LOADED_DISC);
            stereo.remove(ModItems.PLAYBACK);
            stereo.remove(ModItems.PLAYING);
            if (!player.getInventory().add(loaded)) {
                level.addFreshEntity(new ItemEntity(level, player.getX(), player.getY(), player.getZ(), loaded));
            }
            StereoPlayback.refresh(serverPlayer);
            tell(serverPlayer, Component.translatable("message.portable_stereo.ejected"));
            return InteractionResult.SUCCESS;
        }

        // Empty: load the disc from the other hand and start playing
        if (loaded.isEmpty()) {
            if (!other.isEmpty() && other.has(DataComponents.JUKEBOX_PLAYABLE)) {
                stereo.set(ModItems.LOADED_DISC, LoadedDisc.of(other));
                if (!player.hasInfiniteMaterials()) {
                    other.shrink(1);
                }
                StereoPlayback.pauseOthers(serverPlayer, stereo);
                stereo.set(ModItems.PLAYBACK, Playback.start(now));
                stereo.set(ModItems.PLAYING, true);
                StereoPlayback.refresh(serverPlayer);
                announce(serverPlayer, stereo, "message.portable_stereo.now_playing");
                return InteractionResult.SUCCESS;
            }
            tell(serverPlayer, Component.translatable("message.portable_stereo.no_disc"));
            return InteractionResult.FAIL;
        }

        // Loaded: pause / resume
        Playback playback = stereo.get(ModItems.PLAYBACK);
        if (playback != null && !playback.paused()) {
            stereo.set(ModItems.PLAYBACK, playback.pause(now));
            stereo.remove(ModItems.PLAYING);
            tell(serverPlayer, Component.translatable("message.portable_stereo.paused"));
        } else {
            StereoPlayback.pauseOthers(serverPlayer, stereo);
            stereo.set(ModItems.PLAYBACK, playback == null ? Playback.start(now) : playback.resume(now));
            stereo.set(ModItems.PLAYING, true);
            announce(serverPlayer, stereo, playback == null ? "message.portable_stereo.now_playing" : "message.portable_stereo.resumed");
        }
        StereoPlayback.refresh(serverPlayer);
        return InteractionResult.SUCCESS;
    }

    static void announce(ServerPlayer player, ItemStack stereo, String key) {
        Optional<Holder<JukeboxSong>> song = StereoPlayback.songOf(stereo);
        song.ifPresent(s -> tell(player, Component.translatable(key, s.value().description())));
    }

    static void tell(ServerPlayer player, Component message) {
        player.sendSystemMessage(message, true); // true = action bar
    }
}
