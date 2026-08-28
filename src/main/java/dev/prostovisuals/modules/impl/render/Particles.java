package dev.prostovisuals.modules.impl.render;

import dev.prostovisuals.client.events.impl.EventTick;
import dev.prostovisuals.modules.api.Category;
import dev.prostovisuals.modules.api.Module;
import dev.prostovisuals.modules.settings.api.Nameable;
import dev.prostovisuals.modules.settings.impl.BooleanSetting;
import dev.prostovisuals.modules.settings.impl.EnumSetting;
import dev.prostovisuals.modules.settings.impl.NumberSetting;
import meteordevelopment.orbit.EventHandler;

/** ProstoVisual controller for Wyvern's complete particle renderer.
 *  Rendering, collision and event triggers are still the original Wyvern implementation. */
public final class Particles extends Module {
    public enum Texture implements Nameable {
        SPARK("Spark", "Спарк 1"),
        SPARKLE("Sparkle", "Сияние"),
        DOLLAR("Dollar", "Доллар"),
        GLOW("Glow", "Свечение"),
        SNOW("Snow", "Снег"),
        STAR("Star", "Звезда");
        private final String name, nativeName; Texture(String n,String nativeName){this.name=n;this.nativeName=nativeName;}
        @Override public String getName(){return name;} public String nativeName(){return nativeName;}
    }
    private final EnumSetting<Texture> texture = new EnumSetting<>("Particle Texture", Texture.SPARK);
    private final BooleanSetting idle = new BooleanSetting("Spawn While Idle", false);
    private final BooleanSetting running = new BooleanSetting("Spawn While Running", false);
    private final BooleanSetting pearl = new BooleanSetting("Spawn On Pearl Landing", false);
    private final BooleanSetting trident = new BooleanSetting("Spawn On Trident Landing", false);
    private final BooleanSetting totem = new BooleanSetting("Spawn On Totem Pop", true);
    private final NumberSetting count = new NumberSetting("Particle Count", 10f, 2f, 40f, 1f);
    private final BooleanSetting glow = new BooleanSetting("Particle Glow", true);

    public Particles(){ super("Particles", Category.Render, "Wyvern particles with their original rendering and triggers"); }

    @Override public void onEnable(){ super.onEnable(); sync(); var n=wtf.wyvern.client.modules.impl.render.Particles.INSTANCE; if(!n.isEnabled()) n.setToggled(true); }
    @Override public void onDisable(){ var n=wtf.wyvern.client.modules.impl.render.Particles.INSTANCE; if(n.isEnabled()) n.setToggled(false); super.onDisable(); }
    @EventHandler public void onTick(EventTick e){ sync(); }

    private void sync(){
        var n=wtf.wyvern.client.modules.impl.render.Particles.INSTANCE;
        n.type.set(texture.getValue().nativeName());
        set(n,"Бездействии",idle.getValue());
        set(n,"Беге",running.getValue());
        // Hit particles have their own dedicated DamageParticles module. Keep the
        // native Wyvern hit trigger disabled so the two effects never duplicate.
        set(n,"Ударе",false);
        set(n,"Падении перла",pearl.getValue());
        set(n,"Падении трезубца",trident.getValue());
        set(n,"Сносе тотема",totem.getValue());
        n.count.setCurrent(count.getValue());
        // The Glow texture is authored for additive rendering; force the native glow blend
        // when that texture is selected so the preset can never silently render as a dark quad.
        n.glow.setEnabled(texture.getValue() == Texture.GLOW || glow.getValue());
    }
    private static void set(wtf.wyvern.client.modules.impl.render.Particles n,String key,boolean value){
        var v=n.reason.getValueByName(key); if(v!=null) v.setEnabled(value);
    }
}
