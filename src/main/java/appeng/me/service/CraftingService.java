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

package appeng.me.service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadFactory;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Multimap;
import com.google.common.collect.Sets;

import org.apache.commons.lang3.mutable.MutableObject;
import org.jetbrains.annotations.Nullable;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.Level;

import appeng.api.config.Actionable;
import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.GridHelper;
import appeng.api.networking.IGrid;
import appeng.api.networking.IGridNode;
import appeng.api.networking.IGridServiceProvider;
import appeng.api.networking.crafting.CalculationStrategy;
import appeng.api.networking.crafting.ICraftingCPU;
import appeng.api.networking.crafting.ICraftingLink;
import appeng.api.networking.crafting.ICraftingPlan;
import appeng.api.networking.crafting.ICraftingProvider;
import appeng.api.networking.crafting.ICraftingRequester;
import appeng.api.networking.crafting.ICraftingService;
import appeng.api.networking.crafting.ICraftingSimulationRequester;
import appeng.api.networking.crafting.ICraftingSubmitResult;
import appeng.api.networking.crafting.ICraftingWatcherNode;
import appeng.api.networking.crafting.UnsuitableCpus;
import appeng.api.networking.energy.IEnergyService;
import appeng.api.networking.events.GridCraftingCpuChange;
import appeng.api.networking.security.IActionSource;
import appeng.api.networking.storage.IStorageService;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.api.storage.AEKeyFilter;
import appeng.blockentity.crafting.CraftingBlockEntity;
import appeng.crafting.CraftingCalculation;
import appeng.crafting.CraftingLink;
import appeng.crafting.CraftingLinkNexus;
import appeng.crafting.execution.CraftingSubmitResult;
import appeng.hooks.ticking.TickHandler;
import appeng.me.cluster.implementations.CraftingCPUCluster;
import appeng.me.helpers.InterestManager;
import appeng.me.helpers.StackWatcher;
import appeng.me.service.helpers.CraftingServiceStorage;
import appeng.me.service.helpers.NetworkCraftingProviders;

public class CraftingService implements ICraftingService, IGridServiceProvider {

    /**
     * Sorts Crafting CPUs by Co-Processors in descending order ("fast first"), and storage in ascending order (to
     * minimize the storage waste for jobs.).
     */
    private static final Comparator<CraftingCPUCluster> FAST_FIRST_COMPARATOR = Comparator
            .comparingInt(CraftingCPUCluster::getCoProcessors)
            .reversed()
            .thenComparingLong(CraftingCPUCluster::getAvailableStorage);
    /**
     * Sorts Crafting CPUs by Co-Processors in ascending order ("fast last"), and storage in ascending order (to
     * minimize the storage waste for jobs.).
     */
    private static final Comparator<CraftingCPUCluster> FAST_LAST_COMPARATOR = Comparator
            .comparingInt(CraftingCPUCluster::getCoProcessors)
            .thenComparingLong(CraftingCPUCluster::getAvailableStorage);

    private static final ExecutorService CRAFTING_POOL;

    static {
        final ThreadFactory factory = ar -> {
            final Thread crafting = new Thread(ar, "AE Crafting Calculator");
            crafting.setDaemon(true);
            return crafting;
        };

        CRAFTING_POOL = Executors.newCachedThreadPool(factory);

        GridHelper.addGridServiceEventHandler(GridCraftingCpuChange.class, ICraftingService.class,
                (service, event) -> {
                    ((CraftingService) service).updateList = true;
                });
    }

    private final Set<CraftingCPUCluster> craftingCPUClusters = new HashSet<>();
    private final Map<IGridNode, StackWatcher<ICraftingWatcherNode>> craftingWatchers = new HashMap<>();
    private final IGrid grid;
    private final NetworkCraftingProviders craftingProviders = new NetworkCraftingProviders();
    private final Map<UUID, CraftingLinkNexus> craftingLinks = new HashMap<>();
    private final Multimap<AEKey, StackWatcher<ICraftingWatcherNode>> interests = HashMultimap.create();
    private final InterestManager<StackWatcher<ICraftingWatcherNode>> interestManager = new InterestManager<>(
            this.interests);
    private final IEnergyService energyGrid;
    private final Set<AEKey> currentlyCrafting = new HashSet<>();
    private final Set<AEKey> currentlyCraftable = new HashSet<>();
    private long lastProcessedCraftingLogicChangeTick;
    private long lastProcessedCraftableChangeTick;
    private boolean updateList = false;

    public CraftingService(IGrid grid, IStorageService storageGrid, IEnergyService energyGrid) {
        this.grid = grid;
        this.energyGrid = energyGrid;
        this.lastProcessedCraftingLogicChangeTick = TickHandler.instance().getCurrentTick();
        this.lastProcessedCraftableChangeTick = TickHandler.instance().getCurrentTick();

        storageGrid.addGlobalStorageProvider(new CraftingServiceStorage(this));
    }

    @Override
    public void onServerEndTick() {
        if (this.updateList) {
            this.updateList = false;
            this.updateCPUClusters();
            lastProcessedCraftingLogicChangeTick = -1; // Ensure caches below are also updated
        }

        this.craftingLinks.values().removeIf(nexus -> nexus.isDead(this.grid, this));

        long latestChange = 0;
        for (var cpu : this.craftingCPUClusters) {
            cpu.craftingLogic.tickCraftingLogic(energyGrid, this);
            latestChange = Math.max(
                    latestChange,
                    cpu.craftingLogic.getLastModifiedOnTick());
        }

        // There's nothing to do if we weren't crafting anything and we don't have any CPUs that could craft
        if (latestChange != lastProcessedCraftingLogicChangeTick) {
            lastProcessedCraftingLogicChangeTick = latestChange;

            Set<AEKey> previouslyCrafting = currentlyCrafting.isEmpty() ? Set.of() : new HashSet<>(currentlyCrafting);
            this.currentlyCrafting.clear();

            for (var cpu : this.craftingCPUClusters) {
                cpu.craftingLogic.getAllWaitingFor(this.currentlyCrafting);
            }

            // Notify watchers about items no longer being crafted, but only if there can be changes and there are
            // watchers
            if (!interests.isEmpty() && !(previouslyCrafting.isEmpty() && currentlyCrafting.isEmpty())) {
                var changed = new HashSet<AEKey>();
                changed.addAll(Sets.difference(previouslyCrafting, currentlyCrafting));
                changed.addAll(Sets.difference(currentlyCrafting, previouslyCrafting));
                for (var what : changed) {
                    for (var watcher : interestManager.get(what)) {
                        watcher.getHost().onRequestChange(what);
                    }
                    for (var watcher : interestManager.getAllStacksWatchers()) {
                        watcher.getHost().onRequestChange(what);
                    }
                }
            }
        }

        // Throttle updates of craftables to once every 10 ticks
        if (lastProcessedCraftableChangeTick != craftingProviders.getLastModifiedOnTick()) {
            lastProcessedCraftableChangeTick = craftingProviders.getLastModifiedOnTick();

            // If everything is empty, there's nothing to do
            if (!currentlyCraftable.isEmpty() || !craftingProviders.getCraftableKeys().isEmpty()
                    || !craftingProviders.getEmittableKeys().isEmpty()) {
                Set<AEKey> previouslyCraftable = currentlyCraftable.isEmpty() ? Set.of()
                        : new HashSet<>(currentlyCraftable);
                this.currentlyCraftable.clear();
                currentlyCraftable.addAll(craftingProviders.getCraftableKeys());
                currentlyCraftable.addAll(craftingProviders.getEmittableKeys());

                // Only perform the change tracking if there are watchers
                if (!interests.isEmpty()) {
                    var changedCraftable = new HashSet<AEKey>();
                    changedCraftable.addAll(Sets.difference(previouslyCraftable, currentlyCraftable));
                    changedCraftable.addAll(Sets.difference(currentlyCraftable, previouslyCraftable));
                    for (var what : changedCraftable) {
                        for (var watcher : interestManager.get(what)) {
                            watcher.getHost().onCraftableChange(what);
                        }
                        for (var watcher : interestManager.getAllStacksWatchers()) {
                            watcher.getHost().onCraftableChange(what);
                        }
                    }
                }
            }
        }
    }

    @Override
    public void removeNode(IGridNode gridNode) {

        var craftingWatcher = this.craftingWatchers.remove(gridNode);
        if (craftingWatcher != null) {
            craftingWatcher.destroy();
        }

        var requester = gridNode.getService(ICraftingRequester.class);
        if (requester != null) {
            for (CraftingLinkNexus link : this.craftingLinks.values()) {
                if (link.isRequester(requester)) {
                    link.removeNode();
                }
            }
        }

        this.craftingProviders.removeProvider(gridNode);

        if (gridNode.getOwner() instanceof CraftingBlockEntity) {
            this.updateList = true;
        }
    }

    @Override
    public void addNode(IGridNode gridNode, @Nullable CompoundTag savedData) {

        // The provider can already be added because it gets callback from the storage service,
        // in which it might already register itself before coming to this point.
        this.craftingProviders.removeProvider(gridNode);
        this.craftingProviders.addProvider(gridNode);

        var watchingNode = gridNode.getService(ICraftingWatcherNode.class);
        if (watchingNode != null) {
            var watcher = new StackWatcher<>(interestManager, watchingNode);
            this.craftingWatchers.put(gridNode, watcher);
            watchingNode.updateWatcher(watcher);
        }

        var craftingRequester = gridNode.getService(ICraftingRequester.class);
        if (craftingRequester != null) {
            for (ICraftingLink link : craftingRequester.getRequestedJobs()) {
                if (link instanceof CraftingLink) {
                    this.addLink((CraftingLink) link);
                }
            }
        }

        if (gridNode.getOwner() instanceof CraftingBlockEntity) {
            this.updateList = true;
        }
    }

    @Override
    public Set<AEKey> getCraftables(AEKeyFilter filter) {
        return craftingProviders.getCraftables(filter);
    }

    private void updateCPUClusters() {
        this.craftingCPUClusters.clear();

        for (var blockEntity : this.grid.getMachines(CraftingBlockEntity.class)) {
            final CraftingCPUCluster cluster = blockEntity.getCluster();
            if (cluster != null) {
                this.craftingCPUClusters.add(cluster);

                ICraftingLink maybeLink = cluster.craftingLogic.getLastLink();
                if (maybeLink != null) {
                    this.addLink((CraftingLink) maybeLink);
                }
            }
        }
    }

    public void addLink(CraftingLink link) {
        if (link.isStandalone()) {
            return;
        }

        CraftingLinkNexus nexus = this.craftingLinks.get(link.getCraftingID());
        if (nexus == null) {
            this.craftingLinks.put(link.getCraftingID(), nexus = new CraftingLinkNexus(link.getCraftingID()));
        }

        link.setNexus(nexus);
    }

    public long insertIntoCpus(AEKey what, long amount, Actionable type) {
        long inserted = 0;
        for (var cpu : this.craftingCPUClusters) {
            inserted += cpu.craftingLogic.insert(what, amount - inserted, type);
        }

        return inserted;
    }

    @Override
    public Collection<IPatternDetails> getCraftingFor(AEKey whatToCraft) {
        return this.craftingProviders.getCraftingFor(whatToCraft);
    }

    @Override
    public void refreshNodeCraftingProvider(IGridNode node) {
        this.craftingProviders.removeProvider(node);
        this.craftingProviders.addProvider(node);
    }

    @Override
    public void addGlobalCraftingProvider(ICraftingProvider cc) {
        this.craftingProviders.addProvider(cc);
    }

    @Override
    public void removeGlobalCraftingProvider(ICraftingProvider cc) {
        this.craftingProviders.removeProvider(cc);
    }

    @Override
    public void refreshGlobalCraftingProvider(ICraftingProvider cc) {
        this.craftingProviders.removeProvider(cc);
        this.craftingProviders.addProvider(cc);
    }

    @Nullable
    @Override
    public AEKey getFuzzyCraftable(AEKey whatToCraft, AEKeyFilter filter) {
        return this.craftingProviders.getFuzzyCraftable(whatToCraft, filter);
    }

    @Override
    public Future<ICraftingPlan> beginCraftingCalculation(Level level, ICraftingSimulationRequester simRequester,
            AEKey what, long amount, CalculationStrategy strategy) {
        if (level == null || simRequester == null) {
            throw new IllegalArgumentException("Invalid Crafting Job Request");
        }

        final CraftingCalculation job = new CraftingCalculation(level, grid, simRequester,
                new GenericStack(what, amount), strategy);

        return CRAFTING_POOL.submit(job::run);
    }

    @Override
    public ICraftingSubmitResult submitJob(ICraftingPlan job, ICraftingRequester requestingMachine, ICraftingCPU target,
            boolean prioritizePower, IActionSource src) {
        if (job.simulation()) {
            return CraftingSubmitResult.INCOMPLETE_PLAN;
        }

        CraftingCPUCluster cpuCluster;

        if (target instanceof CraftingCPUCluster) {
            cpuCluster = (CraftingCPUCluster) target;
        } else {
            var unsuitableCpusResult = new MutableObject<UnsuitableCpus>();
            cpuCluster = findSuitableCraftingCPU(job, prioritizePower, src, unsuitableCpusResult);
            if (cpuCluster == null) {
                var unsuitableCpus = unsuitableCpusResult.getValue();
                // If no CPUs were unsuitable, but we couldn't find one, that means there aren't any
                if (unsuitableCpus == null) {
                    return CraftingSubmitResult.NO_CPU_FOUND;
                } else {
                    return CraftingSubmitResult.noSuitableCpu(unsuitableCpus);
                }
            }
        }

        return cpuCluster.submitJob(this.grid, job, src, requestingMachine);
    }

    @Nullable
    private CraftingCPUCluster findSuitableCraftingCPU(ICraftingPlan job, boolean prioritizePower, IActionSource src,
            MutableObject<UnsuitableCpus> unsuitableCpus) {
        var validCpusClusters = new ArrayList<CraftingCPUCluster>(this.craftingCPUClusters.size());
        int offline = 0;
        int busy = 0;
        int tooSmall = 0;
        int excluded = 0;
        for (var cpu : this.craftingCPUClusters) {
            if (!cpu.isActive()) {
                offline++;
                continue;
            }
            if (cpu.isBusy()) {
                busy++;
                continue;
            }
            if (cpu.getAvailableStorage() < job.bytes()) {
                tooSmall++;
                continue;
            }
            if (!cpu.canBeAutoSelectedFor(src)) {
                excluded++;
                continue;
            }
            validCpusClusters.add(cpu);
        }

        if (validCpusClusters.isEmpty()) {
            if (offline > 0 || busy > 0 || tooSmall > 0 || excluded > 0) {
                unsuitableCpus.setValue(new UnsuitableCpus(offline, busy, tooSmall, excluded));
            }
            return null;
        }

        validCpusClusters.sort((a, b) -> {
            // Prioritize sorting by selected mode
            var firstPreferred = a.isPreferredFor(src);
            var secondPreferred = b.isPreferredFor(src);
            if (firstPreferred != secondPreferred) {
                // Sort such that preferred comes first, not preferred second
                return Boolean.compare(secondPreferred, firstPreferred);
            }

            if (prioritizePower) {
                return FAST_FIRST_COMPARATOR.compare(a, b);
            } else {
                return FAST_LAST_COMPARATOR.compare(a, b);
            }
        });

        return validCpusClusters.get(0);
    }

    @Override
    public ImmutableSet<ICraftingCPU> getCpus() {
        var cpus = ImmutableSet.<ICraftingCPU>builder();
        for (CraftingCPUCluster cpu : this.craftingCPUClusters) {
            if (cpu.isActive() && !cpu.isDestroyed()) {
                cpus.add(cpu);
            }
        }
        return cpus.build();
    }

    @Override
    public boolean canEmitFor(AEKey someItem) {
        return this.craftingProviders.canEmitFor(someItem);
    }

    @Override
    public boolean isRequesting(AEKey what) {
        return currentlyCrafting.contains(what);
    }

    @Override
    public long getRequestedAmount(AEKey what) {
        long requested = 0;

        for (CraftingCPUCluster cluster : this.craftingCPUClusters) {
            requested += cluster.craftingLogic.getWaitingFor(what);
        }

        return requested;
    }

    @Override
    public boolean isRequestingAny() {
        return !currentlyCrafting.isEmpty();
    }

    public Iterable<ICraftingProvider> getProviders(IPatternDetails key) {
        return craftingProviders.getMediums(key);
    }

    public boolean hasCpu(ICraftingCPU cpu) {
        return this.craftingCPUClusters.contains(cpu);
    }
}
