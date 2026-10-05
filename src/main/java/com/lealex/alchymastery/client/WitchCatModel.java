package com.lealex.alchymastery.client;

import net.minecraft.client.model.animal.feline.AdultCatModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.entity.state.CatRenderState;
import net.minecraft.util.Mth;

/** The vanilla cat, sitting, with a lazily flicking tail (a sitting cat's tail is otherwise still). */
public class WitchCatModel extends AdultCatModel {
    public WitchCatModel(ModelPart root) {
        super(root);
    }

    @Override
    public void setupAnim(CatRenderState state) {
        super.setupAnim(state);
        float age = state.ageInTicks;
        // a slow sway, with a quick flick now and then
        float flick = Mth.sin(age * 0.6F) * Math.max(0.0F, Mth.sin(age * 0.05F) - 0.8F) * 2.0F;
        tail2.yRot = Mth.sin(age * 0.08F) * 0.25F + flick;
        tail1.yRot = Mth.sin(age * 0.08F - 0.6F) * 0.1F;
    }
}
