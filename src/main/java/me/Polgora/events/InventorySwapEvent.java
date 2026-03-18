package me.Polgora.events;

import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.ItemUtils;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.player.windows.ItemContainerWindow;
import com.hypixel.hytale.server.core.event.events.entity.LivingEntityInventoryChangeEvent;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import me.Polgora.JournalManager;
import me.Polgora.KnownItemSwapper;
import me.Polgora.PouchManager;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Listens for inventory changes on living entities and swaps any vanilla item
 * the player has already scanned with its _Known counterpart, in-place.
 * Also assigns pouchIds to new Flask Pouches and routes any Alchemy Flasks
 * into the first available pouch.
 */
public class InventorySwapEvent {

    // Tracks players whose inventory is currently being swapped to prevent
    // replaceAll / addItemStack / setItemStackForSlot from re-triggering recursively.
    private static final Set<String> processing = ConcurrentHashMap.newKeySet();

    @SuppressWarnings("removal")
    public static void onInventoryChange(LivingEntityInventoryChangeEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (player.isWaitingForClientReady()) return;
        var uuid = player.getUuid();
        if (uuid == null) return;
        String playerId = uuid.toString();

        if (!processing.add(playerId)) return;
        try {
            var container = event.getItemContainer();
            if (container == null) return;

            // Convert any vanilla items that are now known into their _Known variants,
            // and assign a pouchId to any Flask Pouch that doesn't have one yet.
            Map<Short, ItemStack> converted = new LinkedHashMap<>();
            container.replaceAll((slot, stack) -> {
                if (stack.isEmpty()) return stack;
                String id = stack.getItemId();

                // Knowledge swap
                if (!id.endsWith(KnownItemSwapper.KNOWN_SUFFIX) && JournalManager.isKnown(playerId, id)) {
                    var knownStack = new ItemStack(id + KnownItemSwapper.KNOWN_SUFFIX, stack.getQuantity());
                    converted.put(slot, knownStack);
                    return knownStack;
                }

                // Assign pouchId to a Flask Pouch that doesn't have one
                if (id.equals(PouchManager.POUCH_ITEM_ID) && PouchManager.getPouchId(stack) == null) {
                    return PouchManager.withNewPouchId(stack);
                }

                return stack;
            });

            var everything = player.getInventory().getCombinedEverything();

            if (!converted.isEmpty()) {
                for (var entry : converted.entrySet()) {
                    container.removeItemStackFromSlot(entry.getKey());
                    everything.addItemStack(entry.getValue());
                }
            }

            // Enforce one Flask Pouch per inventory. If a second one arrives, discard it.
            // Players may store extras in chests; each pouch retains its own contents.
            short capacity = everything.getCapacity();
            boolean hasPouch = false;
            for (short s = 0; s < capacity; s++) {
                ItemStack stack = everything.getItemStack(s);
                if (stack == null || stack.isEmpty()) continue;
                if (!stack.getItemId().equals(PouchManager.POUCH_ITEM_ID)) continue;
                if (!hasPouch) {
                    hasPouch = true;
                    // Pouch arrived — clear the in-flight flag so future pickups
                    // now use the inventory scan path instead.
                    PouchPickupPreventionSystem.pouchPickupInFlight.remove(playerId);
                } else {
                    // Return the extra pouch to whichever container the player has open
                    // (e.g. the chest it was pulled from), rather than destroying it.
                    ItemStack extra = everything.getItemStack(s).withQuantity(1);
                    everything.removeItemStackFromSlot(s, 1);
                    returnToOpenContainer(player, extra);
                    player.sendMessage(Message.raw("You can only carry one Flask Pouch at a time."));
                }
            }

            // Route any Alchemy Flasks that landed in the inventory into the first
            // available Flask Pouch. Discards and notifies if no pouch has room.
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
                        if (!messageSent) {
                            boolean hasAnyPouch = hasPouchSlot(everything, capacity);
                            player.sendMessage(Message.raw(hasAnyPouch
                                    ? "Flask Pouch full! Make room in your pouch before adding more flasks."
                                    : "You need a Flask Pouch to store Alchemy Flasks."));
                            messageSent = true;
                        }
                    } else {
                        ItemStack pouch = everything.getItemStack(pouchSlot);
                        everything.setItemStackForSlot(pouchSlot, PouchManager.withFlaskAdded(pouch, new ItemStack(itemId, 1)));
                    }
                }
            }
        } finally {
            processing.remove(playerId);
        }
    }

    /**
     * Tries to add {@code item} back to the first open ItemContainer window the
     * player has (e.g. a chest). This keeps the item in the chest rather than
     * destroying it when the player already has a Flask Pouch in their inventory.
     */
    private static void returnToOpenContainer(Player player, ItemStack item) {
        for (var window : player.getWindowManager().getWindows()) {
            if (window instanceof ItemContainerWindow icw) {
                icw.getItemContainer().addItemStack(item);
                return;
            }
        }
        // No open container — item was picked up from the ground. Drop it back.
        var store = player.getWorld().getEntityStore().getStore();
        ItemUtils.dropItem(player.getReference(), item, store);
    }

    /** Returns the slot index of the first Flask Pouch that is not full, or -1. */
    private static short findNonFullPouchSlot(ItemContainer inventory, short capacity) {
        for (short s = 0; s < capacity; s++) {
            ItemStack stack = inventory.getItemStack(s);
            if (stack == null || stack.isEmpty()) continue;
            if (!stack.getItemId().equals(PouchManager.POUCH_ITEM_ID)) continue;
            if (!PouchManager.isFull(stack)) return s;
        }
        return -1;
    }

    /** Returns true if the player has at least one Flask Pouch (regardless of fullness). */
    private static boolean hasPouchSlot(ItemContainer inventory, short capacity) {
        for (short s = 0; s < capacity; s++) {
            ItemStack stack = inventory.getItemStack(s);
            if (stack != null && !stack.isEmpty() && stack.getItemId().equals(PouchManager.POUCH_ITEM_ID)) return true;
        }
        return false;
    }
}