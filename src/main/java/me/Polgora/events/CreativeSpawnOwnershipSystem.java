package me.Polgora.events;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.EntityEventSystem;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import com.hypixel.hytale.server.core.prefab.event.PrefabPasteEvent;
import com.hypixel.hytale.server.core.prefab.event.PrefabPlaceEntityEvent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import me.Polgora.CreatureRegistry;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Assigns creature ownership when a player spawns a Menagerie creature via the
 * in-game creative/builder mode (prefab paste).
 *
 * <p>Works in two correlated phases using the shared {@code prefabId} key:
 * <ol>
 *   <li>{@link PasteTracker} catches {@link PrefabPasteEvent} on the player entity
 *       and records {@code prefabId → (ownerUuid, ownerName)}.
 *   <li>{@link PlaceTracker} catches {@link PrefabPlaceEntityEvent} on the player
 *       entity, reads the entity UUID from the holder, and registers ownership.
 * </ol>
 */
public class CreativeSpawnOwnershipSystem {

    /** Correlates an in-flight prefab paste to the initiating player. */
    private static final Map<Integer, UUID>   PENDING_UUID = new ConcurrentHashMap<>();
    private static final Map<Integer, String> PENDING_NAME = new ConcurrentHashMap<>();

    // ── Phase 1: record which player triggered this paste ─────────────────────

    public static class PasteTracker extends EntityEventSystem<EntityStore, PrefabPasteEvent> {

        public PasteTracker() { super(PrefabPasteEvent.class); }

        @Override
        public Query<EntityStore> getQuery() { return Query.any(); }

        @Override
        public void handle(int index, ArchetypeChunk<EntityStore> chunk, Store<EntityStore> store,
                           CommandBuffer<EntityStore> cmd, PrefabPasteEvent event) {
            if (!event.isPasteStart()) return;

            Player player = chunk.getComponent(index, Player.getComponentType());
            if (player == null) return;

            PlayerRef playerRef = chunk.getComponent(index, PlayerRef.getComponentType());
            if (playerRef == null) return;

            PENDING_UUID.put(event.getPrefabId(), playerRef.getUuid());
            PENDING_NAME.put(event.getPrefabId(), playerRef.getUsername());
        }
    }

    // ── Phase 2: register ownership on each entity placed by this paste ───────

    public static class PlaceTracker extends EntityEventSystem<EntityStore, PrefabPlaceEntityEvent> {

        public PlaceTracker() { super(PrefabPlaceEntityEvent.class); }

        @Override
        public Query<EntityStore> getQuery() { return Query.any(); }

        @Override
        public void handle(int index, ArchetypeChunk<EntityStore> chunk, Store<EntityStore> store,
                           CommandBuffer<EntityStore> cmd, PrefabPlaceEntityEvent event) {
            UUID   ownerUuid = PENDING_UUID.get(event.getPrefabId());
            String ownerName = PENDING_NAME.get(event.getPrefabId());
            if (ownerUuid == null) return;

            UUIDComponent uuidComp = event.getHolder().getComponent(UUIDComponent.getComponentType());
            if (uuidComp == null || uuidComp.getUuid() == null) return;

            NPCEntity npc = event.getHolder().getComponent(NPCEntity.getComponentType());
            String roleName = (npc != null) ? npc.getRoleName() : null;

            CreatureRegistry.registerExternal(uuidComp.getUuid(), ownerUuid, ownerName, roleName);
        }
    }
}