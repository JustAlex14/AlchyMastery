package com.lealex.alchymastery.registry;

import com.lealex.alchymastery.block.ExperienceTapBlock;
import com.lealex.alchymastery.block.entity.ExperienceTapBlockEntity;
import com.lealex.alchymastery.item.ExperienceTapItem;
import com.lealex.alchymastery.item.ExperienceSiphonItem;
import com.lealex.alchymastery.block.RenderingCoreBlock;
import com.lealex.alchymastery.block.entity.RenderingCoreBlockEntity;
import com.lealex.alchymastery.menu.RenderingMenu;
import com.lealex.alchyx.AlchyX;
import com.lealex.alchyx.registry.AlchyXRegistries;
import com.lealex.alchymastery.Alchymastery;
import com.lealex.alchymastery.block.WormholeBlock;
import com.lealex.alchymastery.block.DestructurationCoreBlock;
import com.lealex.alchymastery.block.TransmutationCoreBlock;
import com.lealex.alchymastery.block.ReconstructionCoreBlock;
import com.lealex.alchymastery.block.CondensatorCoreBlock;
import com.lealex.alchymastery.block.StalactiteBlock;
import com.lealex.alchymastery.block.NexusCoreBlock;
import com.lealex.alchymastery.block.ExperienceNexusBlock;
import com.lealex.alchymastery.block.entity.ExperienceNexusBlockEntity;
import com.lealex.alchymastery.menu.ExperienceNexusMenu;
import com.lealex.alchymastery.block.entity.NexusCoreBlockEntity;
import com.lealex.alchymastery.menu.NexusMenu;
import net.minecraft.world.level.block.piston.PistonBaseBlock;
import net.minecraft.world.level.block.piston.PistonHeadBlock;
import com.lealex.alchymastery.block.entity.CondensatorCoreBlockEntity;
import com.lealex.alchymastery.menu.CondensatorMenu;
import com.lealex.alchymastery.menu.ReconstructionMenu;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.common.SoundActions;
import net.neoforged.neoforge.fluids.BaseFlowingFluid;
import net.neoforged.neoforge.fluids.FluidType;
import net.neoforged.neoforge.registries.NeoForgeRegistries;
import com.lealex.alchymastery.block.entity.ReconstructionCoreBlockEntity;
import com.lealex.alchymastery.block.entity.TransmutationCoreBlockEntity;
import com.lealex.alchymastery.menu.TransmutationMenu;
import com.lealex.alchymastery.block.entity.DestructurationCoreBlockEntity;
import com.lealex.alchymastery.block.DistortionMatrixBlock;
import com.lealex.alchymastery.item.CompoundItem;
import net.minecraft.resources.Identifier;
import com.lealex.alchymastery.item.WormholeAttunerItem;
import com.lealex.alchymastery.menu.DestructurationMenu;
import net.minecraft.core.GlobalPos;
import com.mojang.serialization.Codec;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.core.component.DataComponentType;
import com.lealex.alchymastery.block.entity.DistortionMatrixBlockEntity;
import com.lealex.alchymastery.menu.DistortionMatrixMenu;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.TransparentBlock;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.minecraft.world.level.block.entity.BlockEntityType;

public class ModRegistries {
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(Alchymastery.MODID);
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(Alchymastery.MODID);
    public static final DeferredRegister.DataComponents DATA_COMPONENTS =
            DeferredRegister.createDataComponents(Registries.DATA_COMPONENT_TYPE, Alchymastery.MODID);

    /** Data stored on a wormhole attuner: the distortion chamber (dimension + matrix position) it is attuned to. */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<GlobalPos>> ATTUNED_TO =
            DATA_COMPONENTS.registerComponentType("attuned_to",
                    builder -> builder.persistent(GlobalPos.CODEC).networkSynchronized(GlobalPos.STREAM_CODEC));

    /** Data stored on an experience siphon: the rendering cauldron (dimension + core position) it draws from. */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<GlobalPos>> LINKED_MACHINE =
            DATA_COMPONENTS.registerComponentType("linked_machine",
                    builder -> builder.persistent(GlobalPos.CODEC).networkSynchronized(GlobalPos.STREAM_CODEC));

    /** The material a compound holds, e.g. alchymastery:iron (see compound/CompoundMaterials). */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Identifier>> MATERIAL =
            DATA_COMPONENTS.registerComponentType("material",
                    builder -> builder.persistent(Identifier.CODEC).networkSynchronized(Identifier.STREAM_CODEC));

    /** The compound's tier (copied from its material when made), shown in the tooltip; sets transmutation prices later. */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Integer>> TIER =
            DATA_COMPONENTS.registerComponentType("tier",
                    builder -> builder.persistent(Codec.INT).networkSynchronized(ByteBufCodecs.VAR_INT));

    // ---- Distortion fluid: made by the condensator from water + DE, consumed by the reconstruction chamber ----
    // A real NeoForge fluid, so other mods' pipes and tanks can move and store it, with a bucket and a placeable form.

    public static final DeferredRegister<FluidType> FLUID_TYPES =
            DeferredRegister.create(NeoForgeRegistries.Keys.FLUID_TYPES, Alchymastery.MODID);
    public static final DeferredRegister<Fluid> FLUIDS = DeferredRegister.create(BuiltInRegistries.FLUID, Alchymastery.MODID);

    /** Purple, used for the fluid's tint in the world and in GUIs. */
    public static final int DISTORTION_FLUID_COLOR = 0xFF9B4DFF;

    /** Shared physical properties (the "type"); the two fluids below are its still and flowing forms. */
    public static final DeferredHolder<FluidType, FluidType> DISTORTION_FLUID_TYPE = FLUID_TYPES.register("distortion_fluid",
            () -> new FluidType(FluidType.Properties.create()
                    .descriptionId("block.alchymastery.distortion_fluid")
                    .lightLevel(7)
                    .density(1200)
                    .viscosity(1500)
                    .canConvertToSource(false)
                    .sound(SoundActions.BUCKET_FILL, SoundEvents.BUCKET_FILL)
                    .sound(SoundActions.BUCKET_EMPTY, SoundEvents.BUCKET_EMPTY)
                    .sound(SoundActions.FLUID_VAPORIZE, SoundEvents.FIRE_EXTINGUISH)));

    public static final DeferredHolder<Fluid, BaseFlowingFluid.Source> DISTORTION_FLUID =
            FLUIDS.register("distortion_fluid", () -> new BaseFlowingFluid.Source(distortionFluidProperties()));
    public static final DeferredHolder<Fluid, BaseFlowingFluid.Flowing> DISTORTION_FLUID_FLOWING =
            FLUIDS.register("flowing_distortion_fluid", () -> new BaseFlowingFluid.Flowing(distortionFluidProperties()));

    /** The fluid placed in the world (from a bucket). */
    public static final DeferredBlock<LiquidBlock> DISTORTION_FLUID_BLOCK = BLOCKS.registerBlock("distortion_fluid",
            p -> new LiquidBlock(DISTORTION_FLUID.get(), p),
            p -> p.mapColor(MapColor.COLOR_PURPLE)
                    .replaceable()
                    .noCollision()
                    .strength(100.0F)
                    .pushReaction(PushReaction.DESTROY)
                    .noLootTable()
                    .liquid()
                    .sound(SoundType.EMPTY)
                    .lightLevel(state -> 7));

    public static final DeferredItem<BucketItem> DISTORTION_FLUID_BUCKET = ITEMS.registerItem("distortion_fluid_bucket",
            p -> new BucketItem(DISTORTION_FLUID.get(), p),
            p -> p.craftRemainder(Items.BUCKET).stacksTo(1));

    private static BaseFlowingFluid.Properties distortionFluidProperties() {
        return new BaseFlowingFluid.Properties(DISTORTION_FLUID_TYPE, DISTORTION_FLUID, DISTORTION_FLUID_FLOWING)
                .bucket(DISTORTION_FLUID_BUCKET)
                .block(DISTORTION_FLUID_BLOCK)
                .slopeFindDistance(3)
                .levelDecreasePerBlock(2)
                .tickRate(10);
    }

    // ---- Liquid experience: made by the rendering cauldron from mob essences; in the c:experience tag, so other
    // mods' XP tanks and machines take it (20 mB = 1 experience point, the common convention) ----

    /** Lime green, used for the fluid's tint in the world and in GUIs. */
    public static final int LIQUID_EXPERIENCE_COLOR = 0xFF8BF23E;
    public static final int MB_PER_XP = 20;

    public static final DeferredHolder<FluidType, FluidType> LIQUID_EXPERIENCE_TYPE = FLUID_TYPES.register("liquid_experience",
            () -> new FluidType(FluidType.Properties.create()
                    .descriptionId("block.alchymastery.liquid_experience")
                    .lightLevel(10)
                    .density(800)
                    .viscosity(1000)
                    .canConvertToSource(false)
                    .sound(SoundActions.BUCKET_FILL, SoundEvents.BUCKET_FILL)
                    .sound(SoundActions.BUCKET_EMPTY, SoundEvents.BUCKET_EMPTY)));

    public static final DeferredHolder<Fluid, BaseFlowingFluid.Source> LIQUID_EXPERIENCE =
            FLUIDS.register("liquid_experience", () -> new BaseFlowingFluid.Source(liquidExperienceProperties()));
    public static final DeferredHolder<Fluid, BaseFlowingFluid.Flowing> LIQUID_EXPERIENCE_FLOWING =
            FLUIDS.register("flowing_liquid_experience", () -> new BaseFlowingFluid.Flowing(liquidExperienceProperties()));

    public static final DeferredBlock<LiquidBlock> LIQUID_EXPERIENCE_BLOCK = BLOCKS.registerBlock("liquid_experience",
            p -> new LiquidBlock(LIQUID_EXPERIENCE.get(), p),
            p -> p.mapColor(MapColor.COLOR_LIGHT_GREEN)
                    .replaceable()
                    .noCollision()
                    .strength(100.0F)
                    .pushReaction(PushReaction.DESTROY)
                    .noLootTable()
                    .liquid()
                    .sound(SoundType.EMPTY)
                    .lightLevel(state -> 10));

    public static final DeferredItem<BucketItem> LIQUID_EXPERIENCE_BUCKET = ITEMS.registerItem("liquid_experience_bucket",
            p -> new BucketItem(LIQUID_EXPERIENCE.get(), p),
            p -> p.craftRemainder(Items.BUCKET).stacksTo(1));

    private static BaseFlowingFluid.Properties liquidExperienceProperties() {
        return new BaseFlowingFluid.Properties(LIQUID_EXPERIENCE_TYPE, LIQUID_EXPERIENCE, LIQUID_EXPERIENCE_FLOWING)
                .bucket(LIQUID_EXPERIENCE_BUCKET)
                .block(LIQUID_EXPERIENCE_BLOCK)
                .slopeFindDistance(3)
                .levelDecreasePerBlock(2)
                .tickRate(5);
    }

    public static final DeferredRegister<CreativeModeTab> CREATIVE_MODE_TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, Alchymastery.MODID);

    // The core of the distortion chamber. A plain block for now; it gets a block entity next step.
    public static final DeferredBlock<DistortionMatrixBlock> DISTORTION_MATRIX = BLOCKS.registerBlock("distortion_matrix",
            DistortionMatrixBlock::new,
            p -> p.mapColor(MapColor.COLOR_PURPLE)
                    .strength(3.0f, 6.0f)
                    .sound(SoundType.AMETHYST)
                    .lightLevel(state -> 7));

    // The destructuration chamber's core. Placeholder look: magma (no damage when walked on, unlike magma).
    public static final DeferredBlock<DestructurationCoreBlock> DESTRUCTURATION_CORE = BLOCKS.registerBlock("destructuration_core",
            DestructurationCoreBlock::new,
            p -> p.mapColor(MapColor.NETHER)
                    .strength(3.0f, 6.0f)
                    .sound(SoundType.NETHER_BRICKS)
                    .lightLevel(state -> 3));

    // The transmutation chamber's core. Placeholder look: an enchanting table (without the book); faces the placer.
    public static final DeferredBlock<TransmutationCoreBlock> TRANSMUTATION_CORE = BLOCKS.registerBlock("transmutation_core",
            TransmutationCoreBlock::new,
            p -> p.mapColor(MapColor.COLOR_RED)
                    .strength(3.0f, 6.0f)
                    .sound(SoundType.STONE)
                    .lightLevel(state -> 7)
                    .noOcclusion());

    // The reconstruction chamber's core. Placeholder look: a brewing stand with empty bottles; faces the placer.
    public static final DeferredBlock<ReconstructionCoreBlock> RECONSTRUCTION_CORE = BLOCKS.registerBlock("reconstruction_core",
            ReconstructionCoreBlock::new,
            p -> p.mapColor(MapColor.METAL)
                    .strength(3.0f, 6.0f)
                    .sound(SoundType.METAL)
                    .lightLevel(state -> 1)
                    .noOcclusion());

    // The distortion condensator's core. Looks like a conduit (drawn by its renderer); faces the placer.
    public static final DeferredBlock<CondensatorCoreBlock> CONDENSATOR_CORE = BLOCKS.registerBlock("condensator_core",
            CondensatorCoreBlock::new,
            p -> p.mapColor(MapColor.DIAMOND)
                    .strength(3.0f, 6.0f)
                    .lightLevel(state -> 15)
                    .noOcclusion());

    // The rendering cauldron's core: mob essences + distortion fluid -> liquid experience. Faces the placer.
    public static final DeferredBlock<RenderingCoreBlock> RENDERING_CORE = BLOCKS.registerBlock("rendering_core",
            RenderingCoreBlock::new,
            p -> p.mapColor(MapColor.COLOR_LIGHT_GREEN)
                    .strength(3.0f, 6.0f)
                    .sound(SoundType.DEEPSLATE_TILES)
                    .lightLevel(state -> 6));

    // Experience tap: a copper spout on a rendering cauldron that pours its experience out as orbs
    public static final DeferredBlock<ExperienceTapBlock> EXPERIENCE_TAP = BLOCKS.registerBlock("experience_tap",
            ExperienceTapBlock::new,
            p -> p.mapColor(MapColor.COLOR_ORANGE)
                    .strength(1.0f, 3.0f)
                    .sound(SoundType.COPPER)
                    .noOcclusion());

    // The alchemical nexus: the end-game core at the center of the all-in-one structure. Placeholder look: a beacon.
    public static final DeferredBlock<NexusCoreBlock> ALCHEMICAL_NEXUS = BLOCKS.registerBlock("alchemical_nexus",
            NexusCoreBlock::new,
            p -> p.mapColor(MapColor.DIAMOND)
                    .strength(5.0f, 1200.0f)
                    .sound(SoundType.GLASS)
                    .lightLevel(state -> 15)
                    .noOcclusion());

    // The experience nexus: the experience chain (destructuration, condensator, rendering) in one structure
    public static final DeferredBlock<ExperienceNexusBlock> EXPERIENCE_NEXUS = BLOCKS.registerBlock("experience_nexus",
            ExperienceNexusBlock::new,
            p -> p.mapColor(MapColor.COLOR_CYAN)
                    .strength(5.0f, 1200.0f)
                    .sound(SoundType.SCULK_CATALYST)
                    .lightLevel(state -> 12)
                    .noOcclusion());

    /**
     * The wormhole: placed on a machine's structure (the destructuration chamber's roof center) where the energy
     * from a linked distortion chamber arrives. A normal placeable block; the link itself is stored on the machine.
     */
    public static final DeferredBlock<WormholeBlock> WORMHOLE = BLOCKS.registerBlock("wormhole",
            WormholeBlock::new,
            p -> p.mapColor(MapColor.COLOR_BLACK)
                    .strength(3.0f, 6.0f)
                    .sound(SoundType.AMETHYST)
                    .lightLevel(state -> state.getValue(WormholeBlock.LOOK).light())
                    .noOcclusion());

    /**
     * The distortion crystal: crowns the distortion chamber (roof center, on the upper amethyst block). The block is
     * its pedestal; client/DistortionCrystalRenderer draws the crystal spinning above it.
     */
    public static final DeferredBlock<com.lealex.alchymastery.block.DistortionCrystalBlock> DISTORTION_CRYSTAL = BLOCKS.registerBlock("distortion_crystal",
            com.lealex.alchymastery.block.DistortionCrystalBlock::new,
            p -> p.mapColor(MapColor.COLOR_PURPLE)
                    .strength(3.0f, 6.0f)
                    .sound(SoundType.AMETHYST)
                    .lightLevel(state -> 10)
                    .noOcclusion());

    // ---- Chamber parts: what the frame blocks become during the transformation wave ----
    // No items and no drops: they only exist inside a formed chamber.

    /** Replaces full frame blocks (stone bricks). */
    public static final DeferredBlock<Block> CHAMBER_PART = BLOCKS.registerSimpleBlock("chamber_part",
            p -> p.mapColor(MapColor.COLOR_PURPLE)
                    .strength(1.5f, 6.0f)
                    .sound(SoundType.STONE)
                    .noLootTable());

    /** Replaces stairs; keeps their facing, half and corner shape. */
    public static final DeferredBlock<StairBlock> CHAMBER_PART_STAIRS = BLOCKS.registerBlock("chamber_part_stairs",
            p -> new StairBlock(CHAMBER_PART.get().defaultBlockState(), p),
            p -> p.mapColor(MapColor.COLOR_PURPLE)
                    .strength(1.5f, 6.0f)
                    .sound(SoundType.STONE)
                    .noLootTable());

    /** Replaces the windows; stays see-through. */
    public static final DeferredBlock<TransparentBlock> CHAMBER_PART_GLASS = BLOCKS.registerBlock("chamber_part_glass",
            TransparentBlock::new,
            p -> p.mapColor(MapColor.COLOR_PURPLE)
                    .strength(0.3f)
                    .sound(SoundType.GLASS)
                    .noOcclusion()
                    .isValidSpawn((state, level, pos, type) -> false)
                    .isRedstoneConductor((state, level, pos) -> false)
                    .isSuffocating((state, level, pos) -> false)
                    .isViewBlocking((state, level, pos) -> false)
                    .noLootTable());

    /** Replaces glowstone; keeps giving light. */
    public static final DeferredBlock<Block> CHAMBER_PART_GLOW = BLOCKS.registerSimpleBlock("chamber_part_glow",
            p -> p.mapColor(MapColor.COLOR_PURPLE)
                    .strength(0.3f)
                    .sound(SoundType.GLASS)
                    .lightLevel(state -> 15)
                    .noLootTable());

    /** A look for shells: hanging dripstone recolored to prismarine (the condensator's stalactites). */
    /**
     * Lava as a block model, for the nexus miniatures only (liquids have no block model, so the miniature
     * destructuration chamber's lava pool would be invisible): a glowing lava slab, never placed in the world.
     */
    public static final DeferredBlock<Block> MINIATURE_LAVA = BLOCKS.registerBlock("miniature_lava",
            Block::new,
            p -> p.mapColor(MapColor.FIRE)
                    .noCollision()
                    .noOcclusion()
                    .noLootTable()
                    .lightLevel(state -> 15));

    public static final DeferredBlock<StalactiteBlock> CHAMBER_PART_STALACTITE = BLOCKS.registerBlock("chamber_part_stalactite",
            StalactiteBlock::new,
            p -> p.mapColor(MapColor.COLOR_CYAN)
                    .strength(1.5f, 3.0f)
                    .sound(SoundType.POINTED_DRIPSTONE)
                    .noOcclusion()
                    .noLootTable());

    /**
     * Looks for shells: a piston recolored to nether bricks (the destructuration chamber's pistons) and its head,
     * which the shell renderer slides out during a strike. Plain vanilla piston classes, used only as disguises.
     */
    public static final DeferredBlock<PistonBaseBlock> CHAMBER_PART_PISTON = BLOCKS.registerBlock("chamber_part_piston",
            p -> new PistonBaseBlock(false, p),
            p -> p.mapColor(MapColor.NETHER)
                    .strength(1.5f)
                    .pushReaction(PushReaction.BLOCK)
                    .noLootTable());
    public static final DeferredBlock<PistonHeadBlock> CHAMBER_PART_PISTON_HEAD = BLOCKS.registerBlock("chamber_part_piston_head",
            PistonHeadBlock::new,
            p -> p.mapColor(MapColor.NETHER)
                    .strength(1.5f)
                    .pushReaction(PushReaction.BLOCK)
                    .noLootTable());

    public static final DeferredItem<BlockItem> DISTORTION_MATRIX_ITEM =
            ITEMS.registerSimpleBlockItem("distortion_matrix", DISTORTION_MATRIX);

    public static final DeferredItem<BlockItem> DESTRUCTURATION_CORE_ITEM =
            ITEMS.registerSimpleBlockItem("destructuration_core", DESTRUCTURATION_CORE);

    public static final DeferredItem<BlockItem> TRANSMUTATION_CORE_ITEM =
            ITEMS.registerSimpleBlockItem("transmutation_core", TRANSMUTATION_CORE);

    public static final DeferredItem<BlockItem> RECONSTRUCTION_CORE_ITEM =
            ITEMS.registerSimpleBlockItem("reconstruction_core", RECONSTRUCTION_CORE);

    public static final DeferredItem<BlockItem> RENDERING_CORE_ITEM =
            ITEMS.registerSimpleBlockItem("rendering_core", RENDERING_CORE);

    public static final DeferredItem<ExperienceTapItem> EXPERIENCE_TAP_ITEM =
            ITEMS.registerItem("experience_tap", p -> new ExperienceTapItem(EXPERIENCE_TAP.get(), p), p -> p.useBlockDescriptionPrefix());

    /** Linked to a rendering cauldron, repairs the holder's Mending gear with its experience (inventory or curio slot). */
    /**
     * Machine upgrades: speed_upgrade_1..4, productivity_upgrade_1..4, efficiency_upgrade_1..3, parallel_upgrade
     * (nexus only). In kind order, then tier.
     */
    public static final java.util.List<DeferredItem<com.lealex.alchyx.upgrade.UpgradeItem>> UPGRADES = registerUpgrades();

    private static java.util.List<DeferredItem<com.lealex.alchyx.upgrade.UpgradeItem>> registerUpgrades() {
        java.util.List<DeferredItem<com.lealex.alchyx.upgrade.UpgradeItem>> found = new java.util.ArrayList<>();
        for (com.lealex.alchyx.upgrade.UpgradeType type : com.lealex.alchymastery.upgrade.AlchymasteryUpgrades.ALL) {
            for (int t = 1; t <= type.maxTier(); t++) {
                int tier = t;
                String name = type.id().getPath() + "_upgrade" + (type.maxTier() > 1 ? "_" + tier : "");
                net.minecraft.world.item.Rarity rarity = type == com.lealex.alchymastery.upgrade.AlchymasteryUpgrades.PARALLEL ? net.minecraft.world.item.Rarity.EPIC
                        : tier >= 4 ? net.minecraft.world.item.Rarity.RARE : tier >= 3 ? net.minecraft.world.item.Rarity.UNCOMMON : net.minecraft.world.item.Rarity.COMMON;
                found.add(ITEMS.registerItem(name, p -> new com.lealex.alchyx.upgrade.UpgradeItem(p, type, tier),
                        p -> p.stacksTo(16).rarity(rarity)));
            }
        }
        return java.util.List.copyOf(found);
    }

    public static final DeferredItem<ExperienceSiphonItem> EXPERIENCE_SIPHON =
            ITEMS.registerItem("experience_siphon", ExperienceSiphonItem::new, p -> p.stacksTo(1));

    public static final DeferredItem<BlockItem> CONDENSATOR_CORE_ITEM =
            ITEMS.registerSimpleBlockItem("condensator_core", CONDENSATOR_CORE);

    public static final DeferredItem<BlockItem> ALCHEMICAL_NEXUS_ITEM =
            ITEMS.registerSimpleBlockItem("alchemical_nexus", ALCHEMICAL_NEXUS);

    public static final DeferredItem<BlockItem> EXPERIENCE_NEXUS_ITEM =
            ITEMS.registerSimpleBlockItem("experience_nexus", EXPERIENCE_NEXUS);

    public static final DeferredItem<BlockItem> DISTORTION_CRYSTAL_ITEM =
            ITEMS.registerSimpleBlockItem("distortion_crystal", DISTORTION_CRYSTAL);
    public static final DeferredItem<BlockItem> WORMHOLE_ITEM =
            ITEMS.registerSimpleBlockItem("wormhole", WORMHOLE);

    /** The single compound item; its material is the MATERIAL component. */
    public static final DeferredItem<CompoundItem> COMPOUND = ITEMS.registerItem("compound", CompoundItem::new);

    /** Links machines to a distortion chamber (attune on the chamber, then use on the machine). */
    public static final DeferredItem<WormholeAttunerItem> WORMHOLE_ATTUNER =
            ITEMS.registerItem("wormhole_attuner", WormholeAttunerItem::new, p -> p.stacksTo(1));

    // ---- Crafting items (recipes in data/alchymastery/recipe) ----

    /** Amethyst soaked in distortion fluid: what every machine but the first two is built with. */
    public static final DeferredItem<Item> DISTORTION_SHARD = ITEMS.registerSimpleItem("distortion_shard");

    /** The frame every machine core is built around (shards in polished blackstone, a gold heart). */
    public static final DeferredItem<Item> ALCHEMICAL_LATTICE = ITEMS.registerSimpleItem("alchemical_lattice");

    /** The alchemical nexus's key: a nether star bound with four precious compounds. */
    public static final DeferredItem<Item> NEXUS_HEART = ITEMS.registerSimpleItem("nexus_heart",
            p -> p.rarity(Rarity.EPIC).component(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true));

    /** The experience nexus's key: a nether star bound with echo shards and four rare essences. */
    public static final DeferredItem<Item> SOUL_HEART = ITEMS.registerSimpleItem("soul_heart",
            p -> p.rarity(Rarity.EPIC).component(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true));

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> MAIN_TAB = CREATIVE_MODE_TABS.register("main",
            () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.alchymastery"))
                    .withTabsBefore(CreativeModeTabs.COMBAT)
                    .icon(() -> DISTORTION_MATRIX_ITEM.get().getDefaultInstance())
                    .displayItems((parameters, output) -> {
                        output.accept(DISTORTION_MATRIX_ITEM.get());
                        output.accept(DISTORTION_CRYSTAL_ITEM.get());
                        output.accept(DESTRUCTURATION_CORE_ITEM.get());
                        output.accept(TRANSMUTATION_CORE_ITEM.get());
                        output.accept(RECONSTRUCTION_CORE_ITEM.get());
                        output.accept(CONDENSATOR_CORE_ITEM.get());
                        output.accept(RENDERING_CORE_ITEM.get());
                        output.accept(EXPERIENCE_TAP_ITEM.get());
                        output.accept(EXPERIENCE_SIPHON.get());
                        output.accept(ALCHEMICAL_NEXUS_ITEM.get());
                        output.accept(EXPERIENCE_NEXUS_ITEM.get());
                        output.accept(DISTORTION_FLUID_BUCKET.get());
                        output.accept(LIQUID_EXPERIENCE_BUCKET.get());
                        output.accept(WORMHOLE_ITEM.get());
                        output.accept(DISTORTION_SHARD.get());
                        output.accept(ALCHEMICAL_LATTICE.get());
                        output.accept(NEXUS_HEART.get());
                        output.accept(SOUL_HEART.get());
                        output.accept(AlchyXRegistries.STRUCTURE_BUILDER.get()); // AlchyX's tool, shown in our tab
                        output.accept(WORMHOLE_ATTUNER.get());
                        for (DeferredItem<com.lealex.alchyx.upgrade.UpgradeItem> upgrade : UPGRADES) output.accept(upgrade.get());
                        output.accept(CompoundItem.create(
                                Identifier.fromNamespaceAndPath(Alchymastery.MODID, "iron"), 0xFFD9D9D9, 1, 1));
                    })
                    .build());

    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITY_TYPES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, Alchymastery.MODID);

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<DistortionMatrixBlockEntity>> DISTORTION_MATRIX_BE =
            BLOCK_ENTITY_TYPES.register("distortion_matrix",
                    () -> new BlockEntityType<>(DistortionMatrixBlockEntity::new, false, DISTORTION_MATRIX.get()));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<DestructurationCoreBlockEntity>> DESTRUCTURATION_CORE_BE =
            BLOCK_ENTITY_TYPES.register("destructuration_core",
                    () -> new BlockEntityType<>(DestructurationCoreBlockEntity::new, false, DESTRUCTURATION_CORE.get()));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<TransmutationCoreBlockEntity>> TRANSMUTATION_CORE_BE =
            BLOCK_ENTITY_TYPES.register("transmutation_core",
                    () -> new BlockEntityType<>(TransmutationCoreBlockEntity::new, false, TRANSMUTATION_CORE.get()));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<ReconstructionCoreBlockEntity>> RECONSTRUCTION_CORE_BE =
            BLOCK_ENTITY_TYPES.register("reconstruction_core",
                    () -> new BlockEntityType<>(ReconstructionCoreBlockEntity::new, false, RECONSTRUCTION_CORE.get()));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<RenderingCoreBlockEntity>> RENDERING_CORE_BE =
            BLOCK_ENTITY_TYPES.register("rendering_core",
                    () -> new BlockEntityType<>(RenderingCoreBlockEntity::new, false, RENDERING_CORE.get()));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<ExperienceTapBlockEntity>> EXPERIENCE_TAP_BE =
            BLOCK_ENTITY_TYPES.register("experience_tap",
                    () -> new BlockEntityType<>(ExperienceTapBlockEntity::new, false, EXPERIENCE_TAP.get()));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<com.lealex.alchymastery.block.entity.DistortionCrystalBlockEntity>> DISTORTION_CRYSTAL_BE =
            BLOCK_ENTITY_TYPES.register("distortion_crystal",
                    () -> new BlockEntityType<>(com.lealex.alchymastery.block.entity.DistortionCrystalBlockEntity::new, false, DISTORTION_CRYSTAL.get()));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<com.lealex.alchymastery.block.entity.WormholeBlockEntity>> WORMHOLE_BE =
            BLOCK_ENTITY_TYPES.register("wormhole",
                    () -> new BlockEntityType<>(com.lealex.alchymastery.block.entity.WormholeBlockEntity::new, false, WORMHOLE.get()));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<CondensatorCoreBlockEntity>> CONDENSATOR_CORE_BE =
            BLOCK_ENTITY_TYPES.register("condensator_core",
                    () -> new BlockEntityType<>(CondensatorCoreBlockEntity::new, false, CONDENSATOR_CORE.get()));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<NexusCoreBlockEntity>> ALCHEMICAL_NEXUS_BE =
            BLOCK_ENTITY_TYPES.register("alchemical_nexus",
                    () -> new BlockEntityType<>(NexusCoreBlockEntity::new, false, ALCHEMICAL_NEXUS.get()));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<ExperienceNexusBlockEntity>> EXPERIENCE_NEXUS_BE =
            BLOCK_ENTITY_TYPES.register("experience_nexus",
                    () -> new BlockEntityType<>(ExperienceNexusBlockEntity::new, false, EXPERIENCE_NEXUS.get()));

    public static final DeferredRegister<MenuType<?>> MENU_TYPES =
            DeferredRegister.create(Registries.MENU, Alchymastery.MODID);

    public static final DeferredHolder<MenuType<?>, MenuType<DistortionMatrixMenu>> DISTORTION_MATRIX_MENU =
            MENU_TYPES.register("distortion_matrix", () -> IMenuTypeExtension.create(DistortionMatrixMenu::new));

    public static final DeferredHolder<MenuType<?>, MenuType<DestructurationMenu>> DESTRUCTURATION_MENU =
            MENU_TYPES.register("destructuration_core", () -> IMenuTypeExtension.create(DestructurationMenu::new));

    public static final DeferredHolder<MenuType<?>, MenuType<TransmutationMenu>> TRANSMUTATION_MENU =
            MENU_TYPES.register("transmutation_core", () -> IMenuTypeExtension.create(TransmutationMenu::new));

    public static final DeferredHolder<MenuType<?>, MenuType<RenderingMenu>> RENDERING_MENU =
            MENU_TYPES.register("rendering_core", () -> IMenuTypeExtension.create(RenderingMenu::new));

    public static final DeferredHolder<MenuType<?>, MenuType<CondensatorMenu>> CONDENSATOR_MENU =
            MENU_TYPES.register("condensator_core", () -> IMenuTypeExtension.create(CondensatorMenu::new));

    public static final DeferredHolder<MenuType<?>, MenuType<ReconstructionMenu>> RECONSTRUCTION_MENU =
            MENU_TYPES.register("reconstruction_core", () -> IMenuTypeExtension.create(ReconstructionMenu::new));

    public static final DeferredHolder<MenuType<?>, MenuType<NexusMenu>> ALCHEMICAL_NEXUS_MENU =
            MENU_TYPES.register("alchemical_nexus", () -> IMenuTypeExtension.create(NexusMenu::new));

    public static final DeferredHolder<MenuType<?>, MenuType<ExperienceNexusMenu>> EXPERIENCE_NEXUS_MENU =
            MENU_TYPES.register("experience_nexus", () -> IMenuTypeExtension.create(ExperienceNexusMenu::new));

    public static void register(IEventBus modEventBus) {
        // The shell and the structure builder moved to AlchyX: worlds saved before keep their blocks
        BLOCKS.addAlias(Identifier.fromNamespaceAndPath(Alchymastery.MODID, "chamber_shell"), AlchyX.id("chamber_shell"));
        BLOCK_ENTITY_TYPES.addAlias(Identifier.fromNamespaceAndPath(Alchymastery.MODID, "chamber_shell"), AlchyX.id("chamber_shell"));
        ITEMS.addAlias(Identifier.fromNamespaceAndPath(Alchymastery.MODID, "structure_builder"), AlchyX.id("structure_builder"));
        FLUID_TYPES.register(modEventBus);
        FLUIDS.register(modEventBus);
        DATA_COMPONENTS.register(modEventBus);
        BLOCKS.register(modEventBus);
        ITEMS.register(modEventBus);
        CREATIVE_MODE_TABS.register(modEventBus);
        BLOCK_ENTITY_TYPES.register(modEventBus);
        MENU_TYPES.register(modEventBus);
        ModParticles.register(modEventBus);
    }
}