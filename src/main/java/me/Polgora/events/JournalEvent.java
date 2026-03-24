package me.Polgora.events;

import com.hypixel.hytale.server.core.event.events.player.PlayerConnectEvent;
import com.hypixel.hytale.server.core.event.events.player.PlayerDisconnectEvent;
import me.Polgora.JournalManager;

public class JournalEvent {

    public static void onPlayerConnect(PlayerConnectEvent event) {
        String playerId = event.getPlayerRef().getUuid().toString();
        JournalManager.loadPlayer(playerId);
        JournalManager.loadPlayerCreatures(playerId);
    }

    public static void onPlayerDisconnect(PlayerDisconnectEvent event) {
        String playerId = event.getPlayerRef().getUuid().toString();
        JournalManager.savePlayer(playerId);
        JournalManager.unloadPlayerCreatures(playerId);
    }
}