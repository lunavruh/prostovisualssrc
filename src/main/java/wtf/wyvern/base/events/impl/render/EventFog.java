package wtf.wyvern.base.events.impl.render;

import com.darkmagician6.eventapi.events.callables.EventCancellable;
import lombok.Generated;

public class EventFog extends EventCancellable {
   private float distance;
   private float startDistance;
   private int color;

   @Generated
   public float getDistance() {
      return this.distance;
   }

   @Generated
   public float getStartDistance() {
      return this.startDistance;
   }

   @Generated
   public void setStartDistance(float startDistance) {
      this.startDistance = startDistance;
   }

   @Generated
   public int getColor() {
      return this.color;
   }

   @Generated
   public void setDistance(float distance) {
      this.distance = distance;
   }

   @Generated
   public void setColor(int color) {
      this.color = color;
   }
}