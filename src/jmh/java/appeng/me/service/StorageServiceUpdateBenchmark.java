package appeng.me.service;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.infra.Blackhole;

import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.neoforged.testframework.junit.EphemeralTestServerProvider;

import appeng.api.networking.IGridNode;
import appeng.api.networking.IStackWatcher;
import appeng.api.networking.storage.IStorageWatcherNode;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;
import appeng.api.storage.MEStorage;
import appeng.me.storage.NetworkStorage;

/**
 * Isolates the bookkeeping in {@link StorageService}'s cache refresh (change detection and watcher notifications) from
 * the cost of collecting stacks from real cells. The network contains a single storage that copies a prebuilt
 * {@link KeyCounter}, so {@link #refreshCache} is dominated by the service's own work. {@link #copyOnly} performs just
 * the copy that {@link #refreshCache} also does, as a baseline to subtract.
 * <p>
 * With {@code churn > 0}, the storage alternates between two snapshots that differ by {@code churn} percent of the
 * stacks removed, {@code churn} percent added and {@code churn} percent changed in amount, so every refresh sees
 * changes.
 */
@State(Scope.Thread)
public class StorageServiceUpdateBenchmark {

    @Param({ "1000", "10000", "63000" })
    public int stacks;

    @Param({ "0", "5" })
    public int churn;

    @Param({ "none", "all" })
    public String watcher;

    private StorageService service;
    private SnapshotStorage serviceStorage;
    private SnapshotStorage baselineStorage;
    private final KeyCounter baselineOut = new KeyCounter();
    private long notifications;

    @Setup(Level.Trial)
    public void setup() {
        // Item components are only bound once a server is running
        EphemeralTestServerProvider.grabServer();

        var random = new Random(42L);
        var items = BuiltInRegistries.ITEM.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(Map.Entry::getValue)
                .filter(item -> item != Items.AIR)
                .toList();

        int changed = stacks * churn / 100;
        var keys = new ArrayList<AEKey>(stacks + changed);
        for (int i = 0; i < stacks + changed; i++) {
            keys.add(createKey(items.get(random.nextInt(items.size())), i));
        }

        // Snapshot A: the first `stacks` keys
        var a = new KeyCounter();
        for (int i = 0; i < stacks; i++) {
            a.add(keys.get(i), 1 + random.nextInt(10000));
        }
        // Snapshot B: remove the first `changed` keys, change the amount of the next `changed`, add `changed` new ones
        var b = new KeyCounter();
        for (int i = changed; i < stacks; i++) {
            long amount = a.get(keys.get(i));
            b.add(keys.get(i), i < 2 * changed ? amount + 1 : amount);
        }
        for (int i = stacks; i < stacks + changed; i++) {
            b.add(keys.get(i), 1 + random.nextInt(10000));
        }

        var snapshots = churn > 0 ? List.of(a, b) : List.of(a);
        serviceStorage = new SnapshotStorage(snapshots);
        baselineStorage = new SnapshotStorage(snapshots);

        service = new StorageService();
        ((NetworkStorage) service.getInventory()).mount(0, serviceStorage);
        if (watcher.equals("all")) {
            service.addNode(createWatcherNode(), null);
        }

        // Fill the cache once, so the first measured refresh compares against a previous state
        service.getCachedInventory();
    }

    private static AEKey createKey(Item item, int index) {
        var stack = new ItemStack(item);
        var tag = new CompoundTag();
        tag.putInt("i", index);
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        return AEItemKey.of(stack);
    }

    private IGridNode createWatcherNode() {
        IStorageWatcherNode watcherNode = new IStorageWatcherNode() {
            @Override
            public void updateWatcher(IStackWatcher newWatcher) {
                newWatcher.setWatchAll(true);
            }

            @Override
            public void onStackChange(AEKey what, long amount) {
                notifications++;
            }
        };
        return (IGridNode) Proxy.newProxyInstance(IGridNode.class.getClassLoader(), new Class<?>[] { IGridNode.class },
                (proxy, method, args) -> {
                    if (method.getName().equals("getService") && args[0] == IStorageWatcherNode.class) {
                        return watcherNode;
                    }
                    return null;
                });
    }

    @Benchmark
    public void refreshCache(Blackhole bh) {
        service.invalidateCache();
        bh.consume(service.getCachedInventory());
        bh.consume(notifications);
    }

    @Benchmark
    public void copyOnly(Blackhole bh) {
        // Mirrors the first part of StorageService#updateCachedStacks
        baselineOut.clear();
        baselineStorage.getAvailableStacks(baselineOut);
        baselineOut.removeEmptySubmaps();
        bh.consume(baselineOut);
    }

    /**
     * Storage that reports prebuilt snapshots, cycling to the next one on every call.
     */
    private static class SnapshotStorage implements MEStorage {
        private final List<KeyCounter> snapshots;
        private int next;

        SnapshotStorage(List<KeyCounter> snapshots) {
            this.snapshots = snapshots;
        }

        @Override
        public void getAvailableStacks(KeyCounter out) {
            var snapshot = snapshots.get(next);
            next = (next + 1) % snapshots.size();
            for (var entry : snapshot) {
                out.add(entry.getKey(), entry.getLongValue());
            }
        }

        @Override
        public int getEstimatedStackCount() {
            return snapshots.getFirst().size();
        }

        @Override
        public Component getDescription() {
            return Component.literal("snapshot");
        }
    }
}
