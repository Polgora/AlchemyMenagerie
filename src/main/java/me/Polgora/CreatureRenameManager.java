package me.Polgora;

import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.event.events.player.PlayerChatEvent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.util.NotificationUtil;
import com.hypixel.hytale.protocol.packets.interface_.NotificationStyle;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages the chat-based creature rename flow.
 *
 * <p>When a player clicks "Rename" in {@link me.Polgora.pages.CreatureInfoPage},
 * their UUID is registered here alongside the target creature UUID. The next chat
 * message from that player is intercepted by {@link #onPlayerChat}, applied as the
 * creature's custom name via {@link CreatureNameManager}, and the chat message is
 * suppressed.
 *
 * <p>Names are trimmed, capped at {@link #MAX_NAME_LENGTH} characters, and may not
 * be blank. The player receives a confirmation or error message in either case.
 */
public class CreatureRenameManager {

    public static final int MAX_NAME_LENGTH = 32;

    /** Players currently awaiting a rename chat input: playerUuid → creatureUuid. */
    private static final Map<UUID, UUID> PENDING = new ConcurrentHashMap<>();

    private CreatureRenameManager() {}

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Registers the player as awaiting a rename chat input for the given creature.
     * Any previously pending rename for this player is replaced.
     */
    public static void awaitRename(UUID playerUuid, UUID creatureUuid) {
        PENDING.put(playerUuid, creatureUuid);
    }

    /** Cancels any pending rename for the given player (e.g. on disconnect). */
    public static void cancelPending(UUID playerUuid) {
        PENDING.remove(playerUuid);
    }

    /** Returns {@code true} if the player is currently awaiting a rename chat input. */
    public static boolean isPending(UUID playerUuid) {
        return PENDING.containsKey(playerUuid);
    }

    // ── Chat intercept ────────────────────────────────────────────────────────

    /**
     * Global {@link PlayerChatEvent} handler. If the sender is awaiting a creature
     * rename, the message is cancelled and applied as the new creature name.
     */
    public static void onPlayerChat(PlayerChatEvent event) {
        PlayerRef sender = event.getSender();
        if (sender == null) return;

        UUID playerUuid   = sender.getUuid();
        UUID creatureUuid = PENDING.remove(playerUuid);
        if (creatureUuid == null) return;

        event.setCancelled(true);

        String input = event.getContent().trim();

        if (input.isEmpty()) {
            NotificationUtil.sendNotification(sender.getPacketHandler(),
                    Message.raw("Name cannot be blank. Rename cancelled."), NotificationStyle.Warning);
            return;
        }

        if (input.length() > MAX_NAME_LENGTH) {
            NotificationUtil.sendNotification(sender.getPacketHandler(),
                    Message.raw("Name is too long (max " + MAX_NAME_LENGTH + " characters). Rename cancelled."), NotificationStyle.Warning);
            return;
        }

        UUID ownerUuid = CreatureRegistry.getOwnerUuid(creatureUuid);
        if (ownerUuid != null && CreatureRegistry.isNameTakenByOwner(ownerUuid, input, creatureUuid)) {
            NotificationUtil.sendNotification(sender.getPacketHandler(),
                    Message.raw("You already have a creature named \"" + input + "\". Choose a different name."), NotificationStyle.Warning);
            return;
        }

        CreatureNameManager.setName(creatureUuid, input);
        NotificationUtil.sendNotification(sender.getPacketHandler(),
                Message.raw("Your creature has been named \"" + input + "\"."), NotificationStyle.Success);
    }
}