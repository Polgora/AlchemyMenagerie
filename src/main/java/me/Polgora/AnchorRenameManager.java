package me.Polgora;

import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.event.events.player.PlayerChatEvent;
import com.hypixel.hytale.server.core.universe.PlayerRef;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages the chat-based anchor rename flow.
 *
 * <p>When a player clicks "Rename" in the {@link me.Polgora.pages.AnchorInfoPage},
 * their UUID is registered here alongside the target anchor. The next chat message
 * from that player is intercepted by {@link #onPlayerChat}, applied as the new name,
 * and the chat message is suppressed.
 *
 * <p>Names are trimmed, capped at {@link #MAX_NAME_LENGTH} characters, and may not
 * be blank. The player receives a confirmation or error message in either case.
 */
public class AnchorRenameManager {

    public static final int MAX_NAME_LENGTH = 32;

    /** Players currently awaiting a rename chat input: playerUuid → target anchor. */
    private static final Map<UUID, AnchorManager.AnchorPos> PENDING = new ConcurrentHashMap<>();

    private AnchorRenameManager() {}

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Registers the player as awaiting a rename chat input for the given anchor.
     * Any previously pending rename for this player is replaced.
     */
    public static void awaitRename(UUID playerUuid, AnchorManager.AnchorPos anchor) {
        PENDING.put(playerUuid, anchor);
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
     * Global {@link PlayerChatEvent} handler. If the sender is awaiting a rename,
     * the message is cancelled and applied as the new anchor name.
     */
    public static void onPlayerChat(PlayerChatEvent event) {
        PlayerRef sender = event.getSender();
        if (sender == null) return;

        UUID playerUuid = sender.getUuid();
        AnchorManager.AnchorPos anchor = PENDING.remove(playerUuid);
        if (anchor == null) return;

        // Suppress the chat message
        event.setCancelled(true);

        String input = event.getContent().trim();

        if (input.isEmpty()) {
            sender.sendMessage(Message.raw(
                    "[Menagerie] Name cannot be blank. Rename cancelled."));
            return;
        }

        if (input.length() > MAX_NAME_LENGTH) {
            sender.sendMessage(Message.raw(
                    "[Menagerie] Name is too long (max " + MAX_NAME_LENGTH + " characters). Rename cancelled."));
            return;
        }

        AnchorManager.setName(anchor.worldUuid, anchor.x, anchor.y, anchor.z, input);
        sender.sendMessage(Message.raw(
                "[Menagerie] Anchor renamed to \"" + input + "\"."));
    }
}