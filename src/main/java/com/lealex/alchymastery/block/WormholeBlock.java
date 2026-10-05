package com.lealex.alchymastery.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.RandomSource;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.level.Level;
import com.lealex.alchymastery.block.entity.WormholeBlockEntity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * The wormhole block, where a machine receives its distortion energy: a void conduit, drawn by client/WormholeRenderer
 * with vanilla's conduit model parts and its own textures (tools/wormhole_textures.py); its block model is empty
 * except in the PREVIEW state (structure previews draw block models only). Its look follows the machine it belongs
 * to (set by PoweredCoreBlockEntity): {@code unlinked} = a sealed obsidian shell (no reachable distortion chamber, or
 * not part of a formed machine), {@code linked} = the cage turns in a slow vortex, eye closed, {@code active} = linked
 * and the machine draws energy: fast cage, the eye open and glowing.
 */
public class WormholeBlock extends Block implements EntityBlock {
    private static final VoxelShape SHAPE = Block.cube(6.0); // like vanilla's conduit
    /** Only for structure previews (Codex pages, ghost blocks) and the item: shows the shell as a block model. */
    public static final BooleanProperty PREVIEW = BooleanProperty.create("preview");

    public enum Look implements StringRepresentable {
        UNLINKED("unlinked", 4), LINKED("linked", 8), ACTIVE("active", 13);

        private final String id;
        private final int light;

        Look(String id, int light) {
            this.id = id;
            this.light = light;
        }

        public int light() {
            return light;
        }

        @Override
        public String getSerializedName() {
            return id;
        }
    }

    public static final EnumProperty<Look> LOOK = EnumProperty.create("look", Look.class);

    public WormholeBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(LOOK, Look.UNLINKED).setValue(PREVIEW, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(LOOK, PREVIEW);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new WormholeBlockEntity(pos, state);
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        Look look = state.getValue(LOOK);
        int count = switch (look) {
            case UNLINKED -> random.nextInt(3) == 0 ? 1 : 0; // a dormant hole: a mote now and then
            case LINKED -> 2;
            case ACTIVE -> 4;
        };
        for (int i = 0; i < count; i++) {
            // Portal particles are pulled toward their spawn point: start them around the block, they swirl in
            double x = pos.getX() + 0.5 + (random.nextDouble() - 0.5) * 1.6;
            double y = pos.getY() + 0.5 + (random.nextDouble() - 0.5) * 1.6;
            double z = pos.getZ() + 0.5 + (random.nextDouble() - 0.5) * 1.6;
            level.addParticle(ParticleTypes.PORTAL, x, y, z,
                    (pos.getX() + 0.5 - x) * 0.5, (pos.getY() + 0.5 - y) * 0.5, (pos.getZ() + 0.5 - z) * 0.5);
        }
        if (look == Look.ACTIVE && random.nextInt(3) == 0) { // energy arriving: a spark falls into the hole
            level.addParticle(com.lealex.alchymastery.registry.ModParticles.VOID_LINK.get(), pos.getX() + 0.5 + (random.nextDouble() - 0.5) * 0.6,
                    pos.getY() + 1.15, pos.getZ() + 0.5 + (random.nextDouble() - 0.5) * 0.6, 0, -0.03, 0);
        }
    }
}
