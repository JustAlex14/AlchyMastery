package com.lealex.alchymastery.block.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

/**
 * The floating book's animation state, ticked on the client (same logic as vanilla's enchanting table:
 * it turns toward a nearby player, opens, and flips pages). While the machine transmutes it stays open
 * and flips pages much more often. Pure numbers, no client classes: safe in a shared class.
 */
public class BookAnimation {
    public int time;
    public float flip, oFlip, flipT, flipA;
    public float open, oOpen;
    public float rot, oRot, tRot;

    public void tick(Level level, BlockPos pos, boolean working) {
        RandomSource random = level.getRandom();
        oOpen = open;
        oRot = rot;
        Player player = level.getNearestPlayer(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 3.0, false);
        if (player != null || working) {
            if (player != null) {
                tRot = (float) Mth.atan2(player.getZ() - (pos.getZ() + 0.5), player.getX() - (pos.getX() + 0.5));
            } else {
                tRot += 0.02F;
            }
            open += 0.1F;
            if (open < 0.5F || random.nextInt(working ? 6 : 40) == 0) {
                float old = flipT;
                do {
                    flipT += random.nextInt(4) - random.nextInt(4);
                } while (old == flipT);
            }
        } else {
            tRot += 0.02F;
            open -= 0.1F;
        }
        rot = wrap(rot);
        tRot = wrap(tRot);
        rot += wrap(tRot - rot) * 0.4F;
        open = Mth.clamp(open, 0.0F, 1.0F);
        time++;
        oFlip = flip;
        float diff = Mth.clamp((flipT - flip) * 0.4F, -0.2F, 0.2F);
        flipA += (diff - flipA) * 0.9F;
        flip += flipA;
    }

    private static float wrap(float angle) {
        while (angle >= Math.PI) angle -= (float) (Math.PI * 2);
        while (angle < -Math.PI) angle += (float) (Math.PI * 2);
        return angle;
    }
}
