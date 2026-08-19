package dev.prostovisuals.modules.impl.render;

import dev.prostovisuals.modules.api.Category;
import dev.prostovisuals.modules.api.Module;
import dev.prostovisuals.modules.settings.api.Nameable;
import dev.prostovisuals.modules.settings.impl.EnumSetting;
import dev.prostovisuals.modules.settings.impl.NumberSetting;

public final class CustomSky extends Module {
    private final EnumSetting<Mode> mode = new EnumSetting<>("Mode", Mode.COSMOS);
    private final NumberSetting intensity = new NumberSetting("Intensity", 0.85f, 0.2f, 1.5f, 0.05f,
            () -> !isFluidEffect());
    private final NumberSetting stars = new NumberSetting("Stars", 0.85f, 0.0f, 1.5f, 0.05f,
            () -> !isFluidEffect());
    private final NumberSetting rotation = new NumberSetting("Rotation", 0.0f, -180.0f, 180.0f, 1.0f);
    private final NumberSetting effectSpeed = new NumberSetting("Effect Speed", 0.70f, 0.0f, 2.0f, 0.05f,
            this::isFluidEffect);
    private final NumberSetting effectSize = new NumberSetting("Effect Scale", 1.0f, 0.35f, 3.0f, 0.05f,
            this::isFluidEffect);
    private final NumberSetting effectIntensity = new NumberSetting("Pattern Strength", 0.008f,
            0.002f, 0.025f, 0.001f, this::isFluidEffect);
    private final NumberSetting effectOpacity = new NumberSetting("Opacity", 0.70f,
            0.05f, 1.0f, 0.05f, this::isFluidEffect);
    private final NumberSetting meteorFrequency = new NumberSetting("Meteor Frequency", 1.0f, 0.2f, 2.0f, 0.05f,
            () -> mode.getValue() == Mode.METEOR_SHOWER);
    private boolean anchorCaptured;
    private Mode anchoredMode;
    private float anchorYaw;
    private float anchorPitch;

    public CustomSky() {
        super("CustomSky", Category.Render, "Optimized shader sky with legacy and Kimiko sky modes");
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

    public boolean isFluidEffect() {
        return mode.getValue() == Mode.WATER || mode.getValue() == Mode.CAUSTIC;
    }

    public enum Mode implements Nameable {
        COSMOS("Cosmos"),
        METEOR_SHOWER("Meteor Shower"),
        AURORA("Aurora"),
        STARFALL("Starfall"),
        BLACK_HOLE("Black Hole"),
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
