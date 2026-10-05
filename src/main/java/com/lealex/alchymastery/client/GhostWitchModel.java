package com.lealex.alchymastery.client;

import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.monster.witch.WitchModel;
import net.minecraft.client.renderer.entity.state.WitchRenderState;
import net.minecraft.util.Mth;

/**
 * The vanilla witch, posed for the rendering hut when no player is linked: arms crossed and head bowed over the end
 * crystal when idle; while the machine works she lifts her crossed arms toward the crystal, chanting (arms rocking,
 * head nodding, nose twitching).
 */
public class GhostWitchModel extends WitchModel {

    /** The witch's state plus whether the machine works. */
    public static class State extends WitchRenderState {
        public boolean casting;
    }

    private final ModelPart armsPart;

    public GhostWitchModel(ModelPart root) {
        super(root);
        this.armsPart = root.getChild("arms");
    }

    @Override
    public void setupAnim(WitchRenderState state) {
        super.setupAnim(state);
        float age = state.ageInTicks;
        boolean casting = state instanceof State witch && witch.casting;
        if (casting) {
            armsPart.xRot += -0.75F + Mth.sin(age * 0.3F) * 0.15F;
            getHead().xRot = 0.25F + Mth.sin(age * 0.3F + 1.0F) * 0.08F;
            getNose().xRot = Mth.sin(age * 0.9F) * 0.12F;
        } else {
            armsPart.xRot += Mth.sin(age * 0.05F) * 0.04F;
            getHead().xRot = 0.45F + Mth.sin(age * 0.05F) * 0.05F;
            getHead().yRot = Mth.sin(age * 0.03F) * 0.15F;
        }
    }
}
