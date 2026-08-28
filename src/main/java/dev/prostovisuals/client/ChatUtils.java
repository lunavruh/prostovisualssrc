package dev.prostovisuals.client;

import java.awt.Color;

import dev.prostovisuals.client.util.Wrapper;
import dev.prostovisuals.client.util.ColorUtils;
import lombok.experimental.UtilityClass;
import net.minecraft.text.MutableText;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.text.TextColor;

@UtilityClass
public class ChatUtils implements Wrapper {

    public void sendMessage(String message) {
        if (mc == null || mc.player == null) return;
        MutableText text = Text.literal("");
        for (int i = 0; i < "prostovisuals".length(); i++) {
            text.append(Text.literal("prostovisuals".charAt(i) + "")
                    .setStyle(Style.EMPTY
                            .withBold(true)
                            .withColor(TextColor.fromRgb(ColorUtils.gradient(ColorUtils.getGlobalColor(), Color.WHITE, (float) i / "prostovisuals".length()).getRGB()))
                    )
            );
        }

        text.append(Text.literal(" ⇨ ")
                .setStyle(Style.EMPTY
                        .withBold(false)
                        .withColor(TextColor.fromRgb(new Color(200, 200, 200).getRGB()))
                )
        );

        text.append(Text.literal(message)
                .setStyle(Style.EMPTY
                        .withBold(false)
                        .withColor(TextColor.fromRgb(new Color(200, 200, 200).getRGB()))
                )
        );

        mc.player.sendMessage(text, false);
    }
}