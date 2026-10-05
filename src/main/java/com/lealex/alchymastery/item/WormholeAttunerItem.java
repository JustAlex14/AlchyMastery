package com.lealex.alchymastery.item;

import com.lealex.alchyx.item.MachineTool;
import com.lealex.alchymastery.block.entity.DistortionMatrixBlockEntity;
import com.lealex.alchyx.block.entity.MultiblockCoreBlockEntity;
import com.lealex.alchymastery.block.entity.PoweredCoreBlockEntity;
import com.lealex.alchyx.multiblock.CoreTracker;
import com.lealex.alchymastery.registry.ModRegistries;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;

import java.util.function.Consumer;

/**
 * Links a machine to a distortion chamber:
 * 1. right-click a distortion chamber (its matrix or any part) to attune the attuner to it;
 * 2. right-click a machine (its core or any block of its formed structure) to link that machine's wormhole.
 * Sneak + right-click in the air to clear it. The link is stored on the machine; the attuner can link many.
 */
public class WormholeAttunerItem extends Item implements MachineTool {

    public WormholeAttunerItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        if (level.isClientSide()) return InteractionResult.SUCCESS;
        BlockPos clicked = context.getClickedPos();
        Player player = context.getPlayer();
        ItemStack stack = context.getItemInHand();

        // The machine that was clicked: its core, or any block of a formed structure
        MultiblockCoreBlockEntity core = level.getBlockEntity(clicked) instanceof MultiblockCoreBlockEntity direct
                ? direct : CoreTracker.formedCoreAt(level, clicked);
        if (core == null) {
            tell(player, "Right-click a distortion chamber, then the machine to power");
            return InteractionResult.FAIL;
        }

        if (core instanceof DistortionMatrixBlockEntity matrix) {
            stack.set(ModRegistries.ATTUNED_TO.get(), GlobalPos.of(level.dimension(), matrix.getBlockPos()));
            tell(player, "Attuned to the distortion chamber at " + format(matrix.getBlockPos()));
            level.playSound(null, clicked, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 1.0f, 1.2f);
            return InteractionResult.SUCCESS;
        }

        if (!(core instanceof PoweredCoreBlockEntity machine)) {
            tell(player, "This machine doesn't run on distortion energy");
            return InteractionResult.FAIL;
        }
        GlobalPos source = stack.get(ModRegistries.ATTUNED_TO.get());
        if (source == null) {
            tell(player, "Not attuned yet: right-click a distortion chamber first");
            return InteractionResult.FAIL;
        }
        if (!source.dimension().equals(level.dimension())) {
            tell(player, "The attuned chamber is in another dimension");
            return InteractionResult.FAIL;
        }
        int distance = (int) Math.round(Math.sqrt(source.pos().distSqr(machine.getBlockPos())));
        if (distance > PoweredCoreBlockEntity.WORMHOLE_RANGE) {
            tell(player, "Too far: " + distance + " blocks (max " + PoweredCoreBlockEntity.WORMHOLE_RANGE + ")");
            return InteractionResult.FAIL;
        }
        machine.setEnergySource(source.pos());
        tell(player, "Wormhole linked to the distortion chamber at " + format(source.pos()) + " (" + distance + " blocks)");
        level.playSound(null, clicked, SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 0.6f, 1.4f);
        return InteractionResult.SUCCESS;
    }

    // Sneak + right-click in the air: forget the attuned chamber
    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (player.isShiftKeyDown() && stack.has(ModRegistries.ATTUNED_TO.get())) {
            if (!level.isClientSide()) {
                stack.remove(ModRegistries.ATTUNED_TO.get());
                tell(player, "Attunement cleared");
            }
            return InteractionResult.SUCCESS;
        }
        return InteractionResult.PASS;
    }

    // Glows while attuned
    @Override
    public boolean isFoil(ItemStack stack) {
        return stack.has(ModRegistries.ATTUNED_TO.get());
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display,
                                Consumer<Component> tooltip, TooltipFlag flag) {
        GlobalPos source = stack.get(ModRegistries.ATTUNED_TO.get());
        tooltip.accept(source == null
                ? Component.literal("Not attuned").withStyle(ChatFormatting.GRAY)
                : Component.literal("Attuned to " + format(source.pos()) + " (" + source.dimension().identifier() + ")")
                        .withStyle(ChatFormatting.LIGHT_PURPLE));
    }

    private static String format(BlockPos pos) {
        return pos.getX() + " " + pos.getY() + " " + pos.getZ();
    }

    private static void tell(Player player, String message) {
        if (player instanceof ServerPlayer serverPlayer) {
            serverPlayer.sendSystemMessage(Component.literal(message), true);
        }
    }
}
