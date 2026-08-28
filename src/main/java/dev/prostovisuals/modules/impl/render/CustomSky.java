package dev.prostovisuals.modules.impl.render;

import dev.prostovisuals.modules.api.Category;
import dev.prostovisuals.modules.api.Module;
import dev.prostovisuals.modules.settings.api.Nameable;
import dev.prostovisuals.modules.settings.impl.EnumSetting;
import dev.prostovisuals.modules.settings.impl.NumberSetting;
import dev.prostovisuals.modules.settings.impl.ColorSetting;
import java.awt.Color;

public final class CustomSky extends Module {
    private final EnumSetting<Mode> mode = new EnumSetting<>("Mode", Mode.GALAXY);
    private final NumberSetting intensity = new NumberSetting("Intensity", 0.85f, 0.2f, 1.5f, 0.05f);
    private final NumberSetting stars = new NumberSetting("Stars", 0.85f, 0.0f, 1.5f, 0.05f,
            () -> !isFluidEffect() && mode.getValue() != Mode.PLASMA);
    private final NumberSetting rotation = new NumberSetting("Rotation", 0.0f, -180.0f, 180.0f, 1.0f);
    private final NumberSetting shaderSpeed = new NumberSetting("Shader Speed", 0.55f, 0.0f, 2.0f, 0.05f);
    private final NumberSetting shaderScale = new NumberSetting("Shader Scale", 1.0f, 0.55f, 1.8f, 0.05f,
            this::isVideoSky);
    private final NumberSetting effectSpeed = new NumberSetting("Effect Speed", 0.70f, 0.0f, 2.0f, 0.05f,
            () -> false);
    private final NumberSetting effectSize = new NumberSetting("Effect Scale", 1.0f, 0.35f, 3.0f, 0.05f,
            this::isFluidEffect);
    private final NumberSetting effectIntensity = new NumberSetting("Pattern Strength", 0.008f,
            0.002f, 0.025f, 0.001f, () -> false);
    private final NumberSetting effectOpacity = new NumberSetting("Opacity", 0.70f,
            0.05f, 1.0f, 0.05f, this::isFluidEffect);
    private final NumberSetting meteorFrequency = new NumberSetting("Meteor Frequency", 1.0f, 0.2f, 2.0f, 0.05f,
            () -> mode.getValue() == Mode.METEOR_SHOWER);
    private final ColorSetting waterColor = new ColorSetting("Water Texture Color", new Color(20, 110, 185).getRGB());
    private final ColorSetting causticColor = new ColorSetting("Caustic Texture Color", new Color(155, 48, 215).getRGB());
    private final ColorSetting auroraColor = new ColorSetting("Aurora Texture Color", new Color(65, 235, 155).getRGB());
    private boolean anchorCaptured;
    private Mode anchoredMode;
    private float anchorYaw;
    private float anchorPitch;

    public CustomSky() {
        super("CustomSky", Category.Render, "360-degree Galaxy, Energy, Plasma and legacy shader skies");
        waterColor.setVisible(() -> mode.getValue() == Mode.WATER);
        causticColor.setVisible(() -> mode.getValue() == Mode.CAUSTIC);
        auroraColor.setVisible(() -> mode.getValue() == Mode.AURORA);
    }

    @Override
    public void onEnable() {
        anchorCaptured = false;
        anchoredMode = null;
        super.onEnable();
    }

    public void captureAnchor(float yaw, float pitch) {
        if (anchorCaptured && anchoredMode == mode.getValue()) return;
        anchorYaw = yaw;
        anchorPitch = pitch;
        anchoredMode = mode.getValue();
        anchorCaptured = true;
    }

    public float getAnchorYaw() {
        return anchorYaw;
    }

    public float getAnchorPitch() {
        return anchorPitch;
    }

    public Mode getMode() {
        return mode.getValue();
    }

    public float getIntensity() {
        return intensity.getValue();
    }

    public float getStars() {
        return stars.getValue();
    }

    public float getRotation() {
        return rotation.getValue();
    }

    public float getShaderSpeed() {
        return shaderSpeed.getValue();
    }

    public float getShaderScale() {
        return shaderScale.getValue();
    }

    public float getEffectSpeed() {
        return effectSpeed.getValue();
    }

    public float getEffectSize() {
        return effectSize.getValue();
    }

    public float getEffectIntensity() {
        return effectIntensity.getValue();
    }

    public float getEffectOpacity() {
        return effectOpacity.getValue();
    }

    public float getMeteorFrequency() {
        return meteorFrequency.getValue();
    }

    public Color getWaterColor() { return waterColor.getColor(); }
    public Color getCausticColor() { return causticColor.getColor(); }
    public Color getAuroraColor() { return auroraColor.getColor(); }

    public boolean isFluidEffect() {
        return mode.getValue() == Mode.WATER || mode.getValue() == Mode.CAUSTIC;
    }

    public boolean isVideoSky() {
        return mode.getValue() == Mode.GALAXY
                || mode.getValue() == Mode.ENERGY
                || mode.getValue() == Mode.PLASMA;
    }

    public enum Mode implements Nameable {
        GALAXY("Galaxy"),
        ENERGY("Energy"),
        PLASMA("Plasma"),
        COSMOS("Cosmos"),
        METEOR_SHOWER("Meteor Shower"),
        AURORA("Aurora"),
        STARFALL("Starfall"),
        WATER("Water"),
        CAUSTIC("Caustic");

        private final String name;

        Mode(String name) {
            this.name = name;
        }

        @Override
        public String getName() {
            return name;
        }
    }
}
