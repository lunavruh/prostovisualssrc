package wtf.wyvern.base.comand.impl;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.command.CommandSource;
import ru.nexusguard.protection.annotations.Native;
import wtf.wyvern.Wyvern;
import wtf.wyvern.base.comand.api.CommandAbstract;
import wtf.wyvern.base.comand.impl.args.FriendArgumentType;
import wtf.wyvern.base.comand.impl.args.PlayerArgumentType;
import wtf.wyvern.utility.game.other.MessageUtil;

public class StaffCommand extends CommandAbstract {
   public StaffCommand() {
      super("friend");
   }

   @Native
   public void execute(LiteralArgumentBuilder<CommandSource> builder) {
      builder.then(literal("add").then(arg("player", PlayerArgumentType.create()).executes((context) -> {
         String name = (String)context.getArgument("player", String.class);
         if (Wyvern.getInstance().getStaffManager().getItems().contains(name)) {
            MessageUtil.displayMessage(MessageUtil.LogLevel.WARN, "Уже добавлен " + name);
            return 1;
         } else {
            Wyvern.getInstance().getStaffManager().add(name);
            MessageUtil.displayMessage(MessageUtil.LogLevel.INFO, "Добавили " + name);
            return 1;
         }
      })));
      builder.then(literal("remove").then(arg("player", FriendArgumentType.create()).executes((context) -> {
         String nickname = (String)context.getArgument("player", String.class);
         Wyvern.getInstance().getStaffManager().remove(nickname);
         MessageUtil.displayMessage(MessageUtil.LogLevel.INFO, nickname + " удален из стаффа");
         return 1;
      })));
      builder.then(literal("list").executes((commandContext) -> {
         MessageUtil.displayMessage(MessageUtil.LogLevel.INFO, Wyvern.getInstance().getStaffManager().getItems().toString());
         return 1;
      }));
   }
}