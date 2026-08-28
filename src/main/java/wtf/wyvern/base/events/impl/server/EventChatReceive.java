package wtf.wyvern.base.events.impl.server;

import lombok.Generated;
import net.minecraft.text.Text;
import wtf.wyvern.base.events.callables.EventCancellable;

public class EventChatReceive extends EventCancellable {
   private Text message;

   @Generated
   public Text getMessage() {
      return this.message;
   }

   @Generated
   public void setMessage(Text message) {
      this.message = message;
   }

   @Generated
   public EventChatReceive(Text message) {
      this.message = message;
   }
}