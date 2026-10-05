package com.lealex.alchymastery.item;

import com.lealex.alchyx.item.MachineTool;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.level.block.Block;

/** The tap's item: a machine tool, so right-clicking a machine places it instead of opening the GUI. */
public class ExperienceTapItem extends BlockItem implements MachineTool {
    public ExperienceTapItem(Block block, Properties properties) {
        super(block, properties);
    }
}
