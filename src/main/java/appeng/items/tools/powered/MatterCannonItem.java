/*
 * This file is part of Applied Energistics 2.
 * Copyright (c) 2013 - 2014, AlgorithmX2, All rights reserved.
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

package appeng.items.tools.powered;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.Sheep;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.tooltip.TooltipComponent;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.ClipContext.Block;
import net.minecraft.world.level.ClipContext.Fluid;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.HitResult.Type;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.BlockSnapshot;
import net.neoforged.neoforge.event.EventHooks;
import net.neoforged.neoforge.event.level.BlockEvent;

import appeng.api.config.Actionable;
import appeng.api.config.FuzzyMode;
import appeng.api.ids.AEComponents;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.AEKeyType;
import appeng.api.storage.StorageCells;
import appeng.api.storage.cells.IBasicCellItem;
import appeng.api.upgrades.IUpgradeInventory;
import appeng.api.upgrades.UpgradeInventories;
import appeng.api.upgrades.Upgrades;
import appeng.api.util.AEColor;
import appeng.api.util.DimensionalBlockPos;
import appeng.blockentity.misc.PaintSplotchesBlockEntity;
import appeng.core.AEConfig;
import appeng.core.AppEng;
import appeng.core.definitions.AEBlocks;
import appeng.core.definitions.AEDamageTypes;
import appeng.core.definitions.AEItems;
import appeng.core.localization.PlayerMessages;
import appeng.core.network.clientbound.MatterCannonPacket;
import appeng.items.contents.CellConfig;
import appeng.items.misc.PaintBallItem;
import appeng.items.tools.powered.powersink.AEBasePoweredItem;
import appeng.me.helpers.PlayerSource;
import appeng.recipes.AERecipeTypes;
import appeng.util.ConfigInventory;
import appeng.util.InteractionUtil;
import appeng.util.LookDirection;
import appeng.util.Platform;

public class MatterCannonItem extends AEBasePoweredItem implements IBasicCellItem {

    private static final Logger LOG = LoggerFactory.getLogger(MatterCannonItem.class);

    /**
     * AE energy units consumer per shot fired.
     */
    private static final int ENERGY_PER_SHOT = 1600;

    public MatterCannonItem(Properties props) {
        super(AEConfig.instance().getMatterCannonBattery(), props);
    }

    @Override
    public double getChargeRate(ItemStack stack) {
        return 800d + 800d * Upgrades.getEnergyCardMultiplier(getUpgrades(stack));
    }

    @OnlyIn(Dist.CLIENT)
    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> lines,
            TooltipFlag advancedTooltips) {
        super.appendHoverText(stack, context, lines, advancedTooltips);
        addCellInformationToTooltip(stack, lines);
    }

    @Override
    public Optional<TooltipComponent> getTooltipImage(ItemStack stack) {
        return getCellTooltipImage(stack);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player p, InteractionHand hand) {
        var stack = p.getItemInHand(hand);

        var direction = InteractionUtil.getPlayerRay(p, 255);

        if (fireCannon(level, stack, p, direction)) {
            return new InteractionResultHolder<>(InteractionResult.sidedSuccess(level.isClientSide()),
                    stack);
        } else {
            return new InteractionResultHolder<>(InteractionResult.FAIL, stack);
        }
    }

    public boolean fireCannon(Level level, ItemStack stack, Player player, LookDirection dir) {

        var inv = StorageCells.getCellInventory(stack, null);
        if (inv == null) {
            return false;
        }

        var itemList = inv.getAvailableStacks();
        var req = itemList.getFirstEntry(AEItemKey.class);
        if (req == null || !(req.getKey() instanceof AEItemKey itemKey)) {
            if (!level.isClientSide()) {
                player.displayClientMessage(PlayerMessages.AmmoDepleted.text(), true);
            }
            return true;
        }

        int shotPower = 1;
        var cu = getUpgrades(stack);
        if (cu != null) {
            shotPower += cu.getInstalledUpgrades(AEItems.SPEED_CARD);
        }
        shotPower = Math.min(shotPower, (int) req.getLongValue());

        if (getAECurrentPower(stack) < ENERGY_PER_SHOT) {
            return false;
        }

        shotPower = Math.min(shotPower, (int) getAECurrentPower(stack) / ENERGY_PER_SHOT);

        extractAEPower(stack, ENERGY_PER_SHOT * shotPower, Actionable.MODULATE);

        if (level.isClientSide()) {
            // Up until this point, we can simulate on the client, after this,
            // we need to run the server-side version
            return true;
        }

        var aeAmmo = inv.extract(req.getKey(), 1, Actionable.MODULATE, new PlayerSource(player));
        if (aeAmmo == 0) {
            return true;
        }

        var rayFrom = dir.getA();
        var rayTo = dir.getB();
        var direction = rayTo.subtract(rayFrom);
        direction.normalize();

        var x = rayFrom.x;
        var y = rayFrom.y;
        var z = rayFrom.z;

        var penetration = getPenetration(itemKey) * shotPower; // 196.96655f;
        if (penetration <= 0) {
            if (itemKey.getItem() instanceof PaintBallItem paintBallItem) {
                shootPaintBalls(paintBallItem.getColor(), paintBallItem.isLumen(), level, player, rayFrom, rayTo,
                        direction, x, y, z);
                return true;
            }
        } else {
            standardAmmo(penetration, level, player, rayFrom, rayTo, direction, x, y, z);
        }

        return true;
    }

    private void shootPaintBalls(AEColor color, boolean lit, Level level, @Nullable Player p, Vec3 Vector3d,
            Vec3 Vector3d1, Vec3 direction, double d0, double d1, double d2) {
        final AABB bb = new AABB(Math.min(Vector3d.x, Vector3d1.x), Math.min(Vector3d.y, Vector3d1.y),
                Math.min(Vector3d.z, Vector3d1.z), Math.max(Vector3d.x, Vector3d1.x), Math.max(Vector3d.y, Vector3d1.y),
                Math.max(Vector3d.z, Vector3d1.z)).inflate(16, 16, 16);

        Entity entity = null;
        Vec3 entityIntersection = null;
        final List<Entity> list = level.getEntities(p, bb,
                e -> !(e instanceof ItemEntity) && e.isAlive());
        double closest = 9999999.0D;

        for (Entity entity1 : list) {
            if (p.isPassenger() && entity1.hasPassenger(p)) {
                continue;
            }

            final float f1 = 0.3F;

            final AABB boundingBox = entity1.getBoundingBox().inflate(f1, f1, f1);
            final Vec3 intersection = boundingBox.clip(Vector3d, Vector3d1).orElse(null);

            if (intersection != null) {
                final double nd = Vector3d.distanceToSqr(intersection);

                if (nd < closest) {
                    entity = entity1;
                    entityIntersection = intersection;
                    closest = nd;
                }
            }
        }

        ClipContext rayTraceContext = new ClipContext(Vector3d, Vector3d1, Block.COLLIDER,
                Fluid.NONE, p);
        HitResult pos = level.clip(rayTraceContext);

        final Vec3 vec = new Vec3(d0, d1, d2);
        if (entity != null && pos.getType() != Type.MISS
                && pos.getLocation().distanceToSqr(vec) > closest) {
            pos = new EntityHitResult(entity, entityIntersection);
        } else if (entity != null && pos.getType() == Type.MISS) {
            pos = new EntityHitResult(entity, entityIntersection);
        }

        AppEng.instance().sendToAllNearExcept(null, d0, d1, d2, 256, level,
                new MatterCannonPacket(d0, d1, d2, (float) direction.x, (float) direction.y, (float) direction.z,
                        (byte) (pos.getType() == Type.MISS ? 32
                                : pos.getLocation().distanceToSqr(vec) + 1)));

        if (pos.getType() != Type.MISS) {
            if (pos instanceof EntityHitResult entityResult) {
                var entityHit = entityResult.getEntity();

                if (entityHit instanceof Sheep sh) {
                    sh.setColor(color.dye);
                }

                entityHit.hurt(level.damageSources().playerAttack(p), 0);
            } else if (pos instanceof BlockHitResult blockResult) {
                final Direction side = blockResult.getDirection();
                final BlockPos hitPos = blockResult.getBlockPos().relative(side);

                if (!Platform.hasPermissions(new DimensionalBlockPos(level, hitPos), p)) {
                    return;
                }

                if (EventHooks.onBlockPlace(p, BlockSnapshot.create(p.level().dimension(), level, hitPos),
                        blockResult.getDirection())) {
                    return;
                }

                final BlockState whatsThere = level.getBlockState(hitPos);
                if (whatsThere.canBeReplaced() && level.isEmptyBlock(hitPos)) {
                    level.setBlock(hitPos, AEBlocks.PAINT.block().defaultBlockState(), 3);
                }

                final BlockEntity te = level.getBlockEntity(hitPos);
                if (te instanceof PaintSplotchesBlockEntity) {
                    final Vec3 hp = pos.getLocation().subtract(hitPos.getX(), hitPos.getY(), hitPos.getZ());
                    ((PaintSplotchesBlockEntity) te).addBlot(color, lit, side.getOpposite(), hp);
                }
            }
        }
    }

    private void standardAmmo(float penetration, Level level, Player p, Vec3 Vector3d,
            Vec3 Vector3d1, Vec3 direction, double d0, double d1, double d2) {
        boolean hasDestroyed = true;
        while (penetration > 0 && hasDestroyed) {
            hasDestroyed = false;

            final AABB bb = new AABB(Math.min(Vector3d.x, Vector3d1.x),
                    Math.min(Vector3d.y, Vector3d1.y), Math.min(Vector3d.z, Vector3d1.z),
                    Math.max(Vector3d.x, Vector3d1.x), Math.max(Vector3d.y, Vector3d1.y),
                    Math.max(Vector3d.z, Vector3d1.z)).inflate(16, 16, 16);

            Entity entity = null;
            Vec3 entityIntersection = null;
            var list = level.getEntities(p, bb, e -> !(e instanceof ItemEntity) && e.isAlive());
            double closest = 9999999.0D;

            for (Entity entity1 : list) {
                // Do not shoot your horse.
                if (p.isPassenger() && entity1.hasPassenger(p)) {
                    continue;
                }

                final float f1 = 0.3F;

                final AABB boundingBox = entity1.getBoundingBox().inflate(f1, f1, f1);
                final Vec3 intersection = boundingBox.clip(Vector3d, Vector3d1).orElse(null);

                if (intersection != null) {
                    final double nd = Vector3d.distanceToSqr(intersection);

                    if (nd < closest) {
                        entity = entity1;
                        entityIntersection = intersection;
                        closest = nd;
                    }
                }
            }

            ClipContext rayTraceContext = new ClipContext(Vector3d, Vector3d1,
                    Block.COLLIDER, Fluid.NONE, p);
            final Vec3 vec = new Vec3(d0, d1, d2);
            HitResult pos = level.clip(rayTraceContext);
            if (entity != null && pos.getType() != Type.MISS
                    && pos.getLocation().distanceToSqr(vec) > closest) {
                pos = new EntityHitResult(entity, entityIntersection);
            } else if (entity != null && pos.getType() == Type.MISS) {
                pos = new EntityHitResult(entity, entityIntersection);
            }

            AppEng.instance().sendToAllNearExcept(null, d0, d1, d2, 256, level,
                    new MatterCannonPacket(d0, d1, d2, (float) direction.x, (float) direction.y,
                            (float) direction.z, (byte) (pos.getType() == Type.MISS ? 32
                                    : pos.getLocation().distanceToSqr(vec) + 1)));

            if (pos.getType() != Type.MISS) {
                var dmgSrc = level.damageSources().source(AEDamageTypes.MATTER_CANNON, p);

                if (pos instanceof EntityHitResult entityResult) {
                    Entity entityHit = entityResult.getEntity();

                    final int dmg = getDamageFromPenetration(penetration);
                    if (entityHit instanceof LivingEntity el) {
                        penetration -= dmg;
                        if (el.hurt(dmgSrc, dmg)) {
                            el.knockback(0, -direction.x, -direction.z);
                            if (!el.isAlive()) {
                                hasDestroyed = true;
                            }
                        }
                    } else if (entityHit instanceof ItemEntity) {
                        hasDestroyed = true;
                        entityHit.discard();
                    } else if (entityHit.hurt(dmgSrc, dmg)) {
                        hasDestroyed = !entityHit.isAlive();
                    }
                } else if (pos instanceof BlockHitResult blockResult) {

                    if (!AEConfig.instance().isMatterCanonBlockDamageEnabled()) {
                        penetration = 0;
                    } else {
                        BlockPos blockPos = blockResult.getBlockPos();
                        final BlockState bs = level.getBlockState(blockPos);

                        final float hardness = bs.getDestroySpeed(level, blockPos) * 9.0f;
                        if (hardness >= 0.0 && penetration > hardness && canDestroyBlock(level, blockPos, p)) {
                            hasDestroyed = true;
                            penetration -= hardness;
                            penetration *= 0.60F;
                            level.destroyBlock(blockPos, true);
                        }
                    }
                }
            }
        }
    }

    private boolean canDestroyBlock(Level level, BlockPos pos, Player player) {
        if (!Platform.hasPermissions(new DimensionalBlockPos(level, pos), player)) {
            return false;
        }

        var state = level.getBlockState(pos);
        var event = new BlockEvent.BreakEvent(level, pos, state, player);
        return !NeoForge.EVENT_BUS.post(event).isCanceled();
    }

    public static int getDamageFromPenetration(float penetration) {
        return (int) Math.ceil(penetration / 20.0f);
    }

    @Override
    public IUpgradeInventory getUpgrades(ItemStack is) {
        return UpgradeInventories.forItem(is, 4, this::onUpgradesChanged);
    }

    private void onUpgradesChanged(ItemStack stack, IUpgradeInventory upgrades) {
        // Item is crafted with a normal cell, base energy card contains a dense cell (x8)
        setAEMaxPowerMultiplier(stack, 1 + Upgrades.getEnergyCardMultiplier(upgrades) * 8);
    }

    @Override
    public ConfigInventory getConfigInventory(ItemStack is) {
        return CellConfig.create(Set.of(AEKeyType.items()), is);
    }

    @Override
    public FuzzyMode getFuzzyMode(ItemStack is) {
        return is.getOrDefault(AEComponents.STORAGE_CELL_FUZZY_MODE, FuzzyMode.IGNORE_ALL);
    }

    @Override
    public void setFuzzyMode(ItemStack is, FuzzyMode fzMode) {
        is.set(AEComponents.STORAGE_CELL_FUZZY_MODE, fzMode);
    }

    @Override
    public int getBytes(ItemStack cellItem) {
        return 512;
    }

    @Override
    public int getBytesPerType(ItemStack cellItem) {
        return 8;
    }

    @Override
    public int getTotalTypes(ItemStack cellItem) {
        return 1;
    }

    @Override
    public boolean isBlackListed(ItemStack cellItem, AEKey requestedAddition) {

        if (requestedAddition instanceof AEItemKey itemKey) {
            var pen = getPenetration(itemKey);
            if (pen > 0) {
                return false;
            }

            return !(itemKey.getItem() instanceof PaintBallItem);
        }

        return true;
    }

    private float getPenetration(AEItemKey what) {
        // We need a server to query the recipes if the cache is empty
        var server = AppEng.instance().getCurrentServer();
        if (server == null) {
            LOG.warn("Tried to get penetration of matter cannon ammo for {} while no server was running", what);
            return 0;
        }

        var recipes = server.getRecipeManager().byType(AERecipeTypes.MATTER_CANNON_AMMO);
        for (var holder : recipes) {
            var ammoRecipe = holder.value();
            if (what.matches(ammoRecipe.getAmmo())) {
                return ammoRecipe.getWeight();
            }
        }

        return 0;
    }

    @Override
    public boolean storableInStorageCell() {
        return true;
    }

    @Override
    public double getIdleDrain() {
        return 0.5;
    }

    @Override
    public AEKeyType getKeyType() {
        return AEKeyType.items();
    }
}
