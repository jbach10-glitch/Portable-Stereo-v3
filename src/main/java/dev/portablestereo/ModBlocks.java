package dev.portablestereo;

import net.fabricmc.fabric.api.object.builder.v1.block.entity.FabricBlockEntityTypeBuilder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.PushReaction;

public final class ModBlocks {
    private ModBlocks() {}

    private static final ResourceKey<Block> STEREO_KEY = ResourceKey.create(Registries.BLOCK, PortableStereo.id("portable_stereo"));

    public static final StereoBlock STEREO = Registry.register(BuiltInRegistries.BLOCK, STEREO_KEY,
            new StereoBlock(BlockBehaviour.Properties.of()
                    .setId(STEREO_KEY)
                    .strength(0.5F)
                    .sound(SoundType.METAL)
                    .noOcclusion()));
    public static final BlockEntityType<StereoBlockEntity> STEREO_BLOCK_ENTITY = Registry.register(
            BuiltInRegistries.BLOCK_ENTITY_TYPE,
            PortableStereo.id("portable_stereo"),
            FabricBlockEntityTypeBuilder.<StereoBlockEntity>create(StereoBlockEntity::new, STEREO).build());

    public static void init() {
        // Loading this class registers everything above
    }
}
