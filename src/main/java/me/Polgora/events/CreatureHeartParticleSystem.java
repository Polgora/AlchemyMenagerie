package me.Polgora.events;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.tick.EntityTickingSystem;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.world.ParticleUtil;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import me.Polgora.CreatureBondManager;
import me.Polgora.CreatureDietRegistry;
import me.Polgora.CreatureRegistry;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Periodically spawns heart particles above Bonded and Devoted Menagerie
 * creatures when their owner is nearby.
 *
 * <p>Tier behaviour:
 * <ul>
 *   <li><b>Bonded</b> — fires {@code Hearts_Subtle} (one heart) once every
 *       {@link #BONDED_INTERVAL_MS} ms (1 min).
 *   <li><b>Devoted</b> — fires {@code Hearts} (a streaming 3-second burst of
 *       multiple hearts) once every {@link #DEVOTED_INTERVAL_MS} ms (30 s).
 * </ul>
 *
 * <p>Hearts are only spawned when the owner's body is within
 * {@link #PROXIMITY_RANGE} blocks of the creature. Hearts are visible to all
 * nearby players, not just the owner.
 *
 * <p>Register via:
 * {@code getEntityStoreRegistry().registerSystem(new CreatureHeartParticleSystem())}
 */
public class CreatureHeartParticleSystem extends EntityTickingSystem<EntityStore> {

    // ── Particle system names (base filenames, no path or extension) ──────────

    /** Single-heart burst — used for Bonded creatures. */
    private static final String PARTICLE_BONDED  = "Hearts_Subtle";

    /** Multi-heart 3-second stream — used for Devoted creatures. */
    private static final String PARTICLE_DEVOTED = "Hearts";

    // ── Timing constants ──────────────────────────────────────────────────────

    private static final long BONDED_INTERVAL_MS  = 60_000L; // 1 minute
    private static final long DEVOTED_INTERVAL_MS = 30_000L; // 30 seconds

    // ── Range constant ────────────────────────────────────────────────────────

    /**
     * Owner must be within this many blocks of the creature for hearts to
     * appear. Keeps the effect tied to close interaction.
     */
    private static final double PROXIMITY_RANGE = 10.0;

    // ── Per-creature state ────────────────────────────────────────────────────

    /** Epoch-ms timestamp at which each creature may next emit hearts. */
    private final Map<UUID, Long> nextSpawnAt = new ConcurrentHashMap<>();

    // ── ECS query ─────────────────────────────────────────────────────────────

    @Override
    public Query<EntityStore> getQuery() {
        return Query.any();
    }

    // ── Tick ──────────────────────────────────────────────────────────────────

    @Override
    public void tick(float dt, int index, ArchetypeChunk<EntityStore> chunk,
                     Store<EntityStore> store, CommandBuffer<EntityStore> cmd) {

        NPCEntity npc = chunk.getComponent(index, NPCEntity.getComponentType());
        if (npc == null) return;

        if (!CreatureDietRegistry.isKnownCreature(npc.getRoleName())) return;

        Ref<EntityStore> ref      = chunk.getReferenceTo(index);
        UUIDComponent    uuidComp = store.getComponent(ref, UUIDComponent.getComponentType());
        if (uuidComp == null) return;
        UUID entityUuid = uuidComp.getUuid();
        if (entityUuid == null) return;

        // Bond must be at least Bonded
        CreatureBondManager.BondTier tier = CreatureBondManager.getTier(entityUuid);
        if (tier.level < CreatureBondManager.BondTier.BONDED.level) return;

        // Owner must be known and online
        UUID ownerUuid = CreatureRegistry.getOwnerUuid(entityUuid);
        if (ownerUuid == null) return;

        EntityStore entityStore = (EntityStore) store.getExternalData();
        World world = entityStore.getWorld();
        if (world == null) return;

        Ref<EntityStore> ownerRef = world.getEntityRef(ownerUuid);
        if (ownerRef == null) return;

        // Proximity check — owner must be within range
        Vector3d creaturePos = npc.getOldPosition();
        if (creaturePos == null) return;

        TransformComponent ownerTransform = store.getComponent(ownerRef, TransformComponent.getComponentType());
        if (ownerTransform == null) return;
        Vector3d ownerPos = ownerTransform.getPosition();
        if (ownerPos == null) return;

        if (distance(creaturePos, ownerPos) > PROXIMITY_RANGE) return;

        // Time gate
        long now  = System.currentTimeMillis();
        Long next = nextSpawnAt.get(entityUuid);
        if (next != null && now < next) return;

        // Schedule next spawn with a tier-appropriate fixed interval
        long interval = (tier == CreatureBondManager.BondTier.DEVOTED) ? DEVOTED_INTERVAL_MS : BONDED_INTERVAL_MS;
        nextSpawnAt.put(entityUuid, now + interval);

        // Spawn hearts 1 block above the creature's body, matching the role's offset
        String particleName = (tier == CreatureBondManager.BondTier.DEVOTED)
                ? PARTICLE_DEVOTED : PARTICLE_BONDED;

        ParticleUtil.spawnParticleEffect(particleName,
                new Vector3d(creaturePos.x, creaturePos.y + 1.0, creaturePos.z),
                store);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static double distance(Vector3d a, Vector3d b) {
        double dx = a.x - b.x;
        double dy = a.y - b.y;
        double dz = a.z - b.z;
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }
}