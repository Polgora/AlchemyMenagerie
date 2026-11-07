package me.Polgora;

import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;

/**
 * Swaps vanilla item stacks in a player's inventory with their "_Known" counterparts,
 * preserving each item's original slot position.
 *
 * Uses explicit per-slot {@code setItemStackForSlot} rather than {@code replaceAll}
 * so that only changed slots are marked dirty and the client receives targeted updates.
 * Must be called from the game thread (e.g. inside {@code world.execute()}).
 */
public class KnownItemSwapper {

    public static final String KNOWN_SUFFIX = "_Known";

    /**
     * Strips a trailing "_Known" suffix if present, otherwise returns the ID unchanged.
     * A placed _Known block is identical in composition to its vanilla counterpart —
     * callers resolving a world block ID for journal/Arcanoscope lookups should
     * normalize through this first so a Known-variant placement scans/harvests the
     * same as the vanilla block.
     */
    public static String baseId(String itemId) {
        return itemId.endsWith(KNOWN_SUFFIX)
                ? itemId.substring(0, itemId.length() - KNOWN_SUFFIX.length())
                : itemId;
    }

    /**
     * Replaces every _Known item across the player's entire inventory with its
     * vanilla counterpart, in-place at the same slot. Called when the journal is cleared.
     */
    public static void swapToVanilla(Player player) {
        ItemContainer inv = player.getInventory().getCombinedArmorHotbarUtilityStorage();
        short capacity = inv.getCapacity();
        for (short s = 0; s < capacity; s++) {
            ItemStack stack = inv.getItemStack(s);
            if (stack == null || stack.isEmpty()) continue;
            String id = stack.getItemId();
            if (id.endsWith(KNOWN_SUFFIX)) {
                inv.setItemStackForSlot(s, new ItemStack(
                        id.substring(0, id.length() - KNOWN_SUFFIX.length()), stack.getQuantity()));
            }
        }
    }

    /**
     * Replaces every stack of {@code itemId} across the player's entire inventory
     * with {@code itemId_Known}, in-place at the same slot.
     */
    public static void swapToKnown(Player player, String itemId) {
        String knownId = itemId + KNOWN_SUFFIX;
        ItemContainer inv = player.getInventory().getCombinedArmorHotbarUtilityStorage();
        short capacity = inv.getCapacity();
        for (short s = 0; s < capacity; s++) {
            ItemStack stack = inv.getItemStack(s);
            if (stack == null || stack.isEmpty()) continue;
            if (itemId.equals(stack.getItemId())) {
                inv.setItemStackForSlot(s, new ItemStack(knownId, stack.getQuantity()));
            }
        }
    }
}