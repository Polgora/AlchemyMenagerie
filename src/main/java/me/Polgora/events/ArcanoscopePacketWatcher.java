package me.Polgora.events;

import com.hypixel.hytale.protocol.BlockPosition;
import com.hypixel.hytale.protocol.Direction;
import com.hypixel.hytale.protocol.InteractionState;
import com.hypixel.hytale.protocol.InteractionType;
import com.hypixel.hytale.protocol.Packet;
import com.hypixel.hytale.protocol.Position;
import com.hypixel.hytale.server.core.entity.InteractionChain;
import com.hypixel.hytale.server.core.entity.InteractionManager;
import com.hypixel.hytale.server.core.modules.interaction.InteractionModule;
import com.hypixel.hytale.protocol.packets.world.CancelParticleSystems;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.world.World;
import org.joml.Vector3d;
import com.hypixel.hytale.protocol.packets.interaction.CancelInteractionChain;
import com.hypixel.hytale.protocol.packets.interaction.SyncInteractionChain;
import com.hypixel.hytale.protocol.packets.interaction.SyncInteractionChains;
import com.hypixel.hytale.protocol.packets.player.ClientMovement;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.util.NotificationUtil;
import com.hypixel.hytale.protocol.packets.interface_.NotificationStyle;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.io.adapter.PlayerPacketWatcher;
import com.hypixel.hytale.server.core.modules.i18n.I18nModule;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import me.Polgora.JournalManager;
import me.Polgora.KnownItemSwapper;

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
    private static final String SCAN_PARTICLE_ID = "Tool_Arcanoscope_ParticleSystem";
    private static final long SCAN_DURATION_MS = 5000L;
    private static final float LOOK_THRESHOLD = 0.2f;

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
            rejectScan(playerRef, chain, "No valid target.");
            return;
        }

        var world = Universe.get().getWorld(playerRef.getWorldUuid());
        if (world == null) {
            rejectScan(playerRef, chain, "No valid target.");
            return;
        }

        // This watcher runs on a packet I/O thread, but block/component reads
        // must happen on the world's own tick thread — defer there.
        world.execute(() -> {
            String targetBlockId = resolveBlockId(world, pos);
            if (targetBlockId == null || targetBlockId.isEmpty()) {
                rejectScan(playerRef, chain, "No valid target.");
                return;
            }

            if (!JournalManager.isScannableItem(targetBlockId)) {
                rejectScan(playerRef, chain, "This item cannot be analyzed.");
                return;
            }

            if (JournalManager.isKnown(playerId, targetBlockId)) {
                rejectScan(playerRef, chain, "Already in journal: " + resolveDisplayName(targetBlockId));
                return;
            }

            NotificationUtil.sendNotification(playerRef.getPacketHandler(), Message.raw("Scanning... (5s) Hold still!"), NotificationStyle.Default);

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
                    NotificationUtil.sendNotification(playerRef.getPacketHandler(), Message.raw("Scan complete! Added to journal: " + resolveDisplayName(targetBlockId)), NotificationStyle.Success);
                    world.execute(() -> {
                        var entityRef = world.getEntityRef(playerRef.getUuid());
                        if (entityRef == null) return;
                        var storeInst = world.getEntityStore().getStore();
                        Player player = storeInst.getComponent(entityRef, Player.getComponentType());
                        if (player != null) {
                            KnownItemSwapper.swapToKnown(player, targetBlockId);
                        }
                    });
                }
            }, SCAN_DURATION_MS, TimeUnit.MILLISECONDS);

            activeScans.put(playerId, future);
        });
    }

    /**
     * Rejects a scan attempt: tells the player why, and cancels their secondary-use interaction chain
     * so the client stops the charge effect (the scan particles) instead of playing it for a target
     * that can't be scanned.
     */
    private void rejectScan(PlayerRef playerRef, SyncInteractionChain chain, String message) {
        NotificationUtil.sendNotification(playerRef.getPacketHandler(), Message.raw(message), NotificationStyle.Warning);
        // No cancel packets here: the root interaction only continues to the charging step (which
        // plays the particles) when the ArcanoscopeTargetCheck step passes.
    }

    /**
     * Ends the player's in-progress secondary-use interaction chain from the server side, so the client
     * stops the charge (and the scan particles it plays) when a scan is interrupted.
     */
    private void endScanChain(PlayerRef playerRef) {
        World world = Universe.get().getWorld(playerRef.getWorldUuid());
        if (world == null) return;
        world.execute(() -> {
            var entityRef = world.getEntityRef(playerRef.getUuid());
            if (entityRef == null) return;
            InteractionManager manager = world.getEntityStore().getStore()
                    .getComponent(entityRef, InteractionModule.get().getInteractionManagerComponent());
            if (manager == null) return;
            for (InteractionChain chain : new java.util.ArrayList<>(manager.getChains().values())) {
                if (chain.getType() == InteractionType.Secondary) {
                    manager.cancelChains(chain);
                }
            }
        });
    }

    /** Clears any scan particles still playing around the player (the client-side charge effect). */
    private void stopScanParticles(PlayerRef playerRef) {
        World world = Universe.get().getWorld(playerRef.getWorldUuid());
        if (world == null) return;
        world.execute(() -> {
            var entityRef = world.getEntityRef(playerRef.getUuid());
            if (entityRef == null) return;
            TransformComponent tc = world.getEntityStore().getStore()
                    .getComponent(entityRef, TransformComponent.getComponentType());
            if (tc == null) return;
            Vector3d p = tc.getPosition();
            playerRef.getPacketHandler().write(new CancelParticleSystems(
                    new Position(p.x - 4, p.y - 4, p.z - 4),
                    new Position(p.x + 4, p.y + 4, p.z + 4),
                    new String[] { SCAN_PARTICLE_ID }, true));
        });
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
            endScanChain(playerRef);
            NotificationUtil.sendNotification(playerRef.getPacketHandler(), Message.raw("Scan interrupted."), NotificationStyle.Warning);
        }
    }

    // ----- Helpers -----

    private void cancelScan(String playerId) {
        ScheduledFuture<?> existing = activeScans.remove(playerId);
        if (existing != null) {
            existing.cancel(false);
            PlayerRef playerRef = Universe.get().getPlayer(java.util.UUID.fromString(playerId));
            if (playerRef != null) stopScanParticles(playerRef);
        }
        scanLookOrigins.remove(playerId);
    }

    /** Must be called on the given world's own tick thread. */
    private String resolveBlockId(com.hypixel.hytale.server.core.universe.world.World world, BlockPosition pos) {
        BlockType blockType = me.Polgora.WorldBlockUtil.getBlockType(world, pos.x, pos.y, pos.z);
        if (blockType == null) return null;

        // A placed _Known block is the same block for scanning purposes as its vanilla form.
        return me.Polgora.KnownItemSwapper.baseId(blockType.getId());
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