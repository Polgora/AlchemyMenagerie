package me.Polgora;

import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.event.events.player.PlayerChatEvent;
import com.hypixel.hytale.server.core.universe.PlayerRef;

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
            sender.sendMessage(Message.raw("[Menagerie] Name cannot be blank. Rename cancelled."));
            return;
        }

        if (input.length() > MAX_NAME_LENGTH) {
            sender.sendMessage(Message.raw(
                    "[Menagerie] Name is too long (max " + MAX_NAME_LENGTH + " characters). Rename cancelled."));
            return;
        }

        UUID ownerUuid = CreatureRegistry.getOwnerUuid(creatureUuid);
        if (ownerUuid != null && CreatureRegistry.isNameTakenByOwner(ownerUuid, input, creatureUuid)) {
            sender.sendMessage(Message.raw(
                    "[Menagerie] You already have a creature named \"" + input + "\". Choose a different name."));
            return;
        }

        CreatureNameManager.setName(creatureUuid, input);
        sender.sendMessage(Message.raw("[Menagerie] Your creature has been named \"" + input + "\"."));
    }
}