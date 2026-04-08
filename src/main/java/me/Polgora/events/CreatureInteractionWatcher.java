package me.Polgora.events;

import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.protocol.InteractionState;
import com.hypixel.hytale.protocol.InteractionType;
import com.hypixel.hytale.protocol.Packet;
import com.hypixel.hytale.protocol.packets.interaction.SyncInteractionChain;
import com.hypixel.hytale.protocol.packets.interaction.SyncInteractionChains;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.io.adapter.PlayerPacketWatcher;
import com.hypixel.hytale.server.core.modules.i18n.I18nModule;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import me.Polgora.CreatureDietRegistry;
import me.Polgora.pages.CreatureInfoPage;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Intercepts {@link SyncInteractionChains} packets to detect when a player
 * right-clicks a Menagerie creature with an empty hand, then opens
 * {@link CreatureInfoPage} for that creature.
 *
 * <p>The unarmed secondary ("Use") interaction chain is sent by the client
 * with {@link InteractionType#Use} when the player right-clicks an entity
 * while holding nothing. The chain's {@code data.proxyId} carries the target
 * entity's UUID.
 */
public class CreatureInteractionWatcher implements PlayerPacketWatcher {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    /** Per-player cooldown to prevent the same interaction firing more than once. */
    private static final long OPEN_DEBOUNCE_MS = 500L;
    private final Map<UUID, Long> lastOpened = new ConcurrentHashMap<>();

    @Override
    public void accept(PlayerRef playerRef, Packet packet) {
        if (!(packet instanceof SyncInteractionChains chains)) return;

        for (SyncInteractionChain chain : chains.updates) {
            if (!isUseInteraction(chain)) continue;
            if (chain.data == null || chain.data.proxyId == null) continue;

            UUID playerId = playerRef.getUuid();
            UUID proxyId  = chain.data.proxyId;

            // Debounce: skip if we already opened a page for this player recently
            long now = System.currentTimeMillis();
            Long last = lastOpened.get(playerId);
            if (last != null && now - last < OPEN_DEBOUNCE_MS) continue;
            lastOpened.put(playerId, now);

            openCreaturePage(playerRef, proxyId);
            break; // only process one matching chain per packet
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static boolean isUseInteraction(SyncInteractionChain chain) {
        // InteractionType.Use is the empty-hand right-click interaction.
        // Do NOT filter on itemInHandId — for unarmed interactions it is set to the
        // game's default unarmed ID, not null/empty.
        if (chain.interactionType != InteractionType.Use) return false;
        return chain.state == InteractionState.NotFinished;
    }

    private void openCreaturePage(PlayerRef playerRef, UUID proxyId) {
        World world = Universe.get().getWorld(playerRef.getWorldUuid());
        if (world == null) return;

        world.execute(() -> {
            var entityStore = world.getEntityStore();
            var store       = entityStore.getStore();

            // Resolve the player entity (world.getEntityRef is for players)
            var playerEntityRef = world.getEntityRef(playerRef.getUuid());
            if (playerEntityRef == null) return;

            Player player = store.getComponent(playerEntityRef, Player.getComponentType());
            if (player == null) return;

            // Don't open a second page if one is already showing
            if (player.getPageManager().getCustomPage() != null) return;

            // Resolve the NPC entity by UUID (use getRefFromUUID for non-player entities)
            var targetRef = entityStore.getRefFromUUID(proxyId);
            if (targetRef == null) {
                LOGGER.at(java.util.logging.Level.WARNING).log(
                        "CreatureInteractionWatcher: no entity found for proxyId %s", proxyId);
                return;
            }

            NPCEntity npc = store.getComponent(targetRef, NPCEntity.getComponentType());
            if (npc == null) return;

            String roleName = npc.getRoleName();
            if (!CreatureDietRegistry.isKnownCreature(roleName)) return;

            // Resolve display name from i18n, fall back to formatted role name
            String displayName = resolveDisplayName(roleName);

            player.getPageManager().openCustomPage(playerEntityRef, store,
                    new CreatureInfoPage(playerRef, proxyId, roleName, displayName));

            LOGGER.at(java.util.logging.Level.INFO).log(
                    "CreatureInteractionWatcher: opened CreatureInfoPage for %s (player %s)",
                    roleName, playerRef.getUuid());
        });
    }

    private static String resolveDisplayName(String roleName) {
        String key  = "server.npcRoles." + roleName + ".name";
        String name = I18nModule.get().getMessage(I18nModule.DEFAULT_LANGUAGE, key);
        return (name != null && !name.isBlank()) ? name : roleName.replace("_", " ");
    }
}