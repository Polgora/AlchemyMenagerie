package me.Polgora;

import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.inventory.ItemStack;

/**
 * Swaps vanilla item stacks in a player's inventory with their "_Known" counterparts,
 * preserving each item's original slot position.
 */
public class KnownItemSwapper {

    public static final String KNOWN_SUFFIX = "_Known";

    /**
     * Replaces every _Known item across the player's entire inventory with its
     * vanilla counterpart, in-place at the same slot. Called when the journal is cleared.
     */
    public static void swapToVanilla(Player player) {
        player.getInventory().getCombinedArmorHotbarUtilityStorage().replaceAll((slot, stack) -> {
            if (!stack.isEmpty()) {
                String id = stack.getItemId();
                if (id.endsWith(KNOWN_SUFFIX)) {
                    return new ItemStack(id.substring(0, id.length() - KNOWN_SUFFIX.length()), stack.getQuantity());
                }
            }
            return stack;
        });
    }

    /**
     * Replaces every stack of {@code itemId} across the player's entire inventory
     * with {@code itemId_Known}, in-place at the same slot.
     */
    public static void swapToKnown(Player player, String itemId) {
        String knownId = itemId + KNOWN_SUFFIX;
        player.getInventory().getCombinedArmorHotbarUtilityStorage().replaceAll((slot, stack) ->
            !stack.isEmpty() && itemId.equals(stack.getItemId())
                ? new ItemStack(knownId, stack.getQuantity())
                : stack
        );
    }
}