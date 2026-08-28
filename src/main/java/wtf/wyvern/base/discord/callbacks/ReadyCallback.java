package wtf.wyvern.base.discord.callbacks;

import com.sun.jna.Callback;
import wtf.wyvern.base.discord.utils.DiscordUser;

public interface ReadyCallback extends Callback {
   void apply(DiscordUser var1);
}