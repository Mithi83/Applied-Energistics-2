/*
 * This file is part of Applied Energistics 2.
 * Copyright (c) 2013 - 2015, AlgorithmX2, All rights reserved.
 *
 * Applied Energistics 2 is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Applied Energistics 2 is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with Applied Energistics 2.  If not, see <http://www.gnu.org/licenses/lgpl>.
 */

package appeng.core.definitions;

import static appeng.block.AEBaseBlock.defaultProps;
import static appeng.block.AEBaseBlock.glassProps;
import static appeng.block.AEBaseBlock.metalProps;
import static appeng.block.AEBaseBlock.stoneProps;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Supplier;

import com.google.common.base.Preconditions;

import org.jetbrains.annotations.Nullable;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.WallBlock;
import net.minecraft.world.level.block.state.BlockBehaviour.Properties;
import net.minecraft.world.level.block.state.BlockBehaviour.StateArgumentPredicate;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.neoforge.registries.DeferredRegister;

import appeng.api.ids.AEBlockIds;
import appeng.block.AEBaseBlock;
import appeng.block.AEBaseBlockItem;
import appeng.block.crafting.CraftingBlockItem;
import appeng.block.crafting.CraftingMonitorBlock;
import appeng.block.crafting.CraftingUnitBlock;
import appeng.block.crafting.CraftingUnitType;
import appeng.block.crafting.MolecularAssemblerBlock;
import appeng.block.crafting.PatternProviderBlock;
import appeng.block.misc.CellWorkbenchBlock;
import appeng.block.misc.ChargerBlock;
import appeng.block.misc.CondenserBlock;
import appeng.block.misc.CrankBlock;
import appeng.block.misc.GrowthAcceleratorBlock;
import appeng.block.misc.InscriberBlock;
import appeng.block.misc.InterfaceBlock;
import appeng.block.misc.LightDetectorBlock;
import appeng.block.misc.MysteriousCubeBlock;
import appeng.block.misc.QuartzFixtureBlock;
import appeng.block.misc.TinyTNTBlock;
import appeng.block.misc.VibrationChamberBlock;
import appeng.block.networking.CableBusBlock;
import appeng.block.networking.ControllerBlock;
import appeng.block.networking.CreativeEnergyCellBlock;
import appeng.block.networking.CrystalResonanceGeneratorBlock;
import appeng.block.networking.EnergyAcceptorBlock;
import appeng.block.networking.EnergyCellBlock;
import appeng.block.networking.EnergyCellBlockItem;
import appeng.block.networking.WirelessAccessPointBlock;
import appeng.block.paint.PaintSplotchesBlock;
import appeng.block.qnb.QuantumLinkChamberBlock;
import appeng.block.qnb.QuantumRingBlock;
import appeng.block.spatial.MatrixFrameBlock;
import appeng.block.spatial.SpatialAnchorBlock;
import appeng.block.spatial.SpatialIOPortBlock;
import appeng.block.spatial.SpatialPylonBlock;
import appeng.block.storage.DriveBlock;
import appeng.block.storage.IOPortBlock;
import appeng.block.storage.MEChestBlock;
import appeng.block.storage.SkyChestBlock;
import appeng.block.storage.SkyStoneTankBlock;
import appeng.core.AppEng;
import appeng.core.MainCreativeTab;
import appeng.debug.CubeGeneratorBlock;
import appeng.debug.EnergyGeneratorBlock;
import appeng.debug.ItemGenBlock;
import appeng.debug.PhantomNodeBlock;
import appeng.decorative.AEDecorativeBlock;
import appeng.decorative.solid.BuddingCertusQuartzBlock;
import appeng.decorative.solid.CertusQuartzClusterBlock;
import appeng.decorative.solid.NotSoMysteriousCubeBlock;
import appeng.decorative.solid.QuartzGlassBlock;
import appeng.decorative.solid.QuartzLampBlock;

/**
 * Internal implementation for the API blocks
 */
public final class AEBlocks {
    public static final DeferredRegister.Blocks DR = DeferredRegister.createBlocks(AppEng.MOD_ID);

    private static final List<BlockDefinition<?>> BLOCKS = new ArrayList<>();
    private static final Properties QUARTZ_CLUSTER_PROPERTIES = defaultProps(MapColor.COLOR_CYAN,
            SoundType.AMETHYST_CLUSTER).forceSolidOn().strength(1.5f).requiresCorrectToolForDrops();
    private static final Properties QUARTZ_PROPERTIES = stoneProps().mapColor(MapColor.COLOR_CYAN)
            .strength(3, 5).requiresCorrectToolForDrops();
    private static final Properties SKYSTONE_PROPERTIES = stoneProps().mapColor(MapColor.COLOR_BLACK)
            .strength(5, 150).requiresCorrectToolForDrops();
    private static final StateArgumentPredicate<EntityType<?>> NEVER_ALLOW_SPAWN = (p1, p2, p3,
            p4) -> false;
    private static final Properties SKY_STONE_CHEST_PROPS = stoneProps().mapColor(MapColor.COLOR_BLACK)
            .strength(5, 150).noOcclusion();
    private static final Properties FLUIX_PROPERTIES = stoneProps().mapColor(MapColor.COLOR_PURPLE)
            .strength(3, 5).requiresCorrectToolForDrops();

    // spotless:off
    public static final BlockDefinition<BuddingCertusQuartzBlock> FLAWLESS_BUDDING_QUARTZ = block("Flawless Budding Certus Quartz", AEBlockIds.FLAWLESS_BUDDING_QUARTZ, () -> new BuddingCertusQuartzBlock(QUARTZ_PROPERTIES.randomTicks()));
    public static final BlockDefinition<BuddingCertusQuartzBlock> FLAWED_BUDDING_QUARTZ = block("Flawed Budding Certus Quartz", AEBlockIds.FLAWED_BUDDING_QUARTZ, () -> new BuddingCertusQuartzBlock(QUARTZ_PROPERTIES.randomTicks()));
    public static final BlockDefinition<BuddingCertusQuartzBlock> CHIPPED_BUDDING_QUARTZ = block("Chipped Budding Certus Quartz", AEBlockIds.CHIPPED_BUDDING_QUARTZ, () -> new BuddingCertusQuartzBlock(QUARTZ_PROPERTIES.randomTicks()));
    public static final BlockDefinition<BuddingCertusQuartzBlock> DAMAGED_BUDDING_QUARTZ = block("Damaged Budding Certus Quartz", AEBlockIds.DAMAGED_BUDDING_QUARTZ, () -> new BuddingCertusQuartzBlock(QUARTZ_PROPERTIES.randomTicks()));
    public static final BlockDefinition<CertusQuartzClusterBlock> SMALL_QUARTZ_BUD = block("Small Certus Quartz Bud", AEBlockIds.SMALL_QUARTZ_BUD, () -> new CertusQuartzClusterBlock(3, 4, QUARTZ_CLUSTER_PROPERTIES.sound(SoundType.SMALL_AMETHYST_BUD).lightLevel(s -> 1)));
    public static final BlockDefinition<CertusQuartzClusterBlock> MEDIUM_QUARTZ_BUD = block("Medium Certus Quartz Bud", AEBlockIds.MEDIUM_QUARTZ_BUD, () -> new CertusQuartzClusterBlock(4, 3, QUARTZ_CLUSTER_PROPERTIES.sound(SoundType.MEDIUM_AMETHYST_BUD).lightLevel(s -> 2)));
    public static final BlockDefinition<CertusQuartzClusterBlock> LARGE_QUARTZ_BUD = block("Large Certus Quartz Bud", AEBlockIds.LARGE_QUARTZ_BUD, () -> new CertusQuartzClusterBlock(5, 3, QUARTZ_CLUSTER_PROPERTIES.sound(SoundType.LARGE_AMETHYST_BUD).lightLevel(s -> 4)));
    public static final BlockDefinition<CertusQuartzClusterBlock> QUARTZ_CLUSTER = block("Certus Quartz Cluster", AEBlockIds.QUARTZ_CLUSTER, () -> new CertusQuartzClusterBlock(7, 3, QUARTZ_CLUSTER_PROPERTIES.sound(SoundType.AMETHYST_CLUSTER).lightLevel(s -> 5)));

    public static final BlockDefinition<MatrixFrameBlock> MATRIX_FRAME = block("Matrix Frame", AEBlockIds.MATRIX_FRAME, MatrixFrameBlock::new);

    public static final BlockDefinition<AEDecorativeBlock> QUARTZ_BLOCK = block("Certus Quartz Block", AEBlockIds.QUARTZ_BLOCK, () -> new AEDecorativeBlock(QUARTZ_PROPERTIES));
    public static final BlockDefinition<AEDecorativeBlock> CUT_QUARTZ_BLOCK = block("Cut Certus Quartz Block", AEBlockIds.CUT_QUARTZ_BLOCK, () -> new AEDecorativeBlock(QUARTZ_PROPERTIES));
    public static final BlockDefinition<AEDecorativeBlock> SMOOTH_QUARTZ_BLOCK = block("Smooth Certus Quartz Block", AEBlockIds.SMOOTH_QUARTZ_BLOCK, () -> new AEDecorativeBlock(QUARTZ_PROPERTIES));
    public static final BlockDefinition<AEDecorativeBlock> QUARTZ_BRICKS = block("Certus Quartz Bricks", AEBlockIds.QUARTZ_BRICKS, () -> new AEDecorativeBlock(QUARTZ_PROPERTIES));
    public static final BlockDefinition<RotatedPillarBlock> QUARTZ_PILLAR = block("Certus Quartz Pillar", AEBlockIds.QUARTZ_PILLAR, () -> new RotatedPillarBlock(QUARTZ_PROPERTIES));
    public static final BlockDefinition<AEDecorativeBlock> CHISELED_QUARTZ_BLOCK = block("Chiseled Certus Quartz Block", AEBlockIds.CHISELED_QUARTZ_BLOCK, () -> new AEDecorativeBlock(QUARTZ_PROPERTIES));

    public static final BlockDefinition<QuartzGlassBlock> QUARTZ_GLASS = block("Quartz Glass", AEBlockIds.QUARTZ_GLASS, () -> new QuartzGlassBlock(glassProps().noOcclusion().isValidSpawn(NEVER_ALLOW_SPAWN)));
    public static final BlockDefinition<QuartzLampBlock> QUARTZ_VIBRANT_GLASS = block("Vibrant Quartz Glass", AEBlockIds.QUARTZ_VIBRANT_GLASS, () -> new QuartzLampBlock(glassProps().lightLevel(b -> 15).noOcclusion()
            .isValidSpawn(NEVER_ALLOW_SPAWN)));

    public static final BlockDefinition<QuartzFixtureBlock> QUARTZ_FIXTURE = block("Charged Quartz Fixture", AEBlockIds.QUARTZ_FIXTURE, QuartzFixtureBlock::new);
    public static final BlockDefinition<AEDecorativeBlock> FLUIX_BLOCK = block("Fluix Block", AEBlockIds.FLUIX_BLOCK, () -> new AEDecorativeBlock(FLUIX_PROPERTIES));
    public static final BlockDefinition<AEDecorativeBlock> SKY_STONE_BLOCK = block("Sky Stone", AEBlockIds.SKY_STONE_BLOCK, () -> new AEDecorativeBlock(stoneProps().strength(50, 150).requiresCorrectToolForDrops()));

    public static final BlockDefinition<AEDecorativeBlock> SMOOTH_SKY_STONE_BLOCK = block("Sky Stone Block", AEBlockIds.SMOOTH_SKY_STONE_BLOCK, () -> new AEDecorativeBlock(SKYSTONE_PROPERTIES));
    public static final BlockDefinition<AEDecorativeBlock> SKY_STONE_BRICK = block("Sky Stone Brick", AEBlockIds.SKY_STONE_BRICK, () -> new AEDecorativeBlock(SKYSTONE_PROPERTIES));
    public static final BlockDefinition<AEDecorativeBlock> SKY_STONE_SMALL_BRICK = block("Sky Stone Small Brick", AEBlockIds.SKY_STONE_SMALL_BRICK, () -> new AEDecorativeBlock(SKYSTONE_PROPERTIES));

    public static final BlockDefinition<SkyChestBlock> SKY_STONE_CHEST = block("Sky Stone Chest", AEBlockIds.SKY_STONE_CHEST, () -> new SkyChestBlock(SkyChestBlock.SkyChestType.STONE, SKY_STONE_CHEST_PROPS));
    public static final BlockDefinition<SkyChestBlock> SMOOTH_SKY_STONE_CHEST = block("Sky Stone Block Chest", AEBlockIds.SMOOTH_SKY_STONE_CHEST, () -> new SkyChestBlock(SkyChestBlock.SkyChestType.BLOCK, SKY_STONE_CHEST_PROPS));

    public static final BlockDefinition<SkyStoneTankBlock> SKY_STONE_TANK = block("Sky Stone Tank", AEBlockIds.SKY_STONE_TANK, () -> new SkyStoneTankBlock(SKY_STONE_CHEST_PROPS));
    public static final BlockDefinition<MysteriousCubeBlock> MYSTERIOUS_CUBE = block("Mysterious Cube", AEBlockIds.MYSTERIOUS_CUBE, MysteriousCubeBlock::new);
    public static final BlockDefinition<NotSoMysteriousCubeBlock> NOT_SO_MYSTERIOUS_CUBE = block("Not So Mysterious Cube", AEBlockIds.NOT_SO_MYSTERIOUS_CUBE, NotSoMysteriousCubeBlock::new);

    public static final BlockDefinition<InscriberBlock> INSCRIBER = block("Inscriber", AEBlockIds.INSCRIBER, () -> new InscriberBlock(metalProps().noOcclusion()));
    public static final BlockDefinition<WirelessAccessPointBlock> WIRELESS_ACCESS_POINT = block("ME Wireless Access Point", AEBlockIds.WIRELESS_ACCESS_POINT, WirelessAccessPointBlock::new);
    public static final BlockDefinition<ChargerBlock> CHARGER = block("Charger", AEBlockIds.CHARGER, ChargerBlock::new);

    public static final BlockDefinition<TinyTNTBlock> TINY_TNT = block("Tiny TNT", AEBlockIds.TINY_TNT, () -> new TinyTNTBlock(defaultProps(MapColor.FIRE, SoundType.GRAVEL).strength(0).noOcclusion()));

    public static final BlockDefinition<QuantumRingBlock> QUANTUM_RING = block("ME Quantum Ring", AEBlockIds.QUANTUM_RING, QuantumRingBlock::new);
    public static final BlockDefinition<QuantumLinkChamberBlock> QUANTUM_LINK = block("ME Quantum Link Chamber", AEBlockIds.QUANTUM_LINK, QuantumLinkChamberBlock::new);
    public static final BlockDefinition<SpatialPylonBlock> SPATIAL_PYLON = block("Spatial Pylon", AEBlockIds.SPATIAL_PYLON, SpatialPylonBlock::new);
    public static final BlockDefinition<SpatialIOPortBlock> SPATIAL_IO_PORT = block("Spatial IO Port", AEBlockIds.SPATIAL_IO_PORT, SpatialIOPortBlock::new);
    public static final BlockDefinition<ControllerBlock> CONTROLLER = block("ME Controller", AEBlockIds.CONTROLLER, ControllerBlock::new);
    public static final BlockDefinition<DriveBlock> DRIVE = block("ME Drive", AEBlockIds.DRIVE, DriveBlock::new);
    public static final BlockDefinition<MEChestBlock> ME_CHEST = block("ME Chest", AEBlockIds.ME_CHEST, MEChestBlock::new);
    public static final BlockDefinition<InterfaceBlock> INTERFACE = block("ME Interface", AEBlockIds.INTERFACE, InterfaceBlock::new);
    public static final BlockDefinition<CellWorkbenchBlock> CELL_WORKBENCH = block("Cell Workbench", AEBlockIds.CELL_WORKBENCH, CellWorkbenchBlock::new);
    public static final BlockDefinition<IOPortBlock> IO_PORT = block("ME IO Port", AEBlockIds.IO_PORT, IOPortBlock::new);
    public static final BlockDefinition<CondenserBlock> CONDENSER = block("Matter Condenser", AEBlockIds.CONDENSER, CondenserBlock::new);
    public static final BlockDefinition<EnergyAcceptorBlock> ENERGY_ACCEPTOR = block("Energy Acceptor", AEBlockIds.ENERGY_ACCEPTOR, EnergyAcceptorBlock::new);
    public static final BlockDefinition<CrystalResonanceGeneratorBlock> CRYSTAL_RESONANCE_GENERATOR = block("Crystal Resonance Generator", AEBlockIds.CRYSTAL_RESONANCE_GENERATOR, CrystalResonanceGeneratorBlock::new);

    public static final BlockDefinition<VibrationChamberBlock> VIBRATION_CHAMBER = block("Vibration Chamber", AEBlockIds.VIBRATION_CHAMBER, VibrationChamberBlock::new);
    public static final BlockDefinition<GrowthAcceleratorBlock> GROWTH_ACCELERATOR = block("Growth Accelerator", AEBlockIds.GROWTH_ACCELERATOR, GrowthAcceleratorBlock::new);
    public static final BlockDefinition<EnergyCellBlock> ENERGY_CELL = block("Energy Cell", AEBlockIds.ENERGY_CELL, () -> new EnergyCellBlock(200000, 800, 200), EnergyCellBlockItem::new);
    public static final BlockDefinition<EnergyCellBlock> DENSE_ENERGY_CELL = block("Dense Energy Cell", AEBlockIds.DENSE_ENERGY_CELL, () -> new EnergyCellBlock(1600000, 1600, 1600), EnergyCellBlockItem::new);
    public static final BlockDefinition<CreativeEnergyCellBlock> CREATIVE_ENERGY_CELL = block("Creative Energy Cell", AEBlockIds.CREATIVE_ENERGY_CELL, CreativeEnergyCellBlock::new);

    public static final BlockDefinition<CraftingUnitBlock> CRAFTING_UNIT = block("Crafting Unit", AEBlockIds.CRAFTING_UNIT, () -> new CraftingUnitBlock(CraftingUnitType.UNIT));
    public static final BlockDefinition<CraftingUnitBlock> CRAFTING_ACCELERATOR = craftingBlock("Crafting Co-Processing Unit", AEBlockIds.CRAFTING_ACCELERATOR, () -> new CraftingUnitBlock(CraftingUnitType.ACCELERATOR));
    public static final BlockDefinition<CraftingUnitBlock> CRAFTING_STORAGE_1K = craftingBlock("1k Crafting Storage", AEBlockIds.CRAFTING_STORAGE_1K, () -> new CraftingUnitBlock(CraftingUnitType.STORAGE_1K));
    public static final BlockDefinition<CraftingUnitBlock> CRAFTING_STORAGE_4K = craftingBlock("4k Crafting Storage",AEBlockIds.CRAFTING_STORAGE_4K, () -> new CraftingUnitBlock(CraftingUnitType.STORAGE_4K));
    public static final BlockDefinition<CraftingUnitBlock> CRAFTING_STORAGE_16K = craftingBlock("16k Crafting Storage", AEBlockIds.CRAFTING_STORAGE_16K, () -> new CraftingUnitBlock(CraftingUnitType.STORAGE_16K));
    public static final BlockDefinition<CraftingUnitBlock> CRAFTING_STORAGE_64K = craftingBlock("64k Crafting Storage", AEBlockIds.CRAFTING_STORAGE_64K, () -> new CraftingUnitBlock(CraftingUnitType.STORAGE_64K));
    public static final BlockDefinition<CraftingUnitBlock> CRAFTING_STORAGE_256K = craftingBlock("256k Crafting Storage", AEBlockIds.CRAFTING_STORAGE_256K, () -> new CraftingUnitBlock(CraftingUnitType.STORAGE_256K));
    public static final BlockDefinition<CraftingMonitorBlock> CRAFTING_MONITOR = craftingBlock("Crafting Monitor",AEBlockIds.CRAFTING_MONITOR, () -> new CraftingMonitorBlock(CraftingUnitType.MONITOR));

    private static <T extends Block> BlockDefinition<T> craftingBlock(String englishName, ResourceLocation id, Supplier<T> blockSupplier) {
        return block(englishName, id, blockSupplier, CraftingBlockItem::new);
    }

    public static final BlockDefinition<PatternProviderBlock> PATTERN_PROVIDER = block("ME Pattern Provider", AEBlockIds.PATTERN_PROVIDER, PatternProviderBlock::new);
    public static final BlockDefinition<MolecularAssemblerBlock> MOLECULAR_ASSEMBLER = block("Molecular Assembler", AEBlockIds.MOLECULAR_ASSEMBLER, () -> new MolecularAssemblerBlock(metalProps().noOcclusion()));

    public static final BlockDefinition<LightDetectorBlock> LIGHT_DETECTOR = block("Light Detecting Fixture", AEBlockIds.LIGHT_DETECTOR, LightDetectorBlock::new);
    public static final BlockDefinition<PaintSplotchesBlock> PAINT = block("Paint", AEBlockIds.PAINT, PaintSplotchesBlock::new);

    public static final BlockDefinition<StairBlock> SKY_STONE_STAIRS = block("Sky Stone Stairs", AEBlockIds.SKY_STONE_STAIRS, () -> new StairBlock(SKY_STONE_BLOCK.block().defaultBlockState(), SKYSTONE_PROPERTIES));
    public static final BlockDefinition<StairBlock> SMOOTH_SKY_STONE_STAIRS = block("Sky Stone Block Stairs", AEBlockIds.SMOOTH_SKY_STONE_STAIRS, () -> new StairBlock(SMOOTH_SKY_STONE_BLOCK.block().defaultBlockState(), SKYSTONE_PROPERTIES));
    public static final BlockDefinition<StairBlock> SKY_STONE_BRICK_STAIRS = block("Sky Stone Brick Stairs", AEBlockIds.SKY_STONE_BRICK_STAIRS, () -> new StairBlock(SKY_STONE_BRICK.block().defaultBlockState(), SKYSTONE_PROPERTIES));
    public static final BlockDefinition<StairBlock> SKY_STONE_SMALL_BRICK_STAIRS = block("Sky Stone Small Brick Stairs", AEBlockIds.SKY_STONE_SMALL_BRICK_STAIRS, () -> new StairBlock(SKY_STONE_SMALL_BRICK.block().defaultBlockState(), SKYSTONE_PROPERTIES));
    public static final BlockDefinition<StairBlock> FLUIX_STAIRS = block("Fluix Stairs", AEBlockIds.FLUIX_STAIRS, () -> new StairBlock(FLUIX_BLOCK.block().defaultBlockState(), FLUIX_PROPERTIES));
    public static final BlockDefinition<StairBlock> QUARTZ_STAIRS = block("Certus Quartz Stairs", AEBlockIds.QUARTZ_STAIRS, () -> new StairBlock(QUARTZ_BLOCK.block().defaultBlockState(), QUARTZ_PROPERTIES));
    public static final BlockDefinition<StairBlock> CUT_QUARTZ_STAIRS = block("Cut Certus Quartz Stairs", AEBlockIds.CUT_QUARTZ_STAIRS, () -> new StairBlock(CUT_QUARTZ_BLOCK.block().defaultBlockState(), QUARTZ_PROPERTIES));
    public static final BlockDefinition<StairBlock> SMOOTH_QUARTZ_STAIRS = block("Smooth Certus Quartz Stairs", AEBlockIds.SMOOTH_QUARTZ_STAIRS, () -> new StairBlock(SMOOTH_QUARTZ_BLOCK.block().defaultBlockState(), QUARTZ_PROPERTIES));
    public static final BlockDefinition<StairBlock> QUARTZ_BRICK_STAIRS = block("Certus Quartz Brick Stairs", AEBlockIds.QUARTZ_BRICK_STAIRS, () -> new StairBlock(QUARTZ_BRICKS.block().defaultBlockState(), QUARTZ_PROPERTIES));
    public static final BlockDefinition<StairBlock> CHISELED_QUARTZ_STAIRS = block("Chiseled Certus Quartz Stairs", AEBlockIds.CHISELED_QUARTZ_STAIRS, () -> new StairBlock(CHISELED_QUARTZ_BLOCK.block().defaultBlockState(), QUARTZ_PROPERTIES));
    public static final BlockDefinition<StairBlock> QUARTZ_PILLAR_STAIRS = block("Certus Quartz Pillar Stairs", AEBlockIds.QUARTZ_PILLAR_STAIRS, () -> new StairBlock(QUARTZ_PILLAR.block().defaultBlockState(), QUARTZ_PROPERTIES));

    public static final BlockDefinition<WallBlock> SKY_STONE_WALL = block("Sky Stone Wall", AEBlockIds.SKY_STONE_WALL, () -> new WallBlock(SKYSTONE_PROPERTIES));
    public static final BlockDefinition<WallBlock> SMOOTH_SKY_STONE_WALL = block("Sky Stone Block Wall", AEBlockIds.SMOOTH_SKY_STONE_WALL, () -> new WallBlock(SKYSTONE_PROPERTIES));
    public static final BlockDefinition<WallBlock> SKY_STONE_BRICK_WALL = block("Sky Stone Brick Wall", AEBlockIds.SKY_STONE_BRICK_WALL, () -> new WallBlock(SKYSTONE_PROPERTIES));
    public static final BlockDefinition<WallBlock> SKY_STONE_SMALL_BRICK_WALL = block("Sky Stone Small Brick Wall", AEBlockIds.SKY_STONE_SMALL_BRICK_WALL, () -> new WallBlock(SKYSTONE_PROPERTIES));
    public static final BlockDefinition<WallBlock> FLUIX_WALL = block("Fluix Wall", AEBlockIds.FLUIX_WALL, () -> new WallBlock(FLUIX_PROPERTIES));
    public static final BlockDefinition<WallBlock> QUARTZ_WALL = block("Certus Quartz Wall", AEBlockIds.QUARTZ_WALL, () -> new WallBlock(QUARTZ_PROPERTIES));
    public static final BlockDefinition<WallBlock> CUT_QUARTZ_WALL = block("Cut Certus Quartz Wall", AEBlockIds.CUT_QUARTZ_WALL, () -> new WallBlock(QUARTZ_PROPERTIES));
    public static final BlockDefinition<WallBlock> SMOOTH_QUARTZ_WALL = block("Smooth Certus Quartz Wall", AEBlockIds.SMOOTH_QUARTZ_WALL, () -> new WallBlock(QUARTZ_PROPERTIES));
    public static final BlockDefinition<WallBlock> QUARTZ_BRICK_WALL = block("Certus Quartz Brick Wall", AEBlockIds.QUARTZ_BRICK_WALL, () -> new WallBlock(QUARTZ_PROPERTIES));
    public static final BlockDefinition<WallBlock> CHISELED_QUARTZ_WALL = block("Chiseled Certus Quartz Wall", AEBlockIds.CHISELED_QUARTZ_WALL, () -> new WallBlock(QUARTZ_PROPERTIES));
    public static final BlockDefinition<WallBlock> QUARTZ_PILLAR_WALL = block("Certus Quartz Pillar Wall", AEBlockIds.QUARTZ_PILLAR_WALL, () -> new WallBlock(QUARTZ_PROPERTIES));

    public static final BlockDefinition<CableBusBlock> CABLE_BUS = block("AE2 Cable and/or Bus", AEBlockIds.CABLE_BUS, CableBusBlock::new);

    public static final BlockDefinition<SlabBlock> SKY_STONE_SLAB = block("Sky Stone Slab", AEBlockIds.SKY_STONE_SLAB, () -> new SlabBlock(SKYSTONE_PROPERTIES));
    public static final BlockDefinition<SlabBlock> SMOOTH_SKY_STONE_SLAB = block("Sky Stone Block Slab", AEBlockIds.SMOOTH_SKY_STONE_SLAB, () -> new SlabBlock(SKYSTONE_PROPERTIES));
    public static final BlockDefinition<SlabBlock> SKY_STONE_BRICK_SLAB = block("Sky Stone Brick Slab", AEBlockIds.SKY_STONE_BRICK_SLAB, () -> new SlabBlock(SKYSTONE_PROPERTIES));
    public static final BlockDefinition<SlabBlock> SKY_STONE_SMALL_BRICK_SLAB = block("Sky Stone Small Brick Slab", AEBlockIds.SKY_STONE_SMALL_BRICK_SLAB, () -> new SlabBlock(SKYSTONE_PROPERTIES));

    public static final BlockDefinition<SlabBlock> FLUIX_SLAB = block("Fluix Slab", AEBlockIds.FLUIX_SLAB, () -> new SlabBlock(FLUIX_PROPERTIES));
    public static final BlockDefinition<SlabBlock> QUARTZ_SLAB = block("Certus Quartz Slab", AEBlockIds.QUARTZ_SLAB, () -> new SlabBlock(QUARTZ_PROPERTIES));
    public static final BlockDefinition<SlabBlock> CUT_QUARTZ_SLAB = block("Cut Certus Quartz Slab", AEBlockIds.CUT_QUARTZ_SLAB, () -> new SlabBlock(QUARTZ_PROPERTIES));
    public static final BlockDefinition<SlabBlock> SMOOTH_QUARTZ_SLAB = block("Smooth Certus Quartz Slab", AEBlockIds.SMOOTH_QUARTZ_SLAB, () -> new SlabBlock(QUARTZ_PROPERTIES));
    public static final BlockDefinition<SlabBlock> QUARTZ_BRICK_SLAB = block("Certus Quartz Brick Slab", AEBlockIds.QUARTZ_BRICK_SLAB, () -> new SlabBlock(QUARTZ_PROPERTIES));
    public static final BlockDefinition<SlabBlock> CHISELED_QUARTZ_SLAB = block("Chiseled Certus Quartz Slab", AEBlockIds.CHISELED_QUARTZ_SLAB, () -> new SlabBlock(QUARTZ_PROPERTIES));
    public static final BlockDefinition<SlabBlock> QUARTZ_PILLAR_SLAB = block("Certus Quartz Pillar Slab", AEBlockIds.QUARTZ_PILLAR_SLAB, () -> new SlabBlock(QUARTZ_PROPERTIES));

    public static final BlockDefinition<SpatialAnchorBlock> SPATIAL_ANCHOR = block("Spatial Anchor", AEBlockIds.SPATIAL_ANCHOR, SpatialAnchorBlock::new);

    ///
    /// DEBUG BLOCKS
    ///
    public static final BlockDefinition<ItemGenBlock> DEBUG_ITEM_GEN = block("Dev.ItemGen", AppEng.makeId("debug_item_gen"), ItemGenBlock::new);
    public static final BlockDefinition<PhantomNodeBlock> DEBUG_PHANTOM_NODE = block("Dev.PhantomNode", AppEng.makeId("debug_phantom_node"), PhantomNodeBlock::new);
    public static final BlockDefinition<CubeGeneratorBlock> DEBUG_CUBE_GEN = block("Dev.CubeGen", AppEng.makeId("debug_cube_gen"), CubeGeneratorBlock::new);
    public static final BlockDefinition<EnergyGeneratorBlock> DEBUG_ENERGY_GEN = block("Dev.EnergyGen", AppEng.makeId("debug_energy_gen"), EnergyGeneratorBlock::new);

    public static final BlockDefinition<CrankBlock> CRANK = block("Wooden Crank", AEBlockIds.CRANK, () -> new CrankBlock(defaultProps(MapColor.WOOD, SoundType.WOOD).isViewBlocking(Blocks::never).noCollission()));

    // spotless:on

    public static List<BlockDefinition<?>> getBlocks() {
        return Collections.unmodifiableList(BLOCKS);
    }

    private static <T extends Block> BlockDefinition<T> block(String englishName, ResourceLocation id,
            Supplier<T> blockSupplier) {
        return block(englishName, id, blockSupplier, null);
    }

    private static <T extends Block> BlockDefinition<T> block(
            String englishName,
            ResourceLocation id,
            Supplier<T> blockSupplier,
            @Nullable BiFunction<Block, Item.Properties, BlockItem> itemFactory) {
        Preconditions.checkArgument(id.getNamespace().equals(AppEng.MOD_ID));

        // Create block and matching item
        var deferredBlock = DR.register(id.getPath(), blockSupplier);
        var deferredItem = AEItems.DR.register(id.getPath(), () -> {
            var block = deferredBlock.get();
            var itemProperties = new Item.Properties();
            if (itemFactory != null) {
                var item = itemFactory.apply(block, itemProperties);
                if (item == null) {
                    throw new IllegalArgumentException("BlockItem factory for " + id + " returned null");
                }
                return item;
            } else if (block instanceof AEBaseBlock) {
                return new AEBaseBlockItem(block, itemProperties);
            } else {
                return new BlockItem(block, itemProperties);
            }
        });

        var itemDef = new ItemDefinition<>(englishName, deferredItem);
        MainCreativeTab.add(itemDef);
        BlockDefinition<T> definition = new BlockDefinition<>(englishName, deferredBlock, itemDef);

        BLOCKS.add(definition);

        return definition;

    }

}
