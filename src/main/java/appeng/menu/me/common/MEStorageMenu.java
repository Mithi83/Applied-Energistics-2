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

package appeng.menu.me.common;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import com.google.common.primitives.Ints;

import org.jetbrains.annotations.Nullable;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.neoforged.neoforge.network.PacketDistributor;

import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import it.unimi.dsi.fastutil.shorts.ShortSet;

import appeng.api.config.Actionable;
import appeng.api.config.Setting;
import appeng.api.config.Settings;
import appeng.api.config.SortDir;
import appeng.api.config.SortOrder;
import appeng.api.config.ViewItems;
import appeng.api.implementations.blockentities.IViewCellStorage;
import appeng.api.implementations.menuobjects.IPortableTerminal;
import appeng.api.networking.IGrid;
import appeng.api.networking.IGridNode;
import appeng.api.networking.energy.IEnergySource;
import appeng.api.networking.security.IActionHost;
import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;
import appeng.api.storage.ILinkStatus;
import appeng.api.storage.ITerminalHost;
import appeng.api.storage.MEStorage;
import appeng.api.storage.StorageHelper;
import appeng.api.storage.cells.IBasicCellItem;
import appeng.api.util.IConfigManager;
import appeng.api.util.IConfigurableObject;
import appeng.api.util.KeyTypeSelection;
import appeng.api.util.KeyTypeSelectionHost;
import appeng.client.gui.me.common.MEStorageScreen;
import appeng.core.AELog;
import appeng.core.network.ServerboundPacket;
import appeng.core.network.bidirectional.ConfigValuePacket;
import appeng.core.network.clientbound.MEInventoryUpdatePacket;
import appeng.core.network.clientbound.SetLinkStatusPacket;
import appeng.core.network.serverbound.MEInteractionPacket;
import appeng.helpers.InventoryAction;
import appeng.me.helpers.ActionHostEnergySource;
import appeng.menu.AEBaseMenu;
import appeng.menu.SlotSemantics;
import appeng.menu.ToolboxMenu;
import appeng.menu.guisync.GuiSync;
import appeng.menu.guisync.LinkStatusAwareMenu;
import appeng.menu.implementations.MenuTypeBuilder;
import appeng.menu.interfaces.KeyTypeSelectionMenu;
import appeng.menu.me.crafting.CraftAmountMenu;
import appeng.menu.slot.AppEngSlot;
import appeng.menu.slot.RestrictedInputSlot;
import appeng.util.Platform;

/**
 * @see MEStorageScreen
 */
public class MEStorageMenu extends AEBaseMenu
        implements IConfigurableObject, IMEInteractionHandler, LinkStatusAwareMenu,
        KeyTypeSelectionMenu {

    public static final MenuType<MEStorageMenu> TYPE = MenuTypeBuilder
            .<MEStorageMenu, ITerminalHost>create(MEStorageMenu::new, ITerminalHost.class)
            .build("item_terminal");

    public static final MenuType<MEStorageMenu> PORTABLE_ITEM_CELL_TYPE = MenuTypeBuilder
            .<MEStorageMenu, IPortableTerminal>create(MEStorageMenu::new, IPortableTerminal.class)
            .build("portable_item_cell");
    public static final MenuType<MEStorageMenu> PORTABLE_FLUID_CELL_TYPE = MenuTypeBuilder
            .<MEStorageMenu, IPortableTerminal>create(MEStorageMenu::new, IPortableTerminal.class)
            .build("portable_fluid_cell");

    public static final MenuType<MEStorageMenu> WIRELESS_TYPE = MenuTypeBuilder
            .<MEStorageMenu, IPortableTerminal>create(MEStorageMenu::new, IPortableTerminal.class)
            .build("wirelessterm");

    private final List<RestrictedInputSlot> viewCellSlots;
    private final IConfigManager clientCM;
    private final ToolboxMenu toolboxMenu;
    private final ITerminalHost host;

    /**
     * The number of active crafting jobs in the network. -1 means unknown and will hide the label on the screen.
     */
    @GuiSync(100)
    public int activeCraftingJobs = -1;
    private static final short SEARCH_KEY_TYPES_ID = 101;
    @GuiSync(SEARCH_KEY_TYPES_ID)
    public SyncedKeyTypes searchKeyTypes = new SyncedKeyTypes();

    // Client-side: last status received from server
    // Server-side: last status sent to client
    private ILinkStatus linkStatus = ILinkStatus.ofDisconnected(null);

    @Nullable
    private Runnable gui;
    private IConfigManager serverCM;

    protected final MEStorage storage;

    protected final IEnergySource energySource;

    private final IncrementalUpdateHelper updateHelper = new IncrementalUpdateHelper();

    /**
     * The repository of entries currently known on the client-side. This is maintained by the screen associated with
     * this menu and will only be non-null on the client-side.
     */
    @Nullable
    private IClientRepo clientRepo;

    /**
     * The last set of craftables sent to the client.
     */
    private Set<AEKey> previousCraftables = Collections.emptySet();
    private KeyCounter previousAvailableStacks = new KeyCounter();

    public MEStorageMenu(MenuType<?> menuType, int id, Inventory ip, ITerminalHost host) {
        this(menuType, id, ip, host, true);
    }

    protected MEStorageMenu(MenuType<?> menuType, int id, Inventory ip, ITerminalHost host, boolean bindInventory) {
        super(menuType, id, ip, host);

        this.host = host;
        if (host instanceof IEnergySource hostEnergySource) {
            this.energySource = hostEnergySource;
        } else if (host instanceof IActionHost actionHost) {
            this.energySource = new ActionHostEnergySource(actionHost);
        } else {
            this.energySource = IEnergySource.empty();
        }
        this.storage = Objects.requireNonNull(host.getInventory(), "host inventory is null");

        this.clientCM = IConfigManager.builder(this::onSettingChanged)
                .registerSetting(Settings.SORT_BY, SortOrder.NAME)
                .registerSetting(Settings.VIEW_MODE, ViewItems.ALL)
                .registerSetting(Settings.SORT_DIRECTION, SortDir.ASCENDING)
                .build();

        if (isServerSide()) {
            this.serverCM = host.getConfigManager();
        }

        // Create slots for the view cells, in case the terminal host supports those
        if (!hideViewCells() && host instanceof IViewCellStorage) {
            var viewCellStorage = ((IViewCellStorage) host).getViewCellStorage();
            this.viewCellSlots = new ArrayList<>(viewCellStorage.size());
            for (int i = 0; i < viewCellStorage.size(); i++) {
                var slot = new RestrictedInputSlot(RestrictedInputSlot.PlacableItemType.VIEW_CELL,
                        viewCellStorage, i);
                this.addSlot(slot, SlotSemantics.VIEW_CELL);
                this.viewCellSlots.add(slot);
            }
        } else {
            this.viewCellSlots = Collections.emptyList();
        }

        this.toolboxMenu = new ToolboxMenu(this);

        setupUpgrades(host.getUpgrades());

        if (bindInventory) {
            this.createPlayerInventorySlots(ip);
        }
    }

    public ToolboxMenu getToolbox() {
        return toolboxMenu;
    }

    protected boolean hideViewCells() {
        return false;
    }

    @Nullable
    public IGridNode getGridNode() {
        if (host instanceof IActionHost actionHost) {
            return actionHost.getActionableNode();
        }
        return null;
    }

    public boolean isKeyVisible(AEKey key) {
        // If the host is a basic item cell with a limited key space, account for this
        if (itemMenuHost != null && itemMenuHost.getItem() instanceof IBasicCellItem basicCellItem) {
            return basicCellItem.getKeyType().contains(key);
        }

        return true;
    }

    @Override
    public void broadcastChanges() {
        toolboxMenu.tick();

        if (isServerSide()) {
            this.updateLinkStatus();

            this.updateActiveCraftingJobs();

            for (var set : this.serverCM.getSettings()) {
                var sideLocal = this.serverCM.getSetting(set);
                var sideRemote = this.clientCM.getSetting(set);

                if (sideLocal != sideRemote) {
                    set.copy(serverCM, clientCM);
                    sendPacketToClient(new ConfigValuePacket(set, serverCM));
                }
            }

            if (host instanceof KeyTypeSelectionHost keyTypeSelectionHost) {
                this.searchKeyTypes = new SyncedKeyTypes(keyTypeSelectionHost.getKeyTypeSelection().enabled());
            }

            var craftables = getCraftablesFromGrid();
            var availableStacks = storage.getAvailableStacks();

            // This is currently not supported/backed by any network service
            var requestables = new KeyCounter();

            try {
                // Craftables
                // Newly craftable
                Sets.difference(previousCraftables, craftables).forEach(updateHelper::addChange);
                // No longer craftable
                Sets.difference(craftables, previousCraftables).forEach(updateHelper::addChange);

                // Available changes
                previousAvailableStacks.removeAll(availableStacks);
                previousAvailableStacks.removeZeros();
                previousAvailableStacks.keySet().forEach(updateHelper::addChange);

                if (updateHelper.hasChanges()) {
                    var builder = MEInventoryUpdatePacket
                            .builder(containerId, updateHelper.isFullUpdate(), getPlayer().registryAccess());
                    builder.setFilter(this::isKeyVisible);
                    builder.addChanges(updateHelper, availableStacks, craftables, requestables);
                    builder.buildAndSend(this::sendPacketToClient);
                    updateHelper.commitChanges();
                }

            } catch (Exception e) {
                AELog.warn(e, "Failed to send incremental inventory update to client");
            }

            previousCraftables = ImmutableSet.copyOf(craftables);
            previousAvailableStacks = availableStacks;

            super.broadcastChanges();
        }

    }

    @Override
    public void onServerDataSync(ShortSet updatedFields) {
        super.onServerDataSync(updatedFields);

        if (updatedFields.contains(SEARCH_KEY_TYPES_ID)) {
            // Trigger re-sort
            if (getGui() != null) {
                getGui().run();
            }
        }
    }

    protected boolean showsCraftables() {
        return true;
    }

    private Set<AEKey> getCraftablesFromGrid() {
        IGridNode hostNode = getGridNode();
        // Wireless terminals do not directly expose the target grid (even though they have one)
        if (hostNode == null && host instanceof IActionHost actionHost) {
            hostNode = actionHost.getActionableNode();
        }
        if (!showsCraftables()) {
            return Collections.emptySet();
        }

        if (hostNode != null && hostNode.isActive()) {
            return hostNode.getGrid().getCraftingService().getCraftables(this::isKeyVisible);
        }
        return Collections.emptySet();
    }

    private void updateActiveCraftingJobs() {
        IGridNode hostNode = getGridNode();
        IGrid grid = null;
        if (hostNode != null) {
            grid = hostNode.getGrid();
        }

        if (grid == null) {
            // No grid to query crafting jobs from
            this.activeCraftingJobs = -1;
            return;
        }

        int activeJobs = 0;
        for (var cpus : grid.getCraftingService().getCpus()) {
            if (cpus.isBusy()) {
                activeJobs++;
            }
        }
        this.activeCraftingJobs = activeJobs;
    }

    private void onSettingChanged(IConfigManager manager, Setting<?> setting) {
        if (this.getGui() != null) {
            this.getGui().run();
        }
    }

    @Override
    public IConfigManager getConfigManager() {
        if (isServerSide()) {
            return this.serverCM;
        }
        return this.clientCM;
    }

    public List<ItemStack> getViewCells() {
        return this.viewCellSlots.stream()
                .map(AppEngSlot::getItem)
                .collect(Collectors.toList());
    }

    /**
     * Checks that the inventory monitor is connected, a power source exists and that it is powered.
     */
    protected final boolean canInteractWithGrid() {
        return getLinkStatus().connected();
    }

    @Override
    public final void handleInteraction(long serial, InventoryAction action) {
        if (isClientSide()) {
            ServerboundPacket message = new MEInteractionPacket(containerId, serial, action);
            PacketDistributor.sendToServer(message);
            return;
        }

        // Do not allow interactions if there's no monitor or no power
        if (!canInteractWithGrid()) {
            return;
        }

        ServerPlayer player = (ServerPlayer) this.getPlayerInventory().player;

        // Serial -1 is used to target empty virtual slots, which only allows the player to put
        // items under their cursor into the network inventory
        if (serial == -1) {
            handleNetworkInteraction(player, null, action);
            return;
        }

        AEKey stack = getStackBySerial(serial);
        if (stack == null) {
            // This can happen if the client sent the request after we removed the item, but before
            // the client knows about it (-> network delay).
            return;
        }

        handleNetworkInteraction(player, stack, action);
    }

    protected void handleNetworkInteraction(ServerPlayer player, @Nullable AEKey clickedKey, InventoryAction action) {

        if (!canInteractWithGrid()) {
            return;
        }

        // Handle auto-crafting requests
        if (action == InventoryAction.AUTO_CRAFT) {
            var locator = getLocator();
            if (locator != null && clickedKey != null) {
                CraftAmountMenu.open(player, locator, clickedKey, clickedKey.getAmountPerUnit());
            }
            return;
        }

        // Attempt fluid related actions first
        switch (action) {
            case FILL_ITEM -> tryFillContainerItem(clickedKey, false, false);
            case FILL_ITEM_MOVE_TO_PLAYER -> tryFillContainerItem(clickedKey, true, false);
            case FILL_ENTIRE_ITEM -> tryFillContainerItem(clickedKey, false, true);
            case FILL_ENTIRE_ITEM_MOVE_TO_PLAYER -> tryFillContainerItem(clickedKey, true, true);
            case EMPTY_ITEM ->
                handleEmptyHeldItem(
                        (what, amount, mode) -> StorageHelper.poweredInsert(energySource, storage, what, amount,
                                getActionSource(), mode),
                        false);
            case EMPTY_ENTIRE_ITEM ->
                handleEmptyHeldItem(
                        (what, amount, mode) -> StorageHelper.poweredInsert(energySource, storage, what, amount,
                                getActionSource(), mode),
                        true);
        }

        // Handle interactions where the player wants to put something into the network
        if (clickedKey == null) {
            if (action == InventoryAction.SPLIT_OR_PLACE_SINGLE || action == InventoryAction.ROLL_DOWN) {
                putCarriedItemIntoNetwork(true);
            } else if (action == InventoryAction.PICKUP_OR_SET_DOWN) {
                putCarriedItemIntoNetwork(false);
            }
            return;
        }

        // Any of the remaining actions are for items only
        if (!(clickedKey instanceof AEItemKey clickedItem)) {
            return;
        }

        switch (action) {
            case SHIFT_CLICK -> moveOneStackToPlayer(clickedItem);
            case ROLL_DOWN -> {
                // Insert 1 of the carried stack into the network (or at least try to), regardless of what we're
                // hovering in the network inventory.
                var carried = getCarried();
                if (!carried.isEmpty()) {
                    var what = AEItemKey.of(carried);
                    var inserted = StorageHelper.poweredInsert(energySource, storage, what, 1, this.getActionSource());
                    if (inserted > 0) {
                        getCarried().shrink(1);
                    }
                }
            }
            case ROLL_UP, PICKUP_SINGLE -> {
                // Extract 1 of the hovered stack from the network (or at least try to), and add it to the carried item
                var item = getCarried();

                if (!item.isEmpty()) {
                    if (item.getCount() >= item.getMaxStackSize()) {
                        return; // Max stack size reached
                    }
                    if (!clickedItem.matches(item)) {
                        return; // Not stackable
                    }
                }

                var extracted = StorageHelper.poweredExtraction(energySource, storage, clickedItem, 1,
                        this.getActionSource());
                if (extracted > 0) {
                    if (item.isEmpty()) {
                        setCarried(clickedItem.toStack());
                    } else {
                        // we checked beforehand that max stack size was not reached
                        item.grow(1);
                    }
                }
            }
            case PICKUP_OR_SET_DOWN -> {
                if (!getCarried().isEmpty()) {
                    putCarriedItemIntoNetwork(false);
                } else {
                    var extracted = StorageHelper.poweredExtraction(
                            energySource,
                            storage,
                            clickedItem,
                            clickedItem.getMaxStackSize(),
                            this.getActionSource());
                    if (extracted > 0) {
                        setCarried(clickedItem.toStack((int) extracted));
                    } else {
                        setCarried(ItemStack.EMPTY);
                    }
                }
            }
            case SPLIT_OR_PLACE_SINGLE -> {
                if (!getCarried().isEmpty()) {
                    putCarriedItemIntoNetwork(true);
                } else {
                    var extracted = storage.extract(
                            clickedItem,
                            clickedItem.getMaxStackSize(),
                            Actionable.SIMULATE,
                            this.getActionSource());

                    if (extracted > 0) {
                        // Half
                        extracted = extracted + 1 >> 1;
                        extracted = StorageHelper.poweredExtraction(energySource, storage, clickedItem, extracted,
                                this.getActionSource());
                    }

                    if (extracted > 0) {
                        setCarried(clickedItem.toStack((int) extracted));
                    } else {
                        setCarried(ItemStack.EMPTY);
                    }
                }
            }
            case CREATIVE_DUPLICATE -> {
                if (player.getAbilities().instabuild) {
                    var is = clickedItem.toStack();
                    is.setCount(is.getMaxStackSize());
                    setCarried(is);
                }
            }
            case MOVE_REGION -> {
                final int playerInv = player.getInventory().items.size();
                for (int slotNum = 0; slotNum < playerInv; slotNum++) {
                    if (!moveOneStackToPlayer(clickedItem)) {
                        break;
                    }
                }
            }
            default -> AELog.warn("Received unhandled inventory action %s from client in %s", action, getClass());
        }
    }

    private void tryFillContainerItem(@Nullable AEKey clickedKey, boolean moveToPlayer, boolean fillAll) {
        // Special handling for fluids to facilitate filling water/lava buckets which are often
        // needed for crafting and placement in-world.
        boolean grabbedEmptyBucket = false;
        if (getCarried().isEmpty() && clickedKey instanceof AEFluidKey fluidKey
                && fluidKey.getFluid().getBucket() != Items.AIR) {
            // This costs no energy, but who cares...
            if (storage.extract(AEItemKey.of(Items.BUCKET), 1, Actionable.MODULATE, getActionSource()) >= 1) {
                var bucket = Items.BUCKET.getDefaultInstance();
                setCarried(bucket);
                grabbedEmptyBucket = true;
            }
        }

        var carriedBefore = getCarried().getItem();

        handleFillingHeldItem(
                (amount, mode) -> StorageHelper.poweredExtraction(energySource, storage, clickedKey, amount,
                        getActionSource(), mode),
                clickedKey, fillAll);

        // If we grabbed an empty bucket, and after trying to fill it, it's still empty, put it back!
        if (grabbedEmptyBucket && getCarried().is(Items.BUCKET)) {
            var inserted = storage.insert(AEItemKey.of(getCarried()), getCarried().getCount(), Actionable.MODULATE,
                    getActionSource());
            var newCarried = getCarried().copy();
            newCarried.shrink(Ints.saturatedCast(inserted));
            setCarried(newCarried);
        }
        // If the player was holding shift, whatever has been filled should be moved to the player inv
        // To detect the fill operation actually filling, and not moving excess into the inv itself
        // We just compare against the carried item from before the fill operation.
        if (moveToPlayer && !getCarried().is(carriedBefore)) {
            if (getPlayer().addItem(getCarried())) {
                setCarried(ItemStack.EMPTY);
            }
        }
    }

    protected void putCarriedItemIntoNetwork(boolean singleItem) {
        var heldStack = getCarried();

        var what = AEItemKey.of(heldStack);
        if (what == null) {
            return;
        }

        var amount = heldStack.getCount();
        if (singleItem) {
            amount = 1;
        }

        var inserted = StorageHelper.poweredInsert(energySource, storage, what, amount,
                this.getActionSource());
        setCarried(Platform.getInsertionRemainder(heldStack, inserted));
    }

    private boolean moveOneStackToPlayer(AEItemKey what) {
        var potentialAmount = storage.extract(what, what.getMaxStackSize(), Actionable.SIMULATE, getActionSource());
        if (potentialAmount <= 0) {
            return false; // No item available
        }

        var destinationSlots = getQuickMoveDestinationSlots(what.toStack(), false);

        for (var destinationSlot : destinationSlots) {
            var amount = getPlaceableAmount(destinationSlot, what);
            if (amount <= 0) {
                continue;
            }

            var extracted = StorageHelper.poweredExtraction(energySource, storage, what, amount, getActionSource());
            if (extracted == 0) {
                return false; // No items available
            }

            var currentItem = destinationSlot.getItem();
            if (!currentItem.isEmpty()) {
                destinationSlot.setByPlayer(currentItem.copyWithCount(currentItem.getCount() + (int) extracted));
            } else {
                destinationSlot.setByPlayer(what.toStack((int) extracted));
            }
            return true;
        }

        return false;
    }

    @Nullable
    protected final AEKey getStackBySerial(long serial) {
        return updateHelper.getBySerial(serial);
    }

    public ILinkStatus getLinkStatus() {
        return linkStatus;
    }

    @Nullable
    private Runnable getGui() {
        return this.gui;
    }

    /**
     * Sets the current screen. Will be notified when settings change and it needs to update its sorting.
     */
    public void setGui(@Nullable Runnable gui) {
        this.gui = gui;
    }

    @Nullable
    public IClientRepo getClientRepo() {
        return clientRepo;
    }

    public void setClientRepo(@Nullable IClientRepo clientRepo) {
        this.clientRepo = clientRepo;
    }

    /**
     * Try to transfer an item stack into the grid.
     */
    @Override
    protected int transferStackToMenu(ItemStack input) {
        if (!canInteractWithGrid()) {
            // Allow non-grid slots to be use
            return super.transferStackToMenu(input);
        }

        var key = AEItemKey.of(input);
        if (key == null || !isKeyVisible(key)) {
            return 0;
        }

        return (int) StorageHelper.poweredInsert(energySource, storage,
                key, input.getCount(),
                this.getActionSource());
    }

    /**
     * Checks if the terminal has a given reservedAmounts of the requested item. Used to determine for REI/JEI if a
     * recipe is potentially craftable based on the available items.
     * <p/>
     * This method is <strong>slow</strong>, but it is client-only and thus doesn't scale with the player count.
     */
    public boolean hasIngredient(Ingredient ingredient, Object2IntOpenHashMap<Object> reservedAmounts) {
        var clientRepo = getClientRepo();

        if (clientRepo != null && getLinkStatus().connected()) {
            for (var stack : clientRepo.getByIngredient(ingredient)) {
                var reservedAmount = reservedAmounts.getOrDefault(stack, 0);
                if (stack.getStoredAmount() - reservedAmount >= 1) {
                    reservedAmounts.merge(stack, 1, Integer::sum);
                    return true;
                }
            }
        }

        return false;
    }

    /**
     * @return The stacks available in the storage as determined the last time this menu was ticked.
     */
    protected final KeyCounter getPreviousAvailableStacks() {
        Preconditions.checkState(isServerSide());
        return previousAvailableStacks;
    }

    public boolean canConfigureTypeFilter() {
        return this.host instanceof KeyTypeSelectionHost;
    }

    public ITerminalHost getHost() {
        return host;
    }

    // When using a custom implementation of ILinkStatus, override this and implement your own packet
    protected void updateLinkStatus() {
        var linkStatus = host.getLinkStatus();
        if (!Objects.equals(this.linkStatus, linkStatus)) {
            this.linkStatus = linkStatus;
            sendPacketToClient(new SetLinkStatusPacket(linkStatus));
        }
    }

    @Override
    public void setLinkStatus(ILinkStatus linkStatus) {
        this.linkStatus = linkStatus;
    }

    @Override
    public KeyTypeSelection getServerKeyTypeSelection() {
        return ((KeyTypeSelectionHost) host).getKeyTypeSelection();
    }

    @Override
    public SyncedKeyTypes getClientKeyTypeSelection() {
        return searchKeyTypes;
    }
}
