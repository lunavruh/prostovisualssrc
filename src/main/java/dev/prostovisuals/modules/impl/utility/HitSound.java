package dev.prostovisuals.modules.impl.utility;

import dev.prostovisuals.modules.api.Category;
import dev.prostovisuals.modules.api.Module;
import dev.prostovisuals.modules.settings.impl.BooleanSetting;
import dev.prostovisuals.modules.settings.impl.ListSetting;
import dev.prostovisuals.modules.settings.impl.NumberSetting;
import org.jetbrains.annotations.NotNull;
import net.minecraft.client.resource.language.I18n;

public class HitSound extends Module {

    private final @NotNull BooleanSetting bell = new BooleanSetting("mode.bell", true, () -> false);
    private final @NotNull BooleanSetting crime = new BooleanSetting("mode.crime", false, () -> false);
    private final @NotNull BooleanSetting nya = new BooleanSetting("mode.nya", false, () -> false);
    private final @NotNull BooleanSetting skeet = new BooleanSetting("mode.skeet", false, () -> false);
    private final @NotNull BooleanSetting uwu = new BooleanSetting("mode.uwu", false, () -> false);
    private final @NotNull NumberSetting Volume = new NumberSetting(
            "setting.volume",
            1.00f,
            0.1f,
            2.0f,
            0.01f
    );
    private final @NotNull ListSetting mode = new ListSetting(
            "setting.sound",
            true,
            bell, crime, nya, skeet, uwu
    );

    public HitSound() {
        super("HitSound", Category.Utility, I18n.translate("module.hitsound.description"));
    }

    public @NotNull String getSelectedSound() {
        if (crime.getValue()) return "prostovisuals:crime";
        if (nya.getValue()) return "prostovisuals:nya";
        if (skeet.getValue()) return "prostovisuals:skeet";
        if (uwu.getValue()) return "prostovisuals:uwu";
        return "prostovisuals:bell"; // дефолт
    }
    public @NotNull NumberSetting getVolume() {
        return Volume;
    }
}
