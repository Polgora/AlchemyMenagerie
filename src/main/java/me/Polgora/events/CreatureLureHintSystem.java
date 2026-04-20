package me.Polgora.events;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.tick.EntityTickingSystem;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.util.NotificationUtil;
import com.hypixel.hytale.protocol.packets.interface_.NotificationStyle;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import com.hypixel.hytale.server.npc.role.Role;
import me.Polgora.CreatureDietRegistry;

import java.util.Collection;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Sends a contextual hint to players who are holding a food item near a timid
 * Menagerie creature that is actively in its Flee state.
 *
 * <p>Ticks on NPC entities every {@link #CHECK_INTERVAL_MS} ms. If the
 * creature is currently fleeing and a nearby player is holding a
 * diet-appropriate food item, a hint is sent suggesting they crouch to approach.
 */
public class CreatureLureHintSystem extends EntityTickingSystem<EntityStore> {

    /** How often (ms) each fleeing creature checks nearby players for hint eligibility. */
    private static final long CHECK_INTERVAL_MS = 2_000L;

    /** Minimum time (ms) between hints sent to the same player. */
    private static final long HINT_COOLDOWN_MS = 120_000L; // 2 minutes

    /** Maximum range within which a fleeing creature can trigger a hint. */
    private static final double VIEW_RANGE_SQ = 24.0 * 24.0;

    /** Per-NPC: last time this creature ran the hint check. */
    private final Map<UUID, Long> lastCheckTimes = new ConcurrentHashMap<>();

    /** Per-player: last time a lure hint was sent. */
    private final Map<UUID, Long> lastHintTimes = new ConcurrentHashMap<>();

    @Override
    public Query<EntityStore> getQuery() {
        return Query.any();
    }

    @Override
    public void tick(float dt, int index, ArchetypeChunk<EntityStore> chunk,
                     Store<EntityStore> store, CommandBuffer<EntityStore> cmd) {

        NPCEntity npc = chunk.getComponent(index, NPCEntity.getComponentType());
        if (npc == null) return;

        String roleName = npc.getRoleName();
        if (!CreatureDietRegistry.isKnownCreature(roleName)) return;

        // Only act when the creature is actively fleeing
        Role role = npc.getRole();
        if (role == null) return;
        String stateName = role.getStateSupport().getStateName();
        if (stateName == null || !stateName.startsWith("Flee")) return;

        CreatureDietRegistry.DietType diet = CreatureDietRegistry.getDiet(roleName);
        if (diet == null) return;

        // Time-gate per creature to avoid checking every game tick
        Ref<EntityStore> ref = chunk.getReferenceTo(index);
        UUIDComponent uuidComp = store.getComponent(ref, UUIDComponent.getComponentType());
        if (uuidComp == null) return;
        UUID npcKey = uuidComp.getUuid();
        if (npcKey == null) return;
        long now = System.currentTimeMillis();
        Long lastCheck = lastCheckTimes.get(npcKey);
        if (lastCheck != null && now - lastCheck < CHECK_INTERVAL_MS) return;
        lastCheckTimes.put(npcKey, now);

        Vector3d npcPos = npc.getOldPosition();
        if (npcPos == null) return;

        World world = store.getExternalData().getWorld();
        Collection<PlayerRef> playerRefs = world.getPlayerRefs();
        if (playerRefs.isEmpty()) return;

        for (PlayerRef playerRef : playerRefs) {
            if (playerRef == null) continue;
            UUID playerUuid = playerRef.getUuid();

            // Check per-player hint cooldown before doing any heavier work
            Long lastHint = lastHintTimes.get(playerUuid);
            if (lastHint != null && now - lastHint < HINT_COOLDOWN_MS) continue;

            var transform = playerRef.getTransform();
            if (transform == null) continue;
            Vector3d playerPos = transform.getPosition();
            if (playerPos == null) continue;

            double dx = npcPos.x - playerPos.x;
            double dy = npcPos.y - playerPos.y;
            double dz = npcPos.z - playerPos.z;
            if (dx * dx + dy * dy + dz * dz > VIEW_RANGE_SQ) continue;

            // Check if this player is holding a diet-appropriate food item
            Player player = store.getComponent(playerRef.getReference(), Player.getComponentType());
            if (player == null) continue;

            // Use var to avoid referencing the deprecated Inventory type by name
            var inv = player.getInventory();
            if (inv == null) continue;

            ItemStack main = inv.getItemInHand();
            boolean holdingFood = main != null && !main.isEmpty()
                    && CreatureDietRegistry.isAcceptedFood(diet, main.getItemId());

            if (!holdingFood) {
                ItemStack off = inv.getUtilityItem();
                holdingFood = off != null && !off.isEmpty()
                        && CreatureDietRegistry.isAcceptedFood(diet, off.getItemId());
            }

            if (!holdingFood) continue;

            // Creature is actively fleeing and player is nearby holding matching food
            lastHintTimes.put(playerUuid, now);
            NotificationUtil.sendNotification(playerRef.getPacketHandler(),
                    Message.raw("This creature appears to be timid! Try approaching while crouching."), NotificationStyle.Default);
        }
    }
}