package dev.prostovisuals.client.custommodels;

import net.minecraft.client.model.ModelData;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.model.ModelPartBuilder;
import net.minecraft.client.model.ModelPartData;
import net.minecraft.client.model.ModelTransform;
import net.minecraft.client.model.TexturedModelData;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.entity.model.EntityModel;
import net.minecraft.client.render.entity.state.PlayerEntityRenderState;
import net.minecraft.util.math.MathHelper;

/**
 * Tralalero Tralala full 3D replacement.
 *
 * Built to match the approved long-bodied four-sneaker reference rather than a
 * normal humanoid player silhouette. The tail is articulated in three sections
 * and all four legs have a two-piece stance so the model reads as a real
 * quadruped in third person.
 */
public final class TralaleroModel extends EntityModel<PlayerEntityRenderState> {
    private final ModelPart body;
    private final ModelPart head;
    private final ModelPart jaw;

    private final ModelPart frontLeftLeg;
    private final ModelPart frontRightLeg;
    private final ModelPart rearLeftLeg;
    private final ModelPart rearRightLeg;

    private final ModelPart leftFin;
    private final ModelPart rightFin;

    private final ModelPart tailRoot;
    private final ModelPart tailMid;
    private final ModelPart tailTip;

    public TralaleroModel() {
        this(getTexturedModelData().createModel());
    }

    private TralaleroModel(ModelPart root) {
        super(root, RenderLayer::getEntityCutoutNoCull);
        body = root.getChild("body");
        head = body.getChild("head");
        jaw = head.getChild("jaw");

        frontLeftLeg = body.getChild("front_left_leg");
        frontRightLeg = body.getChild("front_right_leg");
        rearLeftLeg = body.getChild("rear_left_leg");
        rearRightLeg = body.getChild("rear_right_leg");

        leftFin = body.getChild("left_fin");
        rightFin = body.getChild("right_fin");

        tailRoot = body.getChild("tail_root");
        tailMid = tailRoot.getChild("tail_mid");
        tailTip = tailMid.getChild("tail_tip");
    }

    public static TexturedModelData getTexturedModelData() {
        ModelData data = new ModelData();
        ModelPartData root = data.getRoot();

        // Main long shark silhouette. Separate upper/mid/belly volumes make the
        // side profile much closer to the reference and avoid the old "box fish" look.
        ModelPartData body = root.addChild("body",
                ModelPartBuilder.create()
                        .uv(0, 0).cuboid(-5.9f, -4.4f, -13.8f, 11.8f, 7.0f, 28.0f)
                        .uv(0, 46).cuboid(-5.55f, 2.2f, -13.0f, 11.1f, 2.9f, 25.7f)
                        .uv(0, 74).cuboid(-4.85f, -3.55f, 11.2f, 9.7f, 6.0f, 5.6f),
                ModelTransform.pivot(0.0f, 9.7f, 0.0f));

        // Slight shoulder/neck block that gives the head a cleaner transition.
        body.addChild("neck",
                ModelPartBuilder.create().uv(82, 0)
                        .cuboid(-5.65f, -3.6f, -2.6f, 11.3f, 6.7f, 4.8f),
                ModelTransform.pivot(0.0f, -0.05f, -12.2f));

        // Large wide shark head, snout and chin, modelled as multiple volumes so it
        // resembles the generated reference rather than a single Minecraft cube.
        ModelPartData head = body.addChild("head",
                ModelPartBuilder.create()
                        .uv(82, 14).cuboid(-5.75f, -3.65f, -7.9f, 11.5f, 6.4f, 7.9f)
                        .uv(128, 0).cuboid(-5.35f, -2.65f, -10.8f, 10.7f, 4.5f, 3.6f)
                        .uv(128, 18).cuboid(-4.65f, -1.95f, -12.05f, 9.3f, 3.25f, 2.0f),
                ModelTransform.pivot(0.0f, -0.25f, -13.0f));

        ModelPartData jaw = head.addChild("jaw",
                ModelPartBuilder.create()
                        .uv(82, 31).cuboid(-5.15f, -0.05f, -10.35f, 10.3f, 3.0f, 9.85f)
                        .uv(128, 28).cuboid(-4.7f, -0.55f, -10.0f, 9.4f, 0.75f, 8.95f),
                ModelTransform.of(0.0f, 2.15f, -0.10f, 0.02f, 0.0f, 0.0f));

        // Pink mouth stripe is its own thin 3D slab, so it stays crisp from side angles.
        jaw.addChild("mouth_stripe",
                ModelPartBuilder.create().uv(166, 0)
                        .cuboid(-4.35f, -0.52f, -9.58f, 8.7f, 0.38f, 7.7f),
                ModelTransform.pivot(0.0f, 0.0f, 0.0f));

        // Dark square eyes slightly proud of the surface.
        head.addChild("left_eye",
                ModelPartBuilder.create().uv(176, 18)
                        .cuboid(-0.45f, -0.75f, -0.45f, 0.9f, 1.5f, 0.9f),
                ModelTransform.pivot(5.50f, -1.15f, -6.45f));
        head.addChild("right_eye",
                ModelPartBuilder.create().uv(180, 18)
                        .cuboid(-0.45f, -0.75f, -0.45f, 0.9f, 1.5f, 0.9f),
                ModelTransform.pivot(-5.50f, -1.15f, -6.45f));

        // Tall dorsal fin, shifted slightly toward the front like the target image.
        body.addChild("dorsal_fin",
                ModelPartBuilder.create().uv(84, 48)
                        .cuboid(-1.15f, -7.5f, -3.4f, 2.3f, 7.5f, 6.8f)
                        .uv(103, 48).cuboid(-0.85f, -9.0f, -1.9f, 1.7f, 2.2f, 4.0f),
                ModelTransform.of(0.0f, -3.7f, -2.3f, -0.08f, 0.0f, 0.0f));

        // Side fins angled backward. A root + tip volume creates the chunkier silhouette.
        ModelPartData leftFin = body.addChild("left_fin",
                ModelPartBuilder.create().uv(116, 48)
                        .cuboid(-1.3f, -1.0f, -1.0f, 2.6f, 2.0f, 7.2f)
                        .uv(136, 48).cuboid(-1.05f, -0.8f, 5.5f, 2.1f, 1.6f, 4.3f),
                ModelTransform.of(5.35f, -0.2f, -3.5f, 0.04f, -0.54f, -0.46f));
        ModelPartData rightFin = body.addChild("right_fin",
                ModelPartBuilder.create().mirrored().uv(116, 48)
                        .cuboid(-1.3f, -1.0f, -1.0f, 2.6f, 2.0f, 7.2f)
                        .uv(136, 48).cuboid(-1.05f, -0.8f, 5.5f, 2.1f, 1.6f, 4.3f),
                ModelTransform.of(-5.35f, -0.2f, -3.5f, 0.04f, 0.54f, 0.46f));

        // Three dark gill bars on each visible side. They are actual thin cuboids, not painted pixels.
        addGill(body, "gill_l_1", 5.90f, -0.15f, -2.3f, false);
        addGill(body, "gill_l_2", 5.92f, -0.10f, -0.8f, false);
        addGill(body, "gill_l_3", 5.94f, -0.05f, 0.7f, false);
        addGill(body, "gill_r_1", -5.90f, -0.15f, -2.3f, true);
        addGill(body, "gill_r_2", -5.92f, -0.10f, -0.8f, true);
        addGill(body, "gill_r_3", -5.94f, -0.05f, 0.7f, true);

        // Four correctly separated legs. Front pair sits under the chest, rear pair
        // under the back half. The generated reference has a slightly bent, athletic stance.
        ModelPartData fl = addLeg(body, "front_left_leg", 3.75f, 3.30f, -7.7f, false, -0.10f);
        addShoe(fl, "front_left_shoe", false);
        ModelPartData fr = addLeg(body, "front_right_leg", -3.75f, 3.30f, -7.7f, true, -0.10f);
        addShoe(fr, "front_right_shoe", true);
        ModelPartData rl = addLeg(body, "rear_left_leg", 3.75f, 3.30f, 7.6f, false, 0.07f);
        addShoe(rl, "rear_left_shoe", false);
        ModelPartData rr = addLeg(body, "rear_right_leg", -3.75f, 3.30f, 7.6f, true, 0.07f);
        addShoe(rr, "rear_right_shoe", true);

        // Long three-joint tail, visibly tapered. Fin is vertical like a shark.
        ModelPartData tailRoot = body.addChild("tail_root",
                ModelPartBuilder.create().uv(0, 112)
                        .cuboid(-2.8f, -2.35f, -0.3f, 5.6f, 4.7f, 8.2f),
                ModelTransform.pivot(0.0f, -0.30f, 15.5f));

        ModelPartData tailMid = tailRoot.addChild("tail_mid",
                ModelPartBuilder.create().uv(29, 112)
                        .cuboid(-2.15f, -1.8f, -0.2f, 4.3f, 3.6f, 7.5f),
                ModelTransform.pivot(0.0f, 0.0f, 7.45f));

        ModelPartData tailTip = tailMid.addChild("tail_tip",
                ModelPartBuilder.create().uv(53, 112)
                        .cuboid(-1.45f, -1.25f, -0.1f, 2.9f, 2.5f, 6.4f),
                ModelTransform.pivot(0.0f, 0.0f, 6.75f));

        tailTip.addChild("tail_fin_top",
                ModelPartBuilder.create().uv(74, 112)
                        .cuboid(-1.15f, -7.0f, -0.5f, 2.3f, 7.0f, 5.1f),
                ModelTransform.of(0.0f, 0.0f, 4.7f, 0.0f, 0.0f, -0.10f));
        tailTip.addChild("tail_fin_bottom",
                ModelPartBuilder.create().uv(93, 112)
                        .cuboid(-1.15f, 0.0f, -0.5f, 2.3f, 6.5f, 5.1f),
                ModelTransform.of(0.0f, 0.0f, 4.7f, 0.0f, 0.0f, 0.10f));

        return TexturedModelData.of(data, 256, 256);
    }

    private static void addGill(ModelPartData body, String name, float x, float y, float z, boolean mirrored) {
        ModelPartBuilder b = ModelPartBuilder.create();
        if (mirrored) b = b.mirrored();
        b = b.uv(190, 0).cuboid(-0.18f, -1.55f, -0.28f, 0.36f, 3.1f, 0.56f);
        body.addChild(name, b, ModelTransform.of(x, y, z, 0.0f, 0.0f, mirrored ? 0.12f : -0.12f));
    }

    private static ModelPartData addLeg(ModelPartData body, String name,
                                        float x, float y, float z, boolean mirrored, float pitch) {
        ModelPartBuilder upper = ModelPartBuilder.create();
        if (mirrored) upper = upper.mirrored();
        upper = upper.uv(mirrored ? 142 : 116, 72)
                .cuboid(-1.75f, 0.0f, -1.7f, 3.5f, 5.1f, 3.4f);
        ModelPartData leg = body.addChild(name, upper,
                ModelTransform.of(x, y, z, pitch, 0.0f, mirrored ? -0.035f : 0.035f));

        ModelPartBuilder lower = ModelPartBuilder.create();
        if (mirrored) lower = lower.mirrored();
        lower = lower.uv(mirrored ? 142 : 116, 84)
                .cuboid(-1.55f, 0.0f, -1.45f, 3.1f, 4.5f, 2.9f);
        leg.addChild("shin", lower, ModelTransform.of(0.0f, 4.45f, -0.05f, 0.13f, 0.0f, 0.0f));
        return leg;
    }

    private static void addShoe(ModelPartData leg, String name, boolean mirrored) {
        ModelPartBuilder shoe = ModelPartBuilder.create();
        if (mirrored) shoe = shoe.mirrored();
        shoe = shoe
                // main blue sneaker body
                .uv(mirrored ? 66 : 0, 88).cuboid(-2.9f, -0.35f, -5.6f, 5.8f, 3.35f, 8.25f)
                // raised heel
                .uv(mirrored ? 66 : 0, 102).cuboid(-2.65f, -1.25f, 0.15f, 5.3f, 1.5f, 2.5f)
                // white sole
                .uv(mirrored ? 66 : 0, 106).cuboid(-3.0f, 2.45f, -5.8f, 6.0f, 1.15f, 8.7f)
                // shoe tongue
                .uv(mirrored ? 104 : 92, 94).cuboid(-1.75f, -1.0f, -3.8f, 3.5f, 1.1f, 4.5f);

        ModelPartData shoePart = leg.addChild(name, shoe, ModelTransform.pivot(0.0f, 8.15f, 0.45f));

        // Three dark lace/vent bars on top.
        shoePart.addChild("lace_1", ModelPartBuilder.create().uv(198, 0)
                        .cuboid(-1.65f, -0.18f, -0.28f, 3.3f, 0.36f, 0.56f),
                ModelTransform.of(0.0f, -0.52f, -4.0f, 0.0f, 0.0f, 0.0f));
        shoePart.addChild("lace_2", ModelPartBuilder.create().uv(198, 0)
                        .cuboid(-1.65f, -0.18f, -0.28f, 3.3f, 0.36f, 0.56f),
                ModelTransform.of(0.0f, -0.50f, -3.1f, 0.0f, 0.0f, 0.0f));
        shoePart.addChild("lace_3", ModelPartBuilder.create().uv(198, 0)
                        .cuboid(-1.65f, -0.18f, -0.28f, 3.3f, 0.36f, 0.56f),
                ModelTransform.of(0.0f, -0.48f, -2.2f, 0.0f, 0.0f, 0.0f));

        // White side emblem, deliberately geometric/pixel-like.
        ModelPartBuilder emblem = ModelPartBuilder.create();
        if (mirrored) emblem = emblem.mirrored();
        emblem = emblem.uv(206, 0)
                .cuboid(-0.18f, -0.75f, -0.9f, 0.36f, 1.5f, 1.8f);
        shoePart.addChild("emblem", emblem,
                ModelTransform.of(mirrored ? -2.93f : 2.93f, 1.00f, -0.10f, 0.0f, 0.0f, 0.0f));
    }

    @Override
    public void setAngles(PlayerEntityRenderState state) {
        super.setAngles(state);
        getRootPart().traverse().forEach(ModelPart::resetTransform);

        float age = state.age;
        float walk = state.limbFrequency;
        float move = MathHelper.clamp(state.limbAmplitudeMultiplier, 0.0f, 1.0f);
        boolean swimming = state.isSwimming || state.touchingWater;
        boolean flying = state.isGliding;

        // Diagonal quadruped gait. Lower amplitude than before so the sneakers stay
        // planted and the model does not look drunk/wobbly.
        float stride = MathHelper.cos(walk * 0.6662f) * 0.58f * move;
        float opposite = MathHelper.cos(walk * 0.6662f + MathHelper.PI) * 0.58f * move;
        frontLeftLeg.pitch += stride;
        rearRightLeg.pitch += stride;
        frontRightLeg.pitch += opposite;
        rearLeftLeg.pitch += opposite;

        leftFin.roll += 0.035f * MathHelper.sin(age * 0.10f);
        rightFin.roll -= 0.035f * MathHelper.sin(age * 0.10f);

        float idleWave = MathHelper.sin(age * 0.085f) * 0.032f;
        float moveWave = MathHelper.sin(walk * 0.92f + age * 0.025f) * (0.10f + move * 0.24f);
        float swimWave = MathHelper.sin(age * 0.31f) * 0.47f;
        float flyWave = MathHelper.sin(age * 0.18f) * 0.18f;
        float wave = swimming ? swimWave : flying ? flyWave : (move > 0.04f ? moveWave : idleWave);

        tailRoot.yaw += wave * 0.52f;
        tailMid.yaw += wave * 0.93f;
        tailTip.yaw += wave * 1.36f;

        tailMid.pitch += MathHelper.sin(age * 0.14f + 0.8f) * (swimming ? 0.045f : 0.012f);
        tailTip.pitch += MathHelper.sin(age * 0.14f + 1.35f) * (swimming ? 0.065f : 0.016f);

        jaw.pitch += 0.008f + MathHelper.sin(age * 0.07f) * 0.010f;
        body.pivotY += MathHelper.sin(age * 0.065f) * 0.045f;

        if (state.sneaking) {
            body.pitch += 0.07f;
            body.pivotY += 0.65f;
        }
    }
}
