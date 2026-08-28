package wtf.wyvern.base.events.impl.other;

import lombok.Generated;
import net.minecraft.client.gui.screen.Screen;
import wtf.wyvern.base.events.callables.EventCancellable;

public class EventCloseScreen extends EventCancellable {
   private final Screen screen;

   @Generated
   public EventCloseScreen(Screen screen) {
      this.screen = screen;
   }
}