package com.lealex.alchymastery.item;

import com.lealex.alchymastery.Alchymastery;
import com.lealex.alchymastery.block.entity.ExperienceReservoir;
import com.lealex.alchymastery.registry.ModRegistries;
import com.lealex.alchyx.item.MachineTool;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.item.enchantment.EnchantmentEffectComponents;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import org.jspecify.annotations.Nullable;

import java.util.function.Consumer;

/**
 * Experience siphon: right-click a rendering cauldron (you then appear as its ghost) or an experience nexus to link it. A machine
 * takes one player at a time: linking is refused while someone else holds it.
 * Carried anywhere in your inventory, or worn in a Curios charm slot, it slowly repairs your equipped Mending gear
 * (armor and hands) with the machine's liquid experience, from any distance and dimension while the machine's chunk
 * is loaded: 4 experience points per second, 2 durability per point (vanilla Mending's rate).
 * Sneak + right-click in the air to unlink.
 */
@EventBusSubscriber(modid = Alchymastery.MODID)
public class ExperienceSiphonItem extends Item implements MachineTool {
    public static final int POINTS_PER_SECOND = 4;
    public static final int DURABILITY_PER_POINT = 2;
    private static final EquipmentSlot[] REPAIRED = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS,
            EquipmentSlot.FEET, EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND};

    public ExperienceSiphonItem(Properties properties) {
        super(properties);
    }

    // ---- Linking ----

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        if (level.isClientSide()) return InteractionResult.SUCCESS;
        ExperienceReservoir machine = machineAt(level, context.getClickedPos());
        Player player = context.getPlayer();
        if (machine == null || !(player instanceof ServerPlayer serverPlayer)) return InteractionResult.PASS;
        ItemStack stack = context.getItemInHand();
        if (!machine.link(serverPlayer)) {
            // One player per machine
            serverPlayer.sendSystemMessage(Component.literal("This " + machine.reservoirName() + " is bound to " + machine.getLinkedName()
                    + " (they must unlink their siphon first)").withStyle(ChatFormatting.RED), true);
            return InteractionResult.FAIL;
        }
        ExperienceReservoir old = linkedMachine(serverPlayer, stack);
        if (old != null && old != machine) old.unlink(serverPlayer.getUUID());
        stack.set(ModRegistries.LINKED_MACHINE.get(), GlobalPos.of(level.dimension(), machine.getBlockPos()));
        serverPlayer.sendSystemMessage(Component.literal("Experience siphon linked to the " + machine.reservoirName()), true);
        return InteractionResult.SUCCESS;
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!player.isShiftKeyDown() || !stack.has(ModRegistries.LINKED_MACHINE.get())) return InteractionResult.PASS;
        if (player instanceof ServerPlayer serverPlayer) {
            unlinkOld(serverPlayer, stack);
            stack.remove(ModRegistries.LINKED_MACHINE.get());
            serverPlayer.sendSystemMessage(Component.literal("Experience siphon unlinked"), true);
        }
        return InteractionResult.SUCCESS;
    }

    /** The machine this siphon was linked to forgets the player (its chamber empties), if it's loaded. */
    private static void unlinkOld(ServerPlayer player, ItemStack stack) {
        ExperienceReservoir old = linkedMachine(player, stack);
        if (old != null) old.unlink(player.getUUID());
    }

    /** The rendering cauldron or experience nexus whose core or formed structure is at pos. */
    private static @Nullable ExperienceReservoir machineAt(Level level, BlockPos pos) {
        return ExperienceReservoir.at(level, pos);
    }

    /** The linked machine if its dimension and chunk are loaded (any distance). */
    private static @Nullable ExperienceReservoir linkedMachine(ServerPlayer player, ItemStack stack) {
        GlobalPos target = stack.get(ModRegistries.LINKED_MACHINE.get());
        if (target == null) return null;
        ServerLevel level = player.level().getServer().getLevel(target.dimension());
        if (level == null || !level.isLoaded(target.pos())) return null;
        return level.getBlockEntity(target.pos()) instanceof ExperienceReservoir core ? core : null;
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return stack.has(ModRegistries.LINKED_MACHINE.get());
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display,
                                Consumer<Component> tooltip, TooltipFlag flag) {
        GlobalPos target = stack.get(ModRegistries.LINKED_MACHINE.get());
        if (target == null) {
            tooltip.accept(Component.literal("Right-click a rendering cauldron or experience nexus to link").withStyle(ChatFormatting.GRAY));
        } else {
            BlockPos pos = target.pos();
            tooltip.accept(Component.literal("Linked: " + pos.getX() + " " + pos.getY() + " " + pos.getZ()
                    + " (" + target.dimension().identifier().getPath() + ")").withStyle(ChatFormatting.GRAY));
            tooltip.accept(Component.literal("Repairs your Mending gear with its experience").withStyle(ChatFormatting.DARK_GRAY));
            tooltip.accept(Component.literal("Sneak + right-click in the air to unlink").withStyle(ChatFormatting.DARK_GRAY));
        }
    }

    // ---- Repairing (once per second per player; inventory or Curios slot) ----

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || (player.tickCount + player.getId()) % 20 != 0) return;
        ItemStack siphon = findSiphon(player);
        if (siphon.isEmpty()) return;
        ExperienceReservoir machine = linkedMachine(player, siphon);
        // Only the machine's linked player draws from it (a stale siphon of a previous owner does nothing)
        if (machine == null || !machine.isFormed() || !machine.isLinkedTo(player.getUUID())) return;
        int budget = POINTS_PER_SECOND;
        for (EquipmentSlot slot : REPAIRED) {
            if (budget <= 0) break;
            ItemStack gear = player.getItemBySlot(slot);
            if (gear.isEmpty() || !gear.isDamaged() || !EnchantmentHelper.has(gear, EnchantmentEffectComponents.REPAIR_WITH_XP)) continue;
            int needed = (gear.getDamageValue() + DURABILITY_PER_POINT - 1) / DURABILITY_PER_POINT;
            int points = machine.drainPoints(Math.min(needed, budget));
            if (points <= 0) break; // the machine ran dry
            gear.setDamageValue(Math.max(0, gear.getDamageValue() - points * DURABILITY_PER_POINT));
            budget -= points;
        }
    }

    /** A linked siphon in the player's inventory, or in a Curios slot when Curios is installed. */
    private static ItemStack findSiphon(ServerPlayer player) {
        for (ItemStack stack : player.getInventory()) {
            if (stack.is(ModRegistries.EXPERIENCE_SIPHON.get()) && stack.has(ModRegistries.LINKED_MACHINE.get())) return stack;
        }
        if (ModList.get().isLoaded("curios")) {
            return com.lealex.alchymastery.compat.curios.CuriosCompat.findLinkedSiphon(player);
        }
        return ItemStack.EMPTY;
    }
}
