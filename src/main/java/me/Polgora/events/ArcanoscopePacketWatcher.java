package me.Polgora.events;

import com.hypixel.hytale.protocol.BlockPosition;
import com.hypixel.hytale.protocol.Direction;
import com.hypixel.hytale.protocol.InteractionState;
import com.hypixel.hytale.protocol.InteractionType;
import com.hypixel.hytale.protocol.Packet;
import com.hypixel.hytale.protocol.packets.interaction.SyncInteractionChain;
import com.hypixel.hytale.protocol.packets.interaction.SyncInteractionChains;
import com.hypixel.hytale.protocol.packets.player.ClientMovement;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.io.adapter.PlayerPacketWatcher;
import com.hypixel.hytale.server.core.modules.i18n.I18nModule;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import me.Polgora.JournalManager;

import java.util.Map;
import java.util.concurrent.*;

/**
 * Listens for SyncInteractionChains (scan start/stop) and ClientMovement (look direction).
 *
 * Flow:
 *   - Player presses secondary → 5-second scan timer begins, look direction recorded.
 *   - Any change in yaw/pitch during the scan → scan is immediately cancelled.
 *   - Interaction ends (Finished/Failed) → scan is cancelled.
 *   - Timer completes undisturbed → block ID recorded in journal.
 */
public class ArcanoscopePacketWatcher implements PlayerPacketWatcher {

    private static final String ARCANOSCOPE_ITEM_ID = "Tool_Arcanoscope";
    private static final long SCAN_DURATION_MS = 5000L;
    private static final float LOOK_THRESHOLD = 0.3f;

    private final ScheduledExecutorService scheduler =
            Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "arcanoscope-scan");
                t.setDaemon(true);
                return t;
            });

    // playerId -> active scan future
    private final Map<String, ScheduledFuture<?>> activeScans = new ConcurrentHashMap<>();
    // playerId -> look direction at scan start
    private final Map<String, Direction> scanLookOrigins = new ConcurrentHashMap<>();
    // playerId -> most recent look direction (updated on every ClientMovement)
    private final Map<String, Direction> lastKnownLook = new ConcurrentHashMap<>();

    @Override
    public void accept(PlayerRef playerRef, Packet packet) {
        if (packet instanceof SyncInteractionChains chains) {
            handleInteractionChains(playerRef, chains);
        } else if (packet instanceof ClientMovement movement) {
            handleClientMovement(playerRef, movement);
        }
    }

    // ----- SyncInteractionChains -----

    private void handleInteractionChains(PlayerRef playerRef, SyncInteractionChains chains) {
        for (SyncInteractionChain chain : chains.updates) {
            if (!ARCANOSCOPE_ITEM_ID.equals(chain.itemInHandId)) continue;
            if (chain.interactionType != InteractionType.Secondary) continue;

            String playerId = playerRef.getUuid().toString();

            if (chain.state == InteractionState.NotFinished) {
                if (!activeScans.containsKey(playerId)) {
                    handleScanStart(playerRef, playerId, chain);
                }
            } else if (chain.state == InteractionState.Finished
                    || chain.state == InteractionState.Failed) {
                cancelScan(playerId);
            }
        }
    }

    private void handleScanStart(PlayerRef playerRef, String playerId, SyncInteractionChain chain) {
        BlockPosition pos = chain.data != null ? chain.data.blockPosition : null;
        if (pos == null) {
            playerRef.sendMessage(Message.raw("[Arcanoscope] No valid target."));
            return;
        }

        String targetBlockId = resolveBlockId(playerRef, pos);
        if (targetBlockId == null || targetBlockId.isEmpty()) {
            playerRef.sendMessage(Message.raw("[Arcanoscope] No valid target."));
            return;
        }

        if (JournalManager.isKnown(playerId, targetBlockId)) {
            playerRef.sendMessage(Message.raw("[Arcanoscope] Already in journal: " + resolveDisplayName(targetBlockId)));
            return;
        }

        playerRef.sendMessage(Message.raw("[Arcanoscope] Scanning... (5s) Hold still!"));

        // Seed the scan's look origin from the most recently received ClientMovement
        Direction currentLook = lastKnownLook.get(playerId);
        if (currentLook != null) {
            scanLookOrigins.put(playerId, currentLook);
        }

        ScheduledFuture<?> future = scheduler.schedule(() -> {
            activeScans.remove(playerId);
            scanLookOrigins.remove(playerId);
            boolean isNew = JournalManager.addKnownItem(playerId, targetBlockId);
            if (isNew) {
                playerRef.sendMessage(Message.raw("[Arcanoscope] Scan complete! Added to journal: " + resolveDisplayName(targetBlockId)));
            }
        }, SCAN_DURATION_MS, TimeUnit.MILLISECONDS);

        activeScans.put(playerId, future);
    }

    // ----- ClientMovement -----

    private void handleClientMovement(PlayerRef playerRef, ClientMovement movement) {
        if (movement.lookOrientation == null) return;

        String playerId = playerRef.getUuid().toString();
        lastKnownLook.put(playerId, movement.lookOrientation);

        if (!activeScans.containsKey(playerId)) return;

        Direction origin = scanLookOrigins.get(playerId);
        if (origin == null) {
            // First movement packet after scan started — record as baseline
            scanLookOrigins.put(playerId, movement.lookOrientation);
            return;
        }

        if (Math.abs(movement.lookOrientation.yaw - origin.yaw) > LOOK_THRESHOLD
                || Math.abs(movement.lookOrientation.pitch - origin.pitch) > LOOK_THRESHOLD) {
            cancelScan(playerId);
            playerRef.sendMessage(Message.raw("[Arcanoscope] Scan interrupted."));
        }
    }

    // ----- Helpers -----

    private void cancelScan(String playerId) {
        ScheduledFuture<?> existing = activeScans.remove(playerId);
        if (existing != null) existing.cancel(false);
        scanLookOrigins.remove(playerId);
    }

    private String resolveBlockId(PlayerRef playerRef, BlockPosition pos) {
        var world = Universe.get().getWorld(playerRef.getWorldUuid());
        if (world == null) return null;

        BlockType blockType = world.getBlockType(pos.x, pos.y, pos.z);
        if (blockType == null) return null;

        return blockType.getId();
    }

    private String resolveDisplayName(String blockTypeId) {
        String key = "server.items." + blockTypeId + ".name";
        String name = I18nModule.get().getMessage(I18nModule.DEFAULT_LANGUAGE, key);
        return (name != null && !name.isBlank()) ? name : blockTypeId;
    }

    public void shutdown() {
        scheduler.shutdownNow();
    }
}