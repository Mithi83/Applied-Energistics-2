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

package appeng.parts.p2p;

import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RedStoneWireBlock;
import net.minecraft.world.level.block.state.BlockState;

import appeng.api.networking.IGridNodeListener;
import appeng.api.parts.IPartItem;
import appeng.api.parts.IPartModel;
import appeng.core.AppEng;
import appeng.items.parts.PartModels;
import appeng.util.Platform;

public class RedstoneP2PTunnelPart extends P2PTunnelPart<RedstoneP2PTunnelPart> {

    private static final P2PModels MODELS = new P2PModels(AppEng.makeId("part/p2p/p2p_tunnel_redstone"));

    @PartModels
    public static List<IPartModel> getModels() {
        return MODELS.getModels();
    }

    private int power;
    private boolean recursive = false;

    public RedstoneP2PTunnelPart(IPartItem<?> partItem) {
        super(partItem);
    }

    @Override
    protected float getPowerDrainPerTick() {
        return 0.5f;
    }

    private void setNetworkReady() {
        if (this.isOutput()) {
            final RedstoneP2PTunnelPart in = this.getInput();
            if (in != null) {
                this.putInput(in.power);
            } else {
                this.putInput(0);
            }
        }
    }

    private void putInput(Object o) {
        if (this.recursive) {
            return;
        }

        this.recursive = true;
        if (this.isOutput()) {
            final int newPower = this.getMainNode().isActive() ? (Integer) o : 0;
            if (this.power != newPower) {
                this.power = newPower;
                this.notifyNeighbors();
            }
        }
        this.recursive = false;
    }

    private void notifyNeighbors() {
        final Level level = this.getBlockEntity().getLevel();

        Platform.notifyBlocksOfNeighbors(level, this.getBlockEntity().getBlockPos());

        // and this cause sometimes it can go thought walls.
        for (Direction face : Direction.values()) {
            Platform.notifyBlocksOfNeighbors(level, this.getBlockEntity().getBlockPos().relative(face));
        }
    }

    @Override
    protected void onMainNodeStateChanged(IGridNodeListener.State reason) {
        super.onMainNodeStateChanged(reason);
        if (getMainNode().hasGridBooted()) {
            this.setNetworkReady();
        }
    }

    @Override
    public void readFromNBT(CompoundTag tag, HolderLookup.Provider registries) {
        super.readFromNBT(tag, registries);
        this.power = tag.getInt("power");
    }

    @Override
    public void writeToNBT(CompoundTag tag, HolderLookup.Provider registries) {
        super.writeToNBT(tag, registries);
        tag.putInt("power", this.power);
    }

    @Override
    public void onTunnelNetworkChange() {
        this.setNetworkReady();
    }

    @Override
    public void onNeighborChanged(BlockGetter level, BlockPos pos, BlockPos neighbor) {
        if (!this.isOutput()) {
            final BlockPos target = this.getBlockEntity().getBlockPos().relative(this.getSide());

            final BlockState state = this.getBlockEntity().getLevel().getBlockState(target);
            final Block b = state.getBlock();
            if (b != null && !this.isOutput()) {
                Direction srcSide = this.getSide();
                if (b instanceof RedStoneWireBlock) {
                    srcSide = Direction.UP;
                }

                this.power = state.getSignal(this.getBlockEntity().getLevel(), target, srcSide);
                this.power = Math.max(this.power,
                        state.getSignal(this.getBlockEntity().getLevel(), target, srcSide));
                this.sendToOutput(this.power);
            } else {
                this.sendToOutput(0);
            }
        }
    }

    @Override
    public boolean canConnectRedstone() {
        return true;
    }

    @Override
    public int isProvidingStrongPower() {
        return this.isOutput() ? this.power : 0;
    }

    @Override
    public int isProvidingWeakPower() {
        return this.isOutput() ? this.power : 0;
    }

    private void sendToOutput(int power) {
        for (RedstoneP2PTunnelPart rs : this.getOutputs()) {
            rs.putInput(power);
        }
    }

    @Override
    public IPartModel getStaticModels() {
        return MODELS.getModel(this.isPowered(), this.isActive());
    }

}
