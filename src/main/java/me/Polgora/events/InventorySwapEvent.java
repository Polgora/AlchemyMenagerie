package me.Polgora.events;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.EntityEventSystem;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.ItemUtils;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.player.windows.ItemContainerWindow;
import com.hypixel.hytale.server.core.inventory.InventoryChangeEvent;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import me.Polgora.JournalManager;
import me.Polgora.KnownItemSwapper;
import me.Polgora.PouchManager;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Listens for inventory changes and swaps any vanilla item the player has
 * already scanned with its _Known counterpart, in-place. Also assigns pouchIds
 * to new Flask Pouches and routes any Alchemy Flasks into the first available pouch.
 */
public class InventorySwapEvent extends EntityEventSystem<EntityStore, InventoryChangeEvent> {

    // Tracks players whose inventory is currently being swapped to prevent
    // setItemStackForSlot / addItemStack from re-triggering recursively.
    private static final Set<String> processing = ConcurrentHashMap.newKeySet();

    public InventorySwapEvent() {
        super(InventoryChangeEvent.class);
    }

    @Override
    public Query<EntityStore> getQuery() {
        return Query.any();
    }

    @Override
    public void handle(int i, ArchetypeChunk<EntityStore> chunk, Store<EntityStore> store,
                       CommandBuffer<EntityStore> cmd, InventoryChangeEvent event) {
        Ref<EntityStore> ref = chunk.getReferenceTo(i);
        Player player = store.getComponent(ref, Player.getComponentType());
        if (player == null) return;
        if (player.isWaitingForClientReady()) return;
        PlayerRef playerRef = store.getComponent(ref, PlayerRef.getComponentType());
        if (playerRef == null || playerRef.getUuid() == null) return;
        String playerId = playerRef.getUuid().toString();

        if (!processing.add(playerId)) return;
        try {
            var container = event.getItemContainer();
            if (container == null) return;

            // Scan the changed container slot-by-slot and only modify slots that actually
            // need changing. Avoid replaceAll() which marks every slot dirty and causes
            // InventoryChangeEvent to re-fire every tick in an infinite cascade.
            short containerCapacity = container.getCapacity();
            for (short s = 0; s < containerCapacity; s++) {
                ItemStack stack = container.getItemStack(s);
                if (stack == null || stack.isEmpty()) continue;
                String id = stack.getItemId();

                // Knowledge swap — replace vanilla item with its _Known variant
                if (!id.endsWith(KnownItemSwapper.KNOWN_SUFFIX) && JournalManager.isKnown(playerId, id)) {
                    String knownId = id + KnownItemSwapper.KNOWN_SUFFIX;
                    container.removeItemStackFromSlot(s, stack.getQuantity());
                    player.getInventory().getCombinedArmorHotbarUtilityStorage()
                            .addItemStack(new ItemStack(knownId, stack.getQuantity()));
                    continue;
                }

                // Assign pouchId to a Flask Pouch that doesn't have one yet
                if (id.equals(PouchManager.POUCH_ITEM_ID) && PouchManager.getPouchId(stack) == null) {
                    container.setItemStackForSlot(s, PouchManager.withNewPouchId(stack));
                }
            }

            var everything = player.getInventory().getCombinedArmorHotbarUtilityStorage();
            short capacity = everything.getCapacity();

            // Enforce one Flask Pouch per inventory. If a second one arrives, discard it.
            boolean hasPouch = false;
            for (short s = 0; s < capacity; s++) {
                ItemStack stack = everything.getItemStack(s);
                if (stack == null || stack.isEmpty()) continue;
                if (!stack.getItemId().equals(PouchManager.POUCH_ITEM_ID)) continue;
                if (!hasPouch) {
                    hasPouch = true;
                    PouchPickupPreventionSystem.pouchPickupInFlight.remove(playerId);
                } else {
                    ItemStack extra = everything.getItemStack(s).withQuantity(1);
                    everything.removeItemStackFromSlot(s, 1);
                    if (!returnToOpenContainer(player, extra)) {
                        var world = player.getWorld();
                        var entityRef = player.getReference();
                        world.execute(() -> {
                            var storeRef = world.getEntityStore().getStore();
                            ItemUtils.dropItem(entityRef, extra, storeRef);
                        });
                    }
                    player.sendMessage(Message.raw("You can only carry one Flask Pouch at a time."));
                }
            }

            // Route any Alchemy Flasks into the first available Flask Pouch.
            boolean messageSent = false;
            for (short s = 0; s < capacity; s++) {
                ItemStack stack = everything.getItemStack(s);
                if (stack == null || stack.isEmpty()) continue;
                if (!stack.getItemId().startsWith(PouchManager.FLASK_ITEM_PREFIX)) continue;

                String itemId = stack.getItemId();
                int qty = stack.getQuantity();
                for (int q = 0; q < qty; q++) {
                    everything.removeItemStackFromSlot(s, 1);

                    short pouchSlot = findNonFullPouchSlot(everything, capacity);
                    if (pouchSlot < 0) {
                        boolean hasAnyPouch = hasPouchSlot(everything, capacity);
                        returnToOpenContainer(player, new ItemStack(itemId, 1));
                        if (!messageSent) {
                            if (hasAnyPouch) {
                                player.sendMessage(Message.raw("Flask Pouch full!"));
                            } else {
                                player.sendMessage(Message.raw("You need a Flask Pouch to store Alchemy Flasks."));
                            }
                            messageSent = true;
                        }
                    } else {
                        ItemStack pouch = everything.getItemStack(pouchSlot);
                        everything.setItemStackForSlot(pouchSlot, PouchManager.withEmptyFlaskAdded(pouch));
                    }
                }
            }
        } finally {
            processing.remove(playerId);
        }
    }

    private static boolean returnToOpenContainer(Player player, ItemStack item) {
        for (var window : player.getWindowManager().getWindows()) {
            if (window instanceof ItemContainerWindow icw) {
                icw.getItemContainer().addItemStack(item);
                return true;
            }
        }
        return false;
    }

    private static short findNonFullPouchSlot(ItemContainer inventory, short capacity) {
        for (short s = 0; s < capacity; s++) {
            ItemStack stack = inventory.getItemStack(s);
            if (stack == null || stack.isEmpty()) continue;
            if (!stack.getItemId().equals(PouchManager.POUCH_ITEM_ID)) continue;
            if (!PouchManager.isFull(stack)) return s;
        }
        return -1;
    }

    private static boolean hasPouchSlot(ItemContainer inventory, short capacity) {
        for (short s = 0; s < capacity; s++) {
            ItemStack stack = inventory.getItemStack(s);
            if (stack != null && !stack.isEmpty() && stack.getItemId().equals(PouchManager.POUCH_ITEM_ID)) return true;
        }
        return false;
    }
}