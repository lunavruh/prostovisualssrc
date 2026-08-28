package wtf.wyvern.base.comand.impl;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.command.CommandSource;
import ru.nexusguard.protection.annotations.Native;
import wtf.wyvern.Wyvern;
import wtf.wyvern.base.comand.api.CommandAbstract;
import wtf.wyvern.base.waypoint.Waypoint;
import wtf.wyvern.utility.game.other.MessageUtil;

public class GPSCommand extends CommandAbstract {
   public GPSCommand() {
      super("gps");
   }

   @Native
   public void execute(LiteralArgumentBuilder<CommandSource> builder) {
      ((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)builder.then(arg("X", IntegerArgumentType.integer()).then(arg("Z", IntegerArgumentType.integer()).executes((context) -> {
         int x = (Integer)context.getArgument("X", Integer.class);
         int z = (Integer)context.getArgument("Z", Integer.class);
         Waypoint waypoint = new Waypoint((double)x, (double)z);
         Wyvern.getInstance().getWaypointManager().set(waypoint);
         MessageUtil.displayInfo("GPS создан и указывает на XZ: %s, %s".formatted(new Object[]{x, z}));
         return 1;
      })))).then(literal("player").then(arg("name", StringArgumentType.word()).then(arg("X", IntegerArgumentType.integer()).then(arg("Z", IntegerArgumentType.integer()).executes((context) -> {
         String name = (String)context.getArgument("name", String.class);
         int x = (Integer)context.getArgument("X", Integer.class);
         int z = (Integer)context.getArgument("Z", Integer.class);
         Waypoint waypoint = new Waypoint(name, (double)x, (double)z);
         Wyvern.getInstance().getWaypointManager().setPlayerWaypoint(waypoint);
         MessageUtil.displayInfo("GPS создан и указывает на XZ: %s, %s".formatted(new Object[]{x, z}));
         return 1;
      })))))).then(literal("player").then(literal("remove").executes((context) -> {
         if (!Wyvern.getInstance().getWaypointManager().isEmptyPlayerWaypoint()) {
            Wyvern.getInstance().getWaypointManager().clearPlayerWaypoint();
            MessageUtil.displayInfo("GPS удален");
         } else {
            MessageUtil.displayInfo("Нет активного GPS");
         }

         return 1;
      })))).then(literal("remove").executes((context) -> {
         if (!Wyvern.getInstance().getWaypointManager().isEmpty()) {
            Wyvern.getInstance().getWaypointManager().clear();
            MessageUtil.displayInfo("GPS на точку игрока удален");
         } else {
            MessageUtil.displayInfo("Нет активного на игрока GPS");
         }

         return 1;
      }));
   }
}