package com.lealex.alchymastery.command;

import com.lealex.alchymastery.block.entity.DistortionMatrixBlockEntity;
import com.lealex.alchymastery.block.entity.PoweredCoreBlockEntity;
import com.lealex.alchyx.link.Links;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.IdentifierArgument;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.transaction.Transaction;

/**
 * For showcase maps and trailers (operators): what a player does by hand to get machines running, in one go.
 * /alchymastery showcase power [radius]: every formed machine around gets a wormhole to the nearest formed distortion
 * chamber (as with the wormhole attuner). feed <pos> <item> <count> / fill <pos> <fluid> <amount>: put items or a
 * fluid into a machine through its normal input (as a hopper or a pipe would). compound <pos> <material> <count> =
 * real compounds of that material, the same way. target <pos> <item> = a transmutation chamber's target (a material
 * id: its compound) or a nexus's target (an item id), as clicking it into the screen does. charge [amount] [radius]
 * = every formed distortion chamber around holds at least that much energy (default 10,000 DE), so the machines
 * fed by it never starve on camera.
 */
public final class ShowcaseCommands {
    private ShowcaseCommands() {}

    public static void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("alchymastery")
                .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                .then(Commands.literal("showcase")
                        .then(Commands.literal("power")
                                .executes(c -> power(c.getSource(), 48))
                                .then(Commands.argument("radius", IntegerArgumentType.integer(1, 128))
                                        .executes(c -> power(c.getSource(), IntegerArgumentType.getInteger(c, "radius")))))
                        .then(Commands.literal("charge")
                                .executes(c -> charge(c.getSource(), 10_000, 48))
                                .then(Commands.argument("amount", IntegerArgumentType.integer(0, 100_000))
                                        .executes(c -> charge(c.getSource(), IntegerArgumentType.getInteger(c, "amount"), 48))
                                        .then(Commands.argument("radius", IntegerArgumentType.integer(1, 128))
                                                .executes(c -> charge(c.getSource(), IntegerArgumentType.getInteger(c, "amount"),
                                                        IntegerArgumentType.getInteger(c, "radius"))))))
                        .then(Commands.literal("feed").then(Commands.argument("pos", BlockPosArgument.blockPos())
                                .then(Commands.argument("item", IdentifierArgument.id())
                                        .then(Commands.argument("count", IntegerArgumentType.integer(1, 4096))
                                                .executes(c -> feed(c.getSource(), BlockPosArgument.getLoadedBlockPos(c, "pos"),
                                                        IdentifierArgument.getId(c, "item"), IntegerArgumentType.getInteger(c, "count")))))))
                        .then(Commands.literal("compound").then(Commands.argument("pos", BlockPosArgument.blockPos())
                                .then(Commands.argument("material", IdentifierArgument.id())
                                        .then(Commands.argument("count", IntegerArgumentType.integer(1, 4096))
                                                .executes(c -> compound(c.getSource(), BlockPosArgument.getLoadedBlockPos(c, "pos"),
                                                        IdentifierArgument.getId(c, "material"), IntegerArgumentType.getInteger(c, "count")))))))
                        .then(Commands.literal("target").then(Commands.argument("pos", BlockPosArgument.blockPos())
                                .then(Commands.argument("what", IdentifierArgument.id())
                                        .executes(c -> target(c.getSource(), BlockPosArgument.getLoadedBlockPos(c, "pos"),
                                                IdentifierArgument.getId(c, "what"))))))
                        .then(Commands.literal("fill").then(Commands.argument("pos", BlockPosArgument.blockPos())
                                .then(Commands.argument("fluid", IdentifierArgument.id())
                                        .then(Commands.argument("amount", IntegerArgumentType.integer(1, 1_000_000))
                                                .executes(c -> fill(c.getSource(), BlockPosArgument.getLoadedBlockPos(c, "pos"),
                                                        IdentifierArgument.getId(c, "fluid"), IntegerArgumentType.getInteger(c, "amount")))))))));
    }

    private static int power(CommandSourceStack source, int radius) {
        ServerLevel level = source.getLevel();
        BlockPos here = BlockPos.containing(source.getPosition());
        List<DistortionMatrixBlockEntity> chambers = new ArrayList<>();
        List<PoweredCoreBlockEntity> machines = new ArrayList<>();
        Links.forEachNear(level, here, radius, be -> {
            if (be instanceof DistortionMatrixBlockEntity chamber && chamber.isFormed()) chambers.add(chamber);
            else if (be instanceof PoweredCoreBlockEntity machine && machine.isFormed()) machines.add(machine);
        });
        int linked = 0;
        for (PoweredCoreBlockEntity machine : machines) {
            DistortionMatrixBlockEntity best = null;
            double bestDistance = Double.MAX_VALUE;
            for (DistortionMatrixBlockEntity chamber : chambers) {
                double d = chamber.getBlockPos().distSqr(machine.getBlockPos());
                if (d < bestDistance && d <= (double) PoweredCoreBlockEntity.WORMHOLE_RANGE * PoweredCoreBlockEntity.WORMHOLE_RANGE) {
                    best = chamber;
                    bestDistance = d;
                }
            }
            if (best != null) {
                machine.setEnergySource(best.getBlockPos());
                linked++;
            }
        }
        int done = linked;
        source.sendSuccess(() -> Component.literal("Linked " + done + " machine(s) to a distortion chamber"), false);
        return linked;
    }

    private static int charge(CommandSourceStack source, int amount, int radius) {
        int[] charged = {0};
        Links.forEachNear(source.getLevel(), BlockPos.containing(source.getPosition()), radius, be -> {
            if (be instanceof DistortionMatrixBlockEntity chamber && chamber.isFormed()) {
                chamber.chargeAtLeast(amount);
                charged[0]++;
            }
        });
        source.sendSuccess(() -> Component.literal("Charged " + charged[0] + " distortion chamber(s) to " + amount + " DE"), false);
        return charged[0];
    }

    private static int feed(CommandSourceStack source, BlockPos pos, Identifier id, int count) {
        Item item = BuiltInRegistries.ITEM.getValue(id);
        ResourceHandler<ItemResource> handler = source.getLevel().getCapability(Capabilities.Item.BLOCK, pos, null);
        if (handler == null && source.getLevel().getBlockEntity(pos) instanceof com.lealex.alchyx.block.entity.MultiblockCoreBlockEntity core) {
            handler = core.getItemHandler();          // a core that only exposes its input through its shells
        }
        if (handler == null) {
            source.sendFailure(Component.literal("Nothing takes items at " + pos.toShortString()));
            return 0;
        }
        int moved;
        try (Transaction tx = Transaction.openRoot()) {
            moved = handler.insert(ItemResource.of(item), count, tx);
            tx.commit();
        }
        int done = moved;
        source.sendSuccess(() -> Component.literal("Fed " + done + " " + id), false);
        return moved;
    }

    private static int compound(CommandSourceStack source, BlockPos pos, Identifier material, int count) {
        com.lealex.alchymastery.compound.CompoundMaterial data = com.lealex.alchymastery.compound.CompoundMaterials.get(material);
        if (data == null) {
            source.sendFailure(Component.literal("No compound material " + material));
            return 0;
        }
        ItemStack stack = com.lealex.alchymastery.item.CompoundItem.create(material, data, 1);
        ResourceHandler<ItemResource> handler = items(source, pos);
        if (handler == null) {
            source.sendFailure(Component.literal("Nothing takes items at " + pos.toShortString()));
            return 0;
        }
        int moved;
        try (Transaction tx = Transaction.openRoot()) {
            moved = handler.insert(ItemResource.of(stack), count, tx);
            tx.commit();
        }
        int done = moved;
        source.sendSuccess(() -> Component.literal("Fed " + done + " " + material + " compounds"), false);
        return moved;
    }

    private static int target(CommandSourceStack source, BlockPos pos, Identifier what) {
        net.minecraft.world.level.block.entity.BlockEntity be = source.getLevel().getBlockEntity(pos);
        ItemStack stack;
        net.minecraft.world.SimpleContainer slot;
        if (be instanceof com.lealex.alchymastery.block.entity.TransmutationCoreBlockEntity core) {
            com.lealex.alchymastery.compound.CompoundMaterial data = com.lealex.alchymastery.compound.CompoundMaterials.get(what);
            if (data == null) {
                source.sendFailure(Component.literal("No compound material " + what));
                return 0;
            }
            stack = com.lealex.alchymastery.item.CompoundItem.create(what, data, 1);
            slot = core.getTargetContainer();
        } else if (be instanceof com.lealex.alchymastery.block.entity.NexusCoreBlockEntity nexus) {
            stack = new ItemStack(BuiltInRegistries.ITEM.getValue(what));
            slot = nexus.getTargetContainer();
        } else {
            source.sendFailure(Component.literal("No transmutation chamber or nexus core at " + pos.toShortString()));
            return 0;
        }
        slot.setItem(0, stack);
        source.sendSuccess(() -> Component.literal("Target set to " + what), false);
        return 1;
    }

    private static @org.jspecify.annotations.Nullable ResourceHandler<ItemResource> items(CommandSourceStack source, BlockPos pos) {
        ResourceHandler<ItemResource> handler = source.getLevel().getCapability(Capabilities.Item.BLOCK, pos, null);
        if (handler == null && source.getLevel().getBlockEntity(pos) instanceof com.lealex.alchyx.block.entity.MultiblockCoreBlockEntity core) {
            handler = core.getItemHandler();
        }
        return handler;
    }

    private static int fill(CommandSourceStack source, BlockPos pos, Identifier id, int amount) {
        Fluid fluid = BuiltInRegistries.FLUID.getValue(id);
        if (fluid == Fluids.EMPTY) {
            source.sendFailure(Component.literal("No fluid " + id));
            return 0;
        }
        ResourceHandler<FluidResource> handler = source.getLevel().getCapability(Capabilities.Fluid.BLOCK, pos, null);
        if (handler == null && source.getLevel().getBlockEntity(pos) instanceof com.lealex.alchyx.block.entity.MultiblockCoreBlockEntity core) {
            handler = core.getFluidHandler();
        }
        if (handler == null) {
            source.sendFailure(Component.literal("Nothing takes fluids at " + pos.toShortString()));
            return 0;
        }
        int moved;
        try (Transaction tx = Transaction.openRoot()) {
            moved = handler.insert(FluidResource.of(fluid), amount, tx);
            tx.commit();
        }
        int done = moved;
        source.sendSuccess(() -> Component.literal("Filled " + done + " mB of " + id), false);
        return moved;
    }
}
