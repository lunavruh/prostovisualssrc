package dev.prostovisuals.modules.impl.render;

import dev.prostovisuals.client.managers.ThemeManager;
import dev.prostovisuals.client.events.impl.EventRender2D;
import dev.prostovisuals.client.events.impl.EventRender3D;
import dev.prostovisuals.modules.api.Category;
import dev.prostovisuals.modules.api.Module;
import dev.prostovisuals.modules.settings.impl.BooleanSetting;
import dev.prostovisuals.modules.settings.impl.VisualColorSettings;
import dev.prostovisuals.client.util.renderer.Render2D;
import dev.prostovisuals.client.util.renderer.Render3D;
import dev.prostovisuals.client.util.renderer.fonts.Fonts;
import dev.prostovisuals.client.util.world.WorldUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.projectile.ArrowEntity;
import net.minecraft.entity.projectile.TridentEntity;
import net.minecraft.entity.projectile.thrown.EnderPearlEntity;
import net.minecraft.item.CrossbowItem;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.RaycastContext;
import net.minecraft.client.resource.language.I18n;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;

public class Predictions extends Module implements ThemeManager.ThemeChangeListener {

    private record ProjectilePoint(Vec3d pos, int ticks, boolean isLandingPoint, long creationTime, ProjectileType type, boolean isMoving, Entity hitEntity, boolean isPlayerThrown) {}
    private record CollisionCandidate(Entity entity, Box expandedBox) {}

    private enum ProjectileType {
        PEARL, ARROW, TRIDENT
    }

    private final List<ProjectilePoint> projectilePoints = new ArrayList<>();
    private final List<ProjectilePoint> prevProjectilePoints = new ArrayList<>();
    private final ThemeManager themeManager;
    private Color currentColor;
    private Color currentColorSecondary;
    private final VisualColorSettings visualColor = new VisualColorSettings();

    // Settings
    private final BooleanSetting showPearl = new BooleanSetting("Show Pearl", true, () -> true);
    private final BooleanSetting showBow = new BooleanSetting("Show Bow", true, () -> true);
    private final BooleanSetting showCrossbow = new BooleanSetting("Show Crossbow", true, () -> true);
    private final BooleanSetting showTrident = new BooleanSetting("Show Trident", true, () -> true);
    private final BooleanSetting showWhenHolding = new BooleanSetting("Show When Holding", true, () -> true);
    private final BooleanSetting showLandingInfo = new BooleanSetting("Show Landing Info", true, () -> true);
    private final BooleanSetting highlightPlayers = new BooleanSetting("Highlight Players", true, () -> true);
    private static final double highlightRange = 100f;
    // Simulation parameters
    private static final int MAX_TICKS = 170;
    private static final int SUBSTEPS = 3;
    private static final float BASE_LINE_WIDTH = 4.0f;

    // Pearl physics
    private static final double PEARL_SPEED = 1.5;
    private static final double PEARL_SPEED_FACTOR = 1.06;
    private static final double PEARL_GRAVITY = 0.03;

    // Arrow physics
    private static final double ARROW_SPEED = 3.0;
    private static final double ARROW_GRAVITY = 0.05;

    // Trident physics
    private static final double TRIDENT_SPEED = 2.5;
    private static final double TRIDENT_GRAVITY = 0.03;

    private static final double WATER_DRAG = 0.8;
    private static final double AIR_DRAG = 0.99;
    private static final double WATER_DRAG_PER_SUBSTEP = Math.pow(WATER_DRAG, 1.0 / SUBSTEPS);
    private static final double AIR_DRAG_PER_SUBSTEP = Math.pow(AIR_DRAG, 1.0 / SUBSTEPS);
    private static final ItemStack PEARL_STACK = new ItemStack(Items.ENDER_PEARL);
    private static final ItemStack ARROW_STACK = new ItemStack(Items.ARROW);
    private static final ItemStack TRIDENT_STACK = new ItemStack(Items.TRIDENT);

    // Performance: one entity snapshot is reused by every trajectory substep.
    private final List<CollisionCandidate> collisionCandidates = new ArrayList<>();
    private final List<EnderPearlEntity> activePearls = new ArrayList<>();
    private final List<ArrowEntity> activeArrows = new ArrayList<>();
    private final List<TridentEntity> activeTridents = new ArrayList<>();
    private List<List<ProjectilePoint>> cachedTrajectories = List.of();
    private List<List<ProjectilePoint>> cachedPrevTrajectories = List.of();
    private long predictionCreationTime;
    private final BlockPos.Mutable waterCheckPos = new BlockPos.Mutable();
    private boolean lastHasPlayerProjectiles;
    private long lastPredictionUpdateNs;
    private static final long PREDICTION_INTERVAL_NS = 33_000_000L; // ~30 Hz simulation, rendering stays full FPS

    public Predictions() {
        super("Predictions", Category.Render, I18n.translate("module.predictions.description"));
        this.themeManager = ThemeManager.getInstance();
        this.currentColor = themeManager.getThemeColor();
        this.currentColorSecondary = themeManager.getRenderedSecondaryBackgroundColor();
        themeManager.addThemeChangeListener(this);
    }

    @Override
    public void onThemeChanged(ThemeManager.Theme theme) {
        this.currentColor = theme.getBackgroundColor();
        this.currentColorSecondary = theme.getSecondaryBackgroundColor();
    }

    @Override
    public void onEnable() {
        themeManager.removeThemeChangeListener(this);
        themeManager.addThemeChangeListener(this);
        lastPredictionUpdateNs = 0L;
        super.onEnable();
    }

    @Override
    public void onDisable() {
        themeManager.removeThemeChangeListener(this);
        projectilePoints.clear();
        prevProjectilePoints.clear();
        collisionCandidates.clear();
        activePearls.clear();
        activeArrows.clear();
        activeTridents.clear();
        cachedTrajectories = List.of();
        cachedPrevTrajectories = List.of();
        super.onDisable();
    }

    @EventHandler
    public void onRender2D(EventRender2D e) {
        if (fullNullCheck()) return;

        // Only draw landing info in 2D HUD; 3D path is rendered in onRender3D now
        if (showLandingInfo.getValue() && !projectilePoints.isEmpty()) {
            renderLandingInfo2D(e);
        }
    }

    @EventHandler
    public void onRender3D(EventRender3D.Game e) {
        if (fullNullCheck()) return;
        currentColor = visualColor.resolve();
        currentColorSecondary = new Color(
                Math.round(currentColor.getRed() * 0.62f),
                Math.round(currentColor.getGreen() * 0.62f),
                Math.round(currentColor.getBlue() * 0.62f));

        // Attach current tick delta for smooth helpers
        Render3D.setTickDelta(e.getTickDelta());

        // Physics/raycasting is the expensive part. Recomputing 170*3 substeps
        // at 180 FPS wastes six times the CPU for no visible benefit. Keep the
        // cached trajectory and render it every frame, but simulate at ~30 Hz.
        long nowNs = System.nanoTime();
        if (nowNs - lastPredictionUpdateNs < PREDICTION_INTERVAL_NS) {
            if (!projectilePoints.isEmpty()) {
                renderProjectileTrajectory3D(e, lastHasPlayerProjectiles);
            }
            renderPredictionHighlights(e);
            return;
        }
        lastPredictionUpdateNs = nowNs;

        collisionCandidates.clear();
        activePearls.clear();
        activeArrows.clear();
        activeTridents.clear();
        Vec3d playerPos = mc.player.getPos();
        boolean collectPearls = showPearl.getValue();
        boolean collectArrows = showBow.getValue() || showCrossbow.getValue();
        boolean collectTridents = showTrident.getValue();
        for (Entity candidate : mc.world.getEntities()) {
            if (candidate == mc.player || !candidate.isAlive()) continue;
            if (candidate.getPos().squaredDistanceTo(playerPos) <= 16384.0) {
                collisionCandidates.add(new CollisionCandidate(
                        candidate, candidate.getBoundingBox().expand(0.3)));
            }
            if (candidate instanceof EnderPearlEntity pearl && collectPearls) activePearls.add(pearl);
            else if (candidate instanceof ArrowEntity arrow && arrow.getOwner() != null
                    && collectArrows) activeArrows.add(arrow);
            else if (candidate instanceof TridentEntity trident && trident.getOwner() != null
                    && collectTridents) activeTridents.add(trident);
        }

        // Recompute prediction points in 3D phase so trajectory can be drawn in world space
        prevProjectilePoints.clear();
        prevProjectilePoints.addAll(projectilePoints);
        projectilePoints.clear();
        predictionCreationTime = System.nanoTime();

        boolean hasPlayerProjectiles = false;

        for (EnderPearlEntity pearl : activePearls) {
            boolean isPlayerThrown = pearl.getOwner() != null && pearl.getOwner() == mc.player;
            hasPlayerProjectiles |= isPlayerThrown;
            simulatePearl(pearl, isPlayerThrown);
        }
        for (ArrowEntity arrow : activeArrows) {
            boolean isMoving = !arrow.isOnGround() && !arrow.isTouchingWater()
                    && arrow.getVelocity().lengthSquared() >= 0.01;
            if (isMoving) {
                hasPlayerProjectiles = true;
                simulateArrow(arrow);
            }
        }
        for (TridentEntity trident : activeTridents) {
            boolean isMoving = !trident.isOnGround() && !trident.isTouchingWater()
                    && trident.getVelocity().lengthSquared() >= 0.01;
            if (isMoving) {
                hasPlayerProjectiles = true;
                simulateTrident(trident);
            }
        }

        // Simulate from hand if requested and nothing is flying
        boolean holdingPearl = isHoldingEnderPearl();
        boolean holdingBow = isHoldingBow();
        boolean holdingCrossbow = isHoldingCrossbow();
        boolean holdingTrident = isHoldingTrident();
        boolean isBowDrawn = mc.player != null && mc.player.isUsingItem() && mc.player.getActiveItem().getItem() == Items.BOW;
        boolean isCrossbowCharged = mc.player != null && holdingCrossbow && CrossbowItem.isCharged(mc.player.getMainHandStack()) || CrossbowItem.isCharged(mc.player.getOffHandStack());
        boolean isTridentCharging = mc.player != null && mc.player.isUsingItem() && mc.player.getActiveItem().getItem() == Items.TRIDENT;

        boolean shouldShowHolding = showWhenHolding.getValue() && (holdingPearl || (holdingBow && isBowDrawn) || (holdingCrossbow && isCrossbowCharged) || (holdingTrident && isTridentCharging));

        if (shouldShowHolding && !hasPlayerProjectiles) {
            if (holdingPearl && showPearl.getValue()) simulatePearlFromHand();
            if (holdingBow && showBow.getValue() && isBowDrawn) simulateBowFromHand();
            if (holdingCrossbow && showCrossbow.getValue() && isCrossbowCharged) simulateCrossbowFromHand();
            if (holdingTrident && showTrident.getValue() && isTridentCharging) simulateTridentFromHand();
        }

        lastHasPlayerProjectiles = hasPlayerProjectiles;
        cachedPrevTrajectories = groupTrajectories(prevProjectilePoints);
        cachedTrajectories = groupTrajectories(projectilePoints);
        if (!projectilePoints.isEmpty()) {
            renderProjectileTrajectory3D(e, hasPlayerProjectiles);
        }

        renderPredictionHighlights(e);
    }

    private void renderPredictionHighlights(EventRender3D.Game e) {
        if (!highlightPlayers.getValue() || mc.player == null || projectilePoints.isEmpty()) return;
        Vec3d playerPos = mc.player.getPos();
        double rangeSq = highlightRange * highlightRange;
        for (ProjectilePoint point : projectilePoints) {
            if (!point.isLandingPoint() || !point.isPlayerThrown()) continue;
            for (CollisionCandidate candidate : collisionCandidates) {
                Entity entity = candidate.entity();
                if (entity instanceof LivingEntity living
                        && living != mc.player
                        && living.isAlive()
                        && candidate.expandedBox().contains(point.pos())
                        && playerPos.squaredDistanceTo(living.getPos()) <= rangeSq) {
                    renderPlayerHighlight(e, living);
                }
            }
        }
    }

    private boolean isHoldingEnderPearl() {
        if (mc.player == null) return false;
        return mc.player.getMainHandStack().getItem() == Items.ENDER_PEARL
                || mc.player.getOffHandStack().getItem() == Items.ENDER_PEARL;
    }

    private boolean isHoldingBow() {
        if (mc.player == null) return false;
        return mc.player.getMainHandStack().getItem() == Items.BOW
                || mc.player.getOffHandStack().getItem() == Items.BOW;
    }

    private boolean isHoldingCrossbow() {
        if (mc.player == null) return false;
        return mc.player.getMainHandStack().getItem() == Items.CROSSBOW
                || mc.player.getOffHandStack().getItem() == Items.CROSSBOW;
    }

    private boolean isHoldingTrident() {
        if (mc.player == null) return false;
        return mc.player.getMainHandStack().getItem() == Items.TRIDENT
                || mc.player.getOffHandStack().getItem() == Items.TRIDENT;
    }

    private void simulatePearlFromHand() {
        if (mc.player == null) return;

        Vec3d startPos = mc.player.getEyePos();
        float pitch = mc.player.getPitch();
        float yaw = mc.player.getYaw();

        Vec3d initialVelocity = new Vec3d(
                -Math.sin(Math.toRadians(yaw)) * Math.cos(Math.toRadians(pitch)),
                -Math.sin(Math.toRadians(pitch)),
                Math.cos(Math.toRadians(yaw)) * Math.cos(Math.toRadians(pitch))
        ).multiply(PEARL_SPEED);

        simulateProjectileLanding(startPos, initialVelocity, ProjectileType.PEARL, false, true);
    }

    private void simulateBowFromHand() {
        if (mc.player == null) return;

        Vec3d startPos = mc.player.getEyePos();
        float pitch = mc.player.getPitch();
        float yaw = mc.player.getYaw();

        Vec3d initialVelocity = new Vec3d(
                -Math.sin(Math.toRadians(yaw)) * Math.cos(Math.toRadians(pitch)),
                -Math.sin(Math.toRadians(pitch)),
                Math.cos(Math.toRadians(yaw)) * Math.cos(Math.toRadians(pitch))
        ).multiply(ARROW_SPEED);

        simulateProjectileLanding(startPos, initialVelocity, ProjectileType.ARROW, false, true);
    }

    private void simulateCrossbowFromHand() {
        if (mc.player == null) return;

        Vec3d startPos = mc.player.getEyePos();
        float pitch = mc.player.getPitch();
        float yaw = mc.player.getYaw();

        Vec3d initialVelocity = new Vec3d(
                -Math.sin(Math.toRadians(yaw)) * Math.cos(Math.toRadians(pitch)),
                -Math.sin(Math.toRadians(pitch)),
                Math.cos(Math.toRadians(yaw)) * Math.cos(Math.toRadians(pitch))
        ).multiply(ARROW_SPEED);

        simulateProjectileLanding(startPos, initialVelocity, ProjectileType.ARROW, false, true);
    }

    private void simulateTridentFromHand() {
        if (mc.player == null) return;

        Vec3d startPos = mc.player.getEyePos();
        float pitch = mc.player.getPitch();
        float yaw = mc.player.getYaw();

        Vec3d initialVelocity = new Vec3d(
                -Math.sin(Math.toRadians(yaw)) * Math.cos(Math.toRadians(pitch)),
                -Math.sin(Math.toRadians(pitch)),
                Math.cos(Math.toRadians(yaw)) * Math.cos(Math.toRadians(pitch))
        ).multiply(TRIDENT_SPEED);

        simulateProjectileLanding(startPos, initialVelocity, ProjectileType.TRIDENT, false, true);
    }

    private void simulatePearl(EnderPearlEntity pearl, boolean isPlayerThrown) {
        Vec3d pos = pearl.getPos();
        Vec3d vel = pearl.getVelocity();
        boolean inWater = pearl.isTouchingWater();

        int ticks = 0;
        outer: for (int t = 0; t < MAX_TICKS; t++) {
            for (int s = 0; s < SUBSTEPS; s++) {
                Vec3d prev = pos;
                inWater = isInWater(pos) || inWater;

                double dragPerSub = inWater ? WATER_DRAG_PER_SUBSTEP : AIR_DRAG_PER_SUBSTEP;
                Vec3d step = vel.multiply(PEARL_SPEED_FACTOR / SUBSTEPS);
                pos = pos.add(step);

                Entity hitEntity = null;
                if (isPlayerThrown) {
                    for (CollisionCandidate candidate : collisionCandidates) {
                        Entity entity = candidate.entity();
                        if (entity != pearl && entity != mc.player && candidate.expandedBox().contains(pos)) {
                            hitEntity = entity;
                            projectilePoints.add(new ProjectilePoint(pos, ticks, true, predictionCreationTime, ProjectileType.PEARL, true, hitEntity, isPlayerThrown));
                            break outer;
                        }
                    }
                }

                projectilePoints.add(new ProjectilePoint(pos, ticks, false, predictionCreationTime, ProjectileType.PEARL, true, null, isPlayerThrown));

                RaycastContext ctx = new RaycastContext(prev, pos, RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.ANY, pearl);
                HitResult hit = mc.world.raycast(ctx);
                if (hit.getType() == HitResult.Type.BLOCK) {
                    BlockHitResult bhr = (BlockHitResult) hit;
                    projectilePoints.add(new ProjectilePoint(bhr.getPos(), ticks, true, predictionCreationTime, ProjectileType.PEARL, true, null, isPlayerThrown));
                    break outer;
                }

                if (pos.y < -128) {
                    projectilePoints.add(new ProjectilePoint(pos, ticks, true, predictionCreationTime, ProjectileType.PEARL, true, null, isPlayerThrown));
                    break outer;
                }

                double gravityMultiplier = inWater ? 0.2 : 1.0;
                vel = vel.subtract(0, (PEARL_GRAVITY * PEARL_SPEED_FACTOR * gravityMultiplier) / SUBSTEPS, 0).multiply(dragPerSub);
            }
            ticks++;
        }
    }

    private void simulateArrow(ArrowEntity arrow) {
        Vec3d pos = arrow.getPos();
        Vec3d vel = arrow.getVelocity();
        boolean inWater = arrow.isTouchingWater();

        int ticks = 0;
        outer: for (int t = 0; t < MAX_TICKS; t++) {
            for (int s = 0; s < SUBSTEPS; s++) {
                Vec3d prev = pos;
                inWater = isInWater(pos) || inWater;

                double dragPerSub = inWater ? WATER_DRAG_PER_SUBSTEP : AIR_DRAG_PER_SUBSTEP;
                Vec3d step = vel.multiply(1.0 / SUBSTEPS);
                pos = pos.add(step);

                Entity hitEntity = null;
                for (CollisionCandidate candidate : collisionCandidates) {
                    Entity entity = candidate.entity();
                    if (entity != arrow && entity != mc.player && candidate.expandedBox().contains(pos)) {
                        hitEntity = entity;
                        projectilePoints.add(new ProjectilePoint(pos, ticks, true, predictionCreationTime, ProjectileType.ARROW, true, hitEntity, true));
                        break outer;
                    }
                }

                projectilePoints.add(new ProjectilePoint(pos, ticks, false, predictionCreationTime, ProjectileType.ARROW, true, null, true));

                RaycastContext ctx = new RaycastContext(prev, pos, RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.ANY, arrow);
                HitResult hit = mc.world.raycast(ctx);
                if (hit.getType() == HitResult.Type.BLOCK) {
                    BlockHitResult bhr = (BlockHitResult) hit;
                    projectilePoints.add(new ProjectilePoint(bhr.getPos(), ticks, true, predictionCreationTime, ProjectileType.ARROW, true, null, true));
                    break outer;
                }

                if (pos.y < -128) {
                    projectilePoints.add(new ProjectilePoint(pos, ticks, true, predictionCreationTime, ProjectileType.ARROW, true, null, true));
                    break outer;
                }

                double gravityMultiplier = inWater ? 0.2 : 1.0;
                vel = vel.subtract(0, (ARROW_GRAVITY * gravityMultiplier) / SUBSTEPS, 0).multiply(dragPerSub);
            }
            ticks++;
        }
    }

    private void simulateTrident(TridentEntity trident) {
        Vec3d pos = trident.getPos();
        Vec3d vel = trident.getVelocity();
        boolean inWater = trident.isTouchingWater();

        int ticks = 0;
        outer: for (int t = 0; t < MAX_TICKS; t++) {
            for (int s = 0; s < SUBSTEPS; s++) {
                Vec3d prev = pos;
                inWater = isInWater(pos) || inWater;

                double dragPerSub = inWater ? WATER_DRAG_PER_SUBSTEP : AIR_DRAG_PER_SUBSTEP;
                Vec3d step = vel.multiply(1.0 / SUBSTEPS);
                pos = pos.add(step);

                Entity hitEntity = null;
                for (CollisionCandidate candidate : collisionCandidates) {
                    Entity entity = candidate.entity();
                    if (entity != trident && entity != mc.player && candidate.expandedBox().contains(pos)) {
                        hitEntity = entity;
                        projectilePoints.add(new ProjectilePoint(pos, ticks, true, predictionCreationTime, ProjectileType.TRIDENT, true, hitEntity, true));
                        break outer;
                    }
                }

                projectilePoints.add(new ProjectilePoint(pos, ticks, false, predictionCreationTime, ProjectileType.TRIDENT, true, null, true));

                RaycastContext ctx = new RaycastContext(prev, pos, RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.ANY, trident);
                HitResult hit = mc.world.raycast(ctx);
                if (hit.getType() == HitResult.Type.BLOCK) {
                    BlockHitResult bhr = (BlockHitResult) hit;
                    projectilePoints.add(new ProjectilePoint(bhr.getPos(), ticks, true, predictionCreationTime, ProjectileType.TRIDENT, true, null, true));
                    break outer;
                }

                if (pos.y < -128) {
                    projectilePoints.add(new ProjectilePoint(pos, ticks, true, predictionCreationTime, ProjectileType.TRIDENT, true, null, true));
                    break outer;
                }

                double gravityMultiplier = inWater ? 0.2 : 1.0;
                vel = vel.subtract(0, (TRIDENT_GRAVITY * gravityMultiplier) / SUBSTEPS, 0).multiply(dragPerSub);
            }
            ticks++;
        }
    }

    private void simulateProjectileLanding(Vec3d startPos, Vec3d initialVelocity, ProjectileType type, boolean isMoving, boolean isPlayerThrown) {
        Vec3d pos = startPos;
        Vec3d vel = initialVelocity;

        int ticks = 0;
        boolean inWater = false;

        outer: for (int t = 0; t < MAX_TICKS; t++) {
            for (int s = 0; s < SUBSTEPS; s++) {
                Vec3d prev = pos;
                inWater = isInWater(pos) || inWater;

                double dragPerSub = inWater ? WATER_DRAG_PER_SUBSTEP : AIR_DRAG_PER_SUBSTEP;
                double speedFactor = type == ProjectileType.PEARL ? PEARL_SPEED_FACTOR : 1.0;
                Vec3d step = vel.multiply(speedFactor / SUBSTEPS);
                pos = pos.add(step);

                Entity hitEntity = null;
                if (isPlayerThrown) {
                    for (CollisionCandidate candidate : collisionCandidates) {
                        Entity entity = candidate.entity();
                        if (entity != mc.player && candidate.expandedBox().contains(pos)) {
                            hitEntity = entity;
                            projectilePoints.add(new ProjectilePoint(pos, ticks, true, predictionCreationTime, type, isMoving, hitEntity, isPlayerThrown));
                            break outer;
                        }
                    }
                }

                projectilePoints.add(new ProjectilePoint(pos, ticks, false, predictionCreationTime, type, isMoving, null, isPlayerThrown));

                RaycastContext ctx = new RaycastContext(prev, pos, RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.ANY, mc.player);
                HitResult hit = mc.world.raycast(ctx);
                if (hit.getType() == HitResult.Type.BLOCK) {
                    BlockHitResult bhr = (BlockHitResult) hit;
                    projectilePoints.add(new ProjectilePoint(bhr.getPos(), ticks, true, predictionCreationTime, type, isMoving, null, isPlayerThrown));
                    break outer;
                }

                if (pos.y < -128) {
                    projectilePoints.add(new ProjectilePoint(pos, ticks, true, predictionCreationTime, type, isMoving, null, isPlayerThrown));
                    break outer;
                }

                double gravity;
                switch (type) {
                    case PEARL:
                        gravity = PEARL_GRAVITY;
                        break;
                    case ARROW:
                        gravity = ARROW_GRAVITY;
                        break;
                    case TRIDENT:
                        gravity = TRIDENT_GRAVITY;
                        break;
                    default:
                        gravity = 0.03;
                }

                double gravityMultiplier = inWater ? 0.2 : 1.0;
                vel = vel.subtract(0, (gravity * speedFactor * gravityMultiplier) / SUBSTEPS, 0).multiply(dragPerSub);
            }
            ticks++;
        }
    }

    private boolean isInWater(Vec3d pos) {
        waterCheckPos.set(MathHelper.floor(pos.x), MathHelper.floor(pos.y), MathHelper.floor(pos.z));
        return mc.world.getBlockState(waterCheckPos).getFluidState().isStill();
    }

    private void renderProjectileTrajectory3D(EventRender3D.Game e, boolean hasFlyingProjectiles) {
        // Group previous and current points into trajectories for interpolation
        List<List<ProjectilePoint>> trajectories = cachedTrajectories;
        List<List<ProjectilePoint>> prevTrajectories = cachedPrevTrajectories;
        float tickDelta = e.getTickDelta();

        // Draw in world space via Render3D batching
        Render3D.prepare();
        Render3D.DEBUG_LINE_WIDTH = BASE_LINE_WIDTH;

        for (int g = 0; g < trajectories.size(); g++) {
            List<ProjectilePoint> trajectory = trajectories.get(g);
            List<ProjectilePoint> prevTrajectory = g < prevTrajectories.size() ? prevTrajectories.get(g) : null;
            if (trajectory.size() < 2) continue;

            Vec3d prevPos = interpolatePoint(prevTrajectory, trajectory, 0, tickDelta);
            for (int i = 1; i < trajectory.size(); i++) {
                Vec3d currPos = interpolatePoint(prevTrajectory, trajectory, i, tickDelta);

                // Theme color with smooth alpha fade towards the tail and gradient
                float t = (float) i / (float) trajectory.size();
                
                // Создаем градиент между основным и вторичным цветом
                int r = (int) (currentColor.getRed() * (1.0f - t) + currentColorSecondary.getRed() * t);
                int gg = (int) (currentColor.getGreen() * (1.0f - t) + currentColorSecondary.getGreen() * t);
                int b = (int) (currentColor.getBlue() * (1.0f - t) + currentColorSecondary.getBlue() * t);
                int a = (int) (255.0f * (1.0f - t)); // head opaque -> tail transparent
                a = Math.max(24, Math.min(255, a));
                int argb = (a << 24) | (r << 16) | (gg << 8) | b;

                Render3D.drawLine(prevPos, currPos, argb, BASE_LINE_WIDTH);

                // Draw hit entity box for player-thrown projectiles at landing with gradient
                if (trajectory.get(i).isPlayerThrown() && trajectory.get(i).hitEntity() != null && trajectory.get(i).isLandingPoint()) {
                    Entity hitEntity = trajectory.get(i).hitEntity();
                    if (hitEntity instanceof LivingEntity living && living.isAlive()) {
                        Box box = living.getBoundingBox().expand(0.1);
                        Color fillColor = new Color(r, gg, b, 80);
                        Color outlineColor = new Color(r, gg, b, 255);
                        Render3D.renderBox(e.getMatrices(), box, fillColor);
                        Render3D.renderBoxOutline(e.getMatrices(), box, outlineColor);
                    }
                }

                prevPos = currPos;
            }
        }

        Render3D.render();
    }

    private List<List<ProjectilePoint>> groupTrajectories(List<ProjectilePoint> points) {
        List<List<ProjectilePoint>> out = new ArrayList<>();
        List<ProjectilePoint> currentTrajectory = null;
        ProjectileType lastType = null;
        boolean lastPlayerThrown = false;
        for (ProjectilePoint point : points) {
            if (lastType != point.type() || lastPlayerThrown != point.isPlayerThrown() || currentTrajectory == null) {
                if (currentTrajectory != null && !currentTrajectory.isEmpty()) out.add(currentTrajectory);
                currentTrajectory = new ArrayList<>();
                lastType = point.type();
                lastPlayerThrown = point.isPlayerThrown();
            }
            currentTrajectory.add(point);
        }
        if (currentTrajectory != null && !currentTrajectory.isEmpty()) out.add(currentTrajectory);
        return out;
    }

    private Vec3d interpolatePoint(List<ProjectilePoint> prev, List<ProjectilePoint> curr, int index, float t) {
        Vec3d c = curr.get(index).pos();
        if (prev == null || prev.isEmpty()) return c;
        int prevIndex = Math.min(index, prev.size() - 1);
        Vec3d p = prev.get(prevIndex).pos();
        // Ease-out for smoother perceived motion within tick
        float tt = 1.0f - (float) Math.pow(1.0f - Math.min(Math.max(t, 0.0f), 1.0f), 3.0);
        double x = p.x + (c.x - p.x) * tt;
        double y = p.y + (c.y - p.y) * tt;
        double z = p.z + (c.z - p.z) * tt;
        return new Vec3d(x, y, z);
    }

    private void renderLandingInfo2D(EventRender2D e) {
        MatrixStack ms = e.getContext().getMatrices();
        for (ProjectilePoint pp : projectilePoints) {
            if (pp.isLandingPoint()) {
                Vec3d pos = pp.pos;
                Vec3d lp = WorldUtils.getPosition(pos);
                if (lp.z > 0 && lp.z < 1) {
                    double timeSec = pp.ticks / 20.0;
                    String timeText = String.format("%.1f s", timeSec);

                    int boxX = (int) lp.x - 20;
                    int boxY = (int) lp.y + 4;
                    int boxW = 40;
                    int boxH = 15;
                    int cornerRadius = 3;
                    float fontSize = 7.5f;

                    Render2D.drawRoundedRect(ms, boxX, boxY, boxW, boxH-1, cornerRadius, new Color(0, 0, 0, 150));

                    ms.push();
                    float scale = 0.6f;
                    int itemSize = 16;
                    int scaledItemSize = (int) (itemSize * scale);
                    int itemX = boxX + (boxW / 2) - (scaledItemSize / 2);
                    int itemY = boxY + (boxH / 2) - (scaledItemSize / 2);
                    ms.translate(itemX, itemY, 0);
                    ms.scale(scale, scale, 1.0f);

                    ItemStack itemStack = switch (pp.type()) {
                        case PEARL -> PEARL_STACK;
                        case ARROW -> ARROW_STACK;
                        case TRIDENT -> TRIDENT_STACK;
                    };

                    e.getContext().drawItem(itemStack, 0 - 20, 0 - 1);
                    ms.pop();

                    Render2D.drawFont(
                            ms,
                            Fonts.MEDIUM.getFont(fontSize),
                            timeText,
                            boxX + 18,
                            boxY + boxH / 2f - Fonts.MEDIUM.getHeight(fontSize) / 2f,
                            new Color(255, 255, 255, 255)
                    );
                }
            }
        }
    }

    private void renderPlayerHighlight(EventRender3D.Game e, LivingEntity player) {
        Box box = player.getBoundingBox().expand(0.1);
        Render3D.renderBox(e.getMatrices(), box, new Color(currentColor.getRed(), currentColor.getGreen(), currentColor.getBlue(), 80));
        Render3D.renderBoxOutline(e.getMatrices(), box, currentColor);
    }
}
