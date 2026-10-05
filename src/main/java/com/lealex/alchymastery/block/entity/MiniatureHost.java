package com.lealex.alchymastery.block.entity;

import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * A nexus-type core that shows animated miniatures of the machines around it (the alchemical nexus, the experience
 * nexus). NexusCoreRenderer draws any of them; each machine is named like its key in the pattern's anchors
 * ("destructuration", "transmutation", "condensator", "reconstruction", "rendering").
 */
public interface MiniatureHost {
    /** The miniatures to draw (computed on the server from the machines' own patterns, synced). */
    List<NexusCoreBlockEntity.Miniature> getMiniatures();

    /** Client: is this machine's stage working right now. */
    boolean isMachineWorking(String machine);

    /** Client: 0..1 through this machine's current operation. */
    float getMachineProgress(String machine);

    /**
     * Items floating over a machine's miniature (see NexusCoreRenderer#submitItems for how each machine shows them):
     * A is the "before" item, B the "after" one. Empty for none.
     */
    ItemStack miniatureItemA(String machine);

    ItemStack miniatureItemB(String machine);

    /** The transmutation miniature's book, or null if this host has no transmutation stage. */
    default @Nullable BookAnimation book() {
        return null;
    }
}
