package me.Polgora.events;

import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.event.events.entity.LivingEntityInventoryChangeEvent;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import me.Polgora.JournalManager;
import me.Polgora.KnownItemSwapper;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Listens for inventory changes on living entities and swaps any vanilla item
 * the player has already scanned with its _Known counterpart, in-place.
 * Covers all acquisition paths: pickup, looting, trading, etc.
 */
public class InventorySwapEvent {

    // Tracks players whose inventory is currently being swapped to prevent
    // replaceAll / addItemStack from re-triggering this handler recursively.
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

            // Convert any vanilla items that are now known into their _Known variants.
            // Track each converted slot and its new stack so we can re-place them.
            Map<Short, ItemStack> converted = new LinkedHashMap<>();
            container.replaceAll((slot, stack) -> {
                if (!stack.isEmpty()) {
                    String id = stack.getItemId();
                    if (!id.endsWith(KnownItemSwapper.KNOWN_SUFFIX) && JournalManager.isKnown(playerId, id)) {
                        var knownStack = new ItemStack(id + KnownItemSwapper.KNOWN_SUFFIX, stack.getQuantity());
                        converted.put(slot, knownStack);
                        return knownStack;
                    }
                }
                return stack;
            });

            if (converted.isEmpty()) return;

            // Remove each freshly-converted item from its temporary slot and re-add
            // it to the full inventory. addItemStack uses the engine's own stacking
            // logic — it merges with existing _Known stacks first (respecting max
            // stack size) and only occupies a new slot if nothing can absorb it.
            var everything = player.getInventory().getCombinedEverything();
            for (var entry : converted.entrySet()) {
                container.removeItemStackFromSlot(entry.getKey());
                everything.addItemStack(entry.getValue());
            }
        } finally {
            processing.remove(playerId);
        }
    }
}