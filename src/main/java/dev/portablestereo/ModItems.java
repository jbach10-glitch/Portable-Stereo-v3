package dev.portablestereo;

import java.util.function.Function;

import com.mojang.serialization.Codec;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;

public final class ModItems {
    private ModItems() {}

    /** Which music disc is loaded into a stereo (with all its custom data). */
    public static final DataComponentType<LoadedDisc> LOADED_DISC = Registry.register(
            BuiltInRegistries.DATA_COMPONENT_TYPE,
            PortableStereo.id("loaded_disc"),
            DataComponentType.<LoadedDisc>builder()
                    .persistent(LoadedDisc.CODEC)
                    .networkSynchronized(LoadedDisc.STREAM_CODEC)
                    .build());

    /** Position in the song (playing or paused). Absent = start from the beginning. */
    public static final DataComponentType<Playback> PLAYBACK = Registry.register(
            BuiltInRegistries.DATA_COMPONENT_TYPE,
            PortableStereo.id("playback"),
            DataComponentType.<Playback>builder()
                    .persistent(Playback.CODEC)
                    .networkSynchronized(Playback.STREAM_CODEC)
                    .build());

    /** Present while the stereo is playing. Drives the animated "playing" texture. */
    public static final DataComponentType<Boolean> PLAYING = Registry.register(
            BuiltInRegistries.DATA_COMPONENT_TYPE,
            PortableStereo.id("playing"),
            DataComponentType.<Boolean>builder()
                    .persistent(Codec.BOOL)
                    .networkSynchronized(ByteBufCodecs.BOOL)
                    .build());

    public static final StereoItem PORTABLE_STEREO = register(
            "portable_stereo", StereoItem::new, new Item.Properties().stacksTo(1));

    private static <T extends Item> T register(String name, Function<Item.Properties, T> factory, Item.Properties props) {
        ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, PortableStereo.id(name));
        T item = factory.apply(props.setId(key));
        return Registry.register(BuiltInRegistries.ITEM, key, item);
    }

    public static void init() {
        CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.TOOLS_AND_UTILITIES)
                .register(output -> output.accept(PORTABLE_STEREO));
    }
}
