package me.Polgora.events;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.tick.EntityTickingSystem;
import org.joml.Vector3d;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import com.hypixel.hytale.server.npc.role.Role;
import me.Polgora.CreatureBondManager;
import me.Polgora.CreatureDietRegistry;
import me.Polgora.CreatureRegistry;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Publishes the owner's bond tier to the NPC role JSON through marked-target
 * slots, so role state machines can branch per tier (the JSON cannot read the
 * bond tier directly).
 *
 * <ul>
 *   <li>{@value #SLOT_TAME}   — set to the owner while the creature is Tame.
 *   <li>{@value #SLOT_BONDED} — set to the owner while the creature is Bonded.
 *   <li>{@value #SLOT_BONDED_STILL} — set alongside {@value #SLOT_BONDED} only once the owner
 *       has stood still for about 1.5 s (lets role JSON gate behavior on a stationary owner).
 *   <li>Devoted uses the {@code OwnerTarget} slot, which is owned by
 *       {@link CreatureLeashTrackingSystem} (not touched here).
 * </ul>
 *
 * <p>At most one of the two slots managed here is filled at a time. A slot only
 * exists if the species' role JSON references it from a {@code Target} sensor;
 * {@code MarkedEntitySupport} silently ignores unknown slot names, so species
 * whose JSON does not use these slots are unaffected.
 *
 * <p>The owner is published whenever they are online in the same world; the
 * role JSON's {@code Target} sensor {@code Range} decides how close counts.
 */
public class CreatureTierSlotSystem extends EntityTickingSystem<EntityStore> {

    public static final String SLOT_TAME   = "TameOwner";
    public static final String SLOT_BONDED = "BondedOwner";
    /** Filled (in addition to {@value #SLOT_BONDED}) only while the Bonded creature's owner is standing still. */
    public static final String SLOT_BONDED_STILL = "BondedOwnerStill";

    @Override
    public Query<EntityStore> getQuery() {
        return Query.any();
    }

    @Override
    public void tick(float dt, int index, ArchetypeChunk<EntityStore> chunk,
                     Store<EntityStore> store, CommandBuffer<EntityStore> cmd) {

        NPCEntity npc = chunk.getComponent(index, NPCEntity.getComponentType());
        if (npc == null) return;
        if (!CreatureDietRegistry.isKnownCreature(npc.getRoleName())) return;

        Role role = npc.getRole();
        if (role == null) return;

        Ref<EntityStore> ref      = chunk.getReferenceTo(index);
        UUIDComponent    uuidComp = store.getComponent(ref, UUIDComponent.getComponentType());
        if (uuidComp == null) return;
        UUID entityUuid = uuidComp.getUuid();
        if (entityUuid == null) return;

        CreatureBondManager.BondTier tier = CreatureBondManager.getTier(entityUuid);

        Ref<EntityStore> ownerRef = null;
        if (tier == CreatureBondManager.BondTier.TAME || tier == CreatureBondManager.BondTier.BONDED) {
            UUID ownerUuid = CreatureRegistry.getOwnerUuid(entityUuid);
            World world = ((EntityStore) store.getExternalData()).getWorld();
            if (ownerUuid != null && world != null) {
                ownerRef = world.getEntityRef(ownerUuid);
            }
        }

        boolean tame   = tier == CreatureBondManager.BondTier.TAME   && ownerRef != null;
        boolean bonded = tier == CreatureBondManager.BondTier.BONDED && ownerRef != null;

        boolean bondedStill = false;
        if (bonded) {
            bondedStill = isOwnerStationary(store, ownerRef, CreatureRegistry.getOwnerUuid(entityUuid));
        }

        // A null ref clears the slot (MarkedEntitySupport.setMarkedEntity null-checks).
        role.setMarkedTarget(ref, store, SLOT_TAME,         tame        ? ownerRef : null);
        role.setMarkedTarget(ref, store, SLOT_BONDED,       bonded      ? ownerRef : null);
        role.setMarkedTarget(ref, store, SLOT_BONDED_STILL, bondedStill ? ownerRef : null);
    }

    // ── Owner stationary tracking ─────────────────────────────────────────────

    /** Owner counts as stationary after not moving more than {@link #MOVE_EPSILON} for this long. */
    private static final long   STILL_AFTER_MS = 1_500L;
    /** Per-tick displacement (blocks) below which the owner is considered not to have moved. */
    private static final double MOVE_EPSILON   = 0.02;

    private static final class OwnerMotion {
        volatile double x, y, z;
        volatile long   lastMovedAt;
    }

    /** Last seen position and last-moved timestamp per owner. Shared by all their creatures. */
    private final Map<UUID, OwnerMotion> ownerMotion = new ConcurrentHashMap<>();

    /**
     * Returns true if the owner has not moved for {@link #STILL_AFTER_MS}. Several creatures
     * share one owner, so the first to tick each frame records any movement as a timestamp and
     * the others simply read it — a late creature seeing "no displacement" cannot erase it.
     */
    private boolean isOwnerStationary(Store<EntityStore> store, Ref<EntityStore> ownerRef, UUID ownerUuid) {
        if (ownerUuid == null) return false;
        TransformComponent tc = store.getComponent(ownerRef, TransformComponent.getComponentType());
        if (tc == null) return false;
        Vector3d pos = tc.getPosition();
        if (pos == null) return false;

        long now = System.currentTimeMillis();
        OwnerMotion m = ownerMotion.get(ownerUuid);
        if (m == null) {
            m = new OwnerMotion();
            m.x = pos.x; m.y = pos.y; m.z = pos.z;
            m.lastMovedAt = now;
            ownerMotion.put(ownerUuid, m);
            return false;
        }

        double dx = pos.x - m.x, dy = pos.y - m.y, dz = pos.z - m.z;
        if (dx * dx + dy * dy + dz * dz > MOVE_EPSILON * MOVE_EPSILON) {
            m.x = pos.x; m.y = pos.y; m.z = pos.z;
            m.lastMovedAt = now;
            return false;
        }
        return now - m.lastMovedAt >= STILL_AFTER_MS;
    }
}
