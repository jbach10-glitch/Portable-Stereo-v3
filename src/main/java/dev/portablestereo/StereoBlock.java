package dev.portablestereo;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Placed controls:
 *   - Right-click holding a disc (stereo empty) -> load it and play
 *   - Right-click                               -> play / pause (keeps its place)
 *   - Sneak + right-click with an empty hand    -> pick it up (keeps playing)
 *   - Break it                                  -> drops the stereo, paused, disc still inside
 */
public class StereoBlock extends BaseEntityBlock {
    public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final BooleanProperty HAS_RECORD = BlockStateProperties.HAS_RECORD;
    public static final BooleanProperty PLAYING = BooleanProperty.create("playing");

    private static final VoxelShape SHAPE_NS = Block.box(1, 0, 6, 15, 11, 10);
    private static final VoxelShape SHAPE_EW = Block.box(6, 0, 1, 10, 11, 15);

    public StereoBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any()
                .setValue(FACING, Direction.NORTH)
                .setValue(HAS_RECORD, false)
                .setValue(PLAYING, false));
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return simpleCodec(StereoBlock::new);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, HAS_RECORD, PLAYING);
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        Direction facing = state.getValue(FACING);
        return facing.getAxis() == Direction.Axis.X ? SHAPE_EW : SHAPE_NS;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new StereoBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide() ? null : createTickerHelper(type, ModBlocks.STEREO_BLOCK_ENTITY, StereoBlockEntity::tick);
    }

    @Override
    protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                          Player player, InteractionHand hand, BlockHitResult hit) {
        if (!stack.has(DataComponents.JUKEBOX_PLAYABLE)
                || !(level.getBlockEntity(pos) instanceof StereoBlockEntity stereo)
                || stereo.hasDisc()) {
            return InteractionResult.TRY_WITH_EMPTY_HAND;
        }
        if (player instanceof ServerPlayer serverPlayer) {
            stereo.insertDisc(stack.copyWithCount(1), serverPlayer);
            if (!player.hasInfiniteMaterials()) {
                stack.shrink(1);
            }
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!(level.getBlockEntity(pos) instanceof StereoBlockEntity stereo)) {
            return InteractionResult.PASS;
        }
        if (!(player instanceof ServerPlayer serverPlayer)) {
            return InteractionResult.SUCCESS;
        }
        if (player.isShiftKeyDown()) {
            if (!player.getMainHandItem().isEmpty()) {
                return InteractionResult.PASS;
            }
            ItemStack picked = stereo.pickUp();
            level.removeBlock(pos, false);
            player.setItemInHand(InteractionHand.MAIN_HAND, picked);
            level.playSound(null, pos, SoundEvents.ITEM_PICKUP, SoundSource.PLAYERS, 0.4F, 1.0F);
            StereoPlayback.refresh(serverPlayer); // the carried stereo takes over, same spot in the song
            return InteractionResult.SUCCESS;
        }
        stereo.togglePlay(serverPlayer);
        return InteractionResult.SUCCESS;
    }
}
