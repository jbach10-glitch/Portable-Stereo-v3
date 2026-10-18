package dev.portablestereo.client;

import dev.portablestereo.ModItems;
import dev.portablestereo.Playback;
import dev.portablestereo.StereoItem;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.JukeboxSong;

/** Song name, playing/paused status and a time readout on the stereo's tooltip. */
public final class StereoTooltip {
    private StereoTooltip() {}

    public static void register() {
        ItemTooltipCallback.EVENT.register((stack, context, flag, lines) -> {
            if (!stack.is(ModItems.PORTABLE_STEREO)) {
                return;
            }
            ItemStack disc = StereoItem.getDisc(stack);
            if (disc.isEmpty()) {
                lines.add(Component.translatable("tooltip.portable_stereo.empty").withStyle(ChatFormatting.GRAY));
                lines.add(Component.translatable("tooltip.portable_stereo.hint_load").withStyle(ChatFormatting.DARK_GRAY));
                lines.add(Component.translatable("tooltip.portable_stereo.hint_place").withStyle(ChatFormatting.DARK_GRAY));
                return;
            }
            var song = JukeboxSong.fromStack(disc);
            Component name = song.map(h -> h.value().description()).orElse(disc.getHoverName());
            Playback playback = stack.get(ModItems.PLAYBACK);
            boolean playing = stack.has(ModItems.PLAYING) && playback != null && !playback.paused();

            String key = playing ? "tooltip.portable_stereo.playing"
                    : playback != null ? "tooltip.portable_stereo.paused"
                    : "tooltip.portable_stereo.loaded";
            lines.add(Component.translatable(key, name)
                    .withStyle(playing ? ChatFormatting.GREEN : playback != null ? ChatFormatting.YELLOW : ChatFormatting.GRAY));

            if (song.isPresent()) {
                long now = Minecraft.getInstance().level != null ? Minecraft.getInstance().level.getGameTime() : 0;
                long elapsed = playback == null ? 0 : Math.min(playback.elapsed(now), song.get().value().lengthInTicks());
                lines.add(Component.translatable("tooltip.portable_stereo.time",
                        time(elapsed), time(song.get().value().lengthInTicks())).withStyle(ChatFormatting.DARK_GRAY));
            }
            lines.add(Component.translatable("tooltip.portable_stereo.hint_eject").withStyle(ChatFormatting.DARK_GRAY));
            lines.add(Component.translatable("tooltip.portable_stereo.hint_place").withStyle(ChatFormatting.DARK_GRAY));
        });
    }

    private static String time(long ticks) {
        long seconds = ticks / 20;
        return String.format("%d:%02d", seconds / 60, seconds % 60);
    }
}
