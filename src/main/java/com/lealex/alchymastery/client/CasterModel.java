package com.lealex.alchymastery.client;

import net.minecraft.client.model.Model;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.util.Mth;

/**
 * A player body (baked from the vanilla player layers, wide or slim arms, skin overlays included) posed by hand:
 * channeling over the end crystal when idle, and the evoker's spell-casting pose while the machine works.
 */
public class CasterModel extends Model<CasterModel.Pose> {

    /** What the pose depends on, filled by the renderer each frame. */
    public static final class Pose {
        public float age;        // ticks, with partial tick
        public boolean casting;  // the machine is working
    }

    private final ModelPart head;
    private final ModelPart rightArm;
    private final ModelPart leftArm;

    public CasterModel(ModelPart root) {
        super(root, RenderTypes::entityTranslucent); // like the player model (skins may have translucent overlays)
        this.head = root.getChild("head");
        this.rightArm = root.getChild("right_arm");
        this.leftArm = root.getChild("left_arm");
    }

    @Override
    public void setupAnim(Pose pose) {
        super.setupAnim(pose); // back to the rest pose
        float age = pose.age;
        if (pose.casting) {
            // The evoker's SPELLCASTING arms (IllagerModel): raised to the sides, swaying
            float sway = Mth.cos(age * 0.6662F) * 0.25F;
            rightArm.x = -5.0F;
            rightArm.z = 0.0F;
            leftArm.x = 5.0F;
            leftArm.z = 0.0F;
            rightArm.xRot = sway;
            leftArm.xRot = sway;
            rightArm.yRot = 0.0F;
            leftArm.yRot = 0.0F;
            rightArm.zRot = (float) (Math.PI * 3.0 / 4.0);
            leftArm.zRot = (float) (-Math.PI * 3.0 / 4.0);
            head.xRot = 0.25F; // eyes on the brew
        } else {
            // Channeling: both hands held out toward the crystal, drifting slowly, head bowed over it
            float drift = Mth.sin(age * 0.06F);
            rightArm.xRot = -1.15F + drift * 0.08F;
            leftArm.xRot = -1.15F - drift * 0.08F;
            rightArm.yRot = -0.25F + Mth.cos(age * 0.045F) * 0.06F;
            leftArm.yRot = 0.25F - Mth.cos(age * 0.045F) * 0.06F;
            head.xRot = 0.45F + Mth.sin(age * 0.05F) * 0.05F;
            head.yRot = Mth.sin(age * 0.03F) * 0.12F;
        }
    }
}
