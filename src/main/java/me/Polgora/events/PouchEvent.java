package me.Polgora.events;

import com.hypixel.hytale.server.core.event.events.player.PlayerConnectEvent;
import com.hypixel.hytale.server.core.event.events.player.PlayerDisconnectEvent;
import me.Polgora.PouchManager;

public class PouchEvent {

    public static void onPlayerConnect(PlayerConnectEvent event) {
        String playerId = event.getPlayerRef().getUuid().toString();
        PouchManager.loadPlayer(playerId);
    }

    public static void onPlayerDisconnect(PlayerDisconnectEvent event) {
        String playerId = event.getPlayerRef().getUuid().toString();
        PouchManager.savePlayer(playerId);
    }
}