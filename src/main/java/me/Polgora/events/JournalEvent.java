package me.Polgora.events;

import com.hypixel.hytale.server.core.command.system.CommandSender;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.event.events.player.PlayerConnectEvent;
import com.hypixel.hytale.server.core.event.events.player.PlayerDisconnectEvent;
import com.hypixel.hytale.server.core.event.events.player.PlayerReadyEvent;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import me.Polgora.JournalManager;
import me.Polgora.KnownItemSwapper;
import me.Polgora.MenagerieJournalManager;

public class JournalEvent {

    public static void onPlayerConnect(PlayerConnectEvent event) {
        String playerId = event.getPlayerRef().getUuid().toString();
        JournalManager.loadPlayer(playerId);
        JournalManager.loadPlayerCreatures(playerId);
        MenagerieJournalManager.loadBestiary(playerId);
        MenagerieJournalManager.loadDeceased(playerId);
    }

    /**
     * On login, scans the player's full inventory and ensures every item is in
     * the correct variant: vanilla items the player knows become _Known, and any
     * _Known items whose base form is no longer known revert to vanilla.
     */
    public static void onPlayerReady(PlayerReadyEvent event) {
        Player player = event.getPlayer();
        String playerId = ((CommandSender) player).getUuid().toString();

        ItemContainer inventory = player.getInventory().getCombinedArmorHotbarUtilityStorage();
        short capacity = inventory.getCapacity();
        boolean changed = false;

        for (short s = 0; s < capacity; s++) {
            ItemStack stack = inventory.getItemStack(s);
            if (stack == null || stack.isEmpty()) continue;
            String id = stack.getItemId();

            if (!id.endsWith(KnownItemSwapper.KNOWN_SUFFIX)) {
                if (JournalManager.isKnown(playerId, id)) {
                    inventory.setItemStackForSlot(s, new ItemStack(id + KnownItemSwapper.KNOWN_SUFFIX, stack.getQuantity()));
                    changed = true;
                }
            } else {
                String vanillaId = id.substring(0, id.length() - KnownItemSwapper.KNOWN_SUFFIX.length());
                if (!JournalManager.isKnown(playerId, vanillaId)) {
                    inventory.setItemStackForSlot(s, new ItemStack(vanillaId, stack.getQuantity()));
                    changed = true;
                }
            }
        }

    }

    public static void onPlayerDisconnect(PlayerDisconnectEvent event) {
        String playerId = event.getPlayerRef().getUuid().toString();
        JournalManager.savePlayer(playerId);
        JournalManager.unloadPlayerCreatures(playerId);
        MenagerieJournalManager.unloadBestiary(playerId);
        MenagerieJournalManager.unloadDeceased(playerId);
    }
}