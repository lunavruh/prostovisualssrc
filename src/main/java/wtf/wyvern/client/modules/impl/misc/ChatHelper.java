package wtf.wyvern.client.modules.impl.misc;

import com.darkmagician6.eventapi.EventTarget;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.s2c.play.GameMessageS2CPacket;
import net.minecraft.text.Text;
import wtf.wyvern.base.events.impl.input.EventChatSend;
import wtf.wyvern.base.events.impl.server.EventPacket;
import wtf.wyvern.client.modules.api.Category;
import wtf.wyvern.client.modules.api.Module;
import wtf.wyvern.client.modules.api.ModuleAnnotation;
import wtf.wyvern.client.modules.api.setting.impl.BooleanSetting;

@ModuleAnnotation(
        name = "ChatHelper",
        category = Category.MISC,
        description = "Помощник чата"
)
public class ChatHelper extends Module {
    public static final ChatHelper INSTANCE = new ChatHelper();

    private final BooleanSetting antiSpam = new BooleanSetting("Анти-спам", true);
    private final BooleanSetting chatSuffix = new BooleanSetting("Суффикс", false);

    private String lastReceivedMessage = "";

    private ChatHelper() {
    }

    @EventTarget
    public void onChatSend(EventChatSend event) {
        if (mc.player == null) return;
        String message = event.getMessage();
        if (chatSuffix.isEnabled() && !message.startsWith("/")) {
            event.setMessage(message + " \u2503 wyvern");
        }
    }

    @EventTarget
    public void onPacket(EventPacket event) {
        if (mc.player == null) return;
        if (!antiSpam.isEnabled() || !event.isReceive()) return;

        Packet<?> packet = event.getPacket();
        if (!(packet instanceof GameMessageS2CPacket gameMessage)) return;

        String msg = gameMessage.content().getString();
        if (msg.equals(lastReceivedMessage)) {
            event.setCancelled(true);
            return;
        }

        lastReceivedMessage = msg;
    }

    public static void sendClientMessage(String message) {
        if (INSTANCE.mc.player != null) {
            INSTANCE.mc.player.sendMessage(Text.literal("\u00a77[\u00a7dWyvern\u00a77] \u00a7f" + message), false);
        }
    }
}