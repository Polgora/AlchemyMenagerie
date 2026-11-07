package me.Polgora.events;

import com.hypixel.hytale.component.AddReason;
import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Holder;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.tick.EntityTickingSystem;
import com.hypixel.hytale.logger.HytaleLogger;
import org.joml.Vector3d;
import com.hypixel.hytale.math.vector.Rotation3f;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.modules.entity.item.ItemComponent;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import me.Polgora.AnchorManager;
import me.Polgora.CreatureBondManager;
import me.Polgora.CreatureDropManager;

import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Periodically generates resource drops for Menagerie creatures within anchor range.
 *
 * <p>Runs as an ECS ticking system. Every tick it checks each NPC entity; the
 * per-entity time gate means the actual drop logic only fires once every
 * {@link #DROP_INTERVAL_MS} milliseconds per individual creature.
 */
public class AnchorResourceSystem extends EntityTickingSystem<EntityStore> {

    /** How often (in ms) each creature generates resources while in anchor range. */
    private static final long DROP_INTERVAL_MS = 5 * 60 * 1000L; // 5 minutes

    private static final Random RANDOM = new Random();
    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    /** Last drop timestamp per creature UUID. Entries linger but are small. */
    private final Map<UUID, Long> lastDropTimes = new ConcurrentHashMap<>();

    @Override
    public Query<EntityStore> getQuery() {
        return Query.any();
    }

    @Override
    public void tick(float dt, int index, ArchetypeChunk<EntityStore> chunk,
                     Store<EntityStore> store, CommandBuffer<EntityStore> cmd) {

        // Only process NPC entities that have a configured drop table
        NPCEntity npc = chunk.getComponent(index, NPCEntity.getComponentType());
        if (npc == null) return;

        String roleName = npc.getRoleName();
        if (roleName == null || !CreatureDropManager.hasDrops(roleName)) return;

        // Bail early if no anchors exist at all (fast path)
        if (AnchorManager.getAnchors().isEmpty()) return;

        // Check position against anchor radius
        Vector3d pos = npc.getOldPosition();
        if (pos == null) return;

        World world = store.getExternalData().getWorld();
        UUID worldUuid = world.getWorldConfig().getUuid();
        if (!AnchorManager.isWithinAnyAnchor(worldUuid, pos.x, pos.y, pos.z)) return;

        // Time-gate: each creature drops independently every DROP_INTERVAL_MS
        Ref<EntityStore> ref = chunk.getReferenceTo(index);
        UUIDComponent uuidComp = store.getComponent(ref, UUIDComponent.getComponentType());
        if (uuidComp == null) return;
        UUID entityUuid = uuidComp.getUuid();
        if (entityUuid == null) return;

        long now = System.currentTimeMillis();
        Long last = lastDropTimes.get(entityUuid);
        if (last != null && now - last < DROP_INTERVAL_MS) return;
        lastDropTimes.put(entityUuid, now);

        // Roll and spawn drops, scaled by the creature's bond tier multiplier
        CreatureBondManager.BondTier bondTier = CreatureBondManager.getTier(entityUuid);
        List<ItemStack> drops = CreatureDropManager.rollDrops(roleName, RANDOM, bondTier.dropMultiplier);
        if (drops.isEmpty()) return;

        @SuppressWarnings("unchecked")
        Holder<EntityStore>[] holders = ItemComponent.generateItemDrops(store, drops, pos, Rotation3f.ZERO);
        for (Holder<EntityStore> holder : holders) {
            if (holder != null) {
                cmd.addEntity(holder, AddReason.SPAWN);
            }
        }

        LOGGER.at(java.util.logging.Level.INFO).log(
                "AnchorResourceSystem: spawned %d drop(s) for %s at (%.1f, %.1f, %.1f)",
                drops.size(), roleName, pos.x, pos.y, pos.z);
    }
}