package me.Polgora.events;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.tick.EntityTickingSystem;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import com.hypixel.hytale.server.npc.role.support.StateSupport;
import me.Polgora.CreatureBondManager;
import me.Polgora.CreatureDietRegistry;
import org.joml.Vector3d;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * DEBUG aid: logs every NPC state-machine change (Idle / Alerted / Flee / Panic / ...) of
 * Menagerie creatures, with the creature's bond tier and the distance to the nearest
 * player. Lets us see exactly which transition precedes a behavior (e.g. "why did it
 * flee?") instead of guessing from role JSON.
 *
 * <p>State changes are rare (a handful per encounter), so INFO level is not noisy. Set
 * {@link #ENABLED} to false (or delete the registration in {@code Main}) once no longer
 * needed.
 */
public class CreatureStateDebugSystem extends EntityTickingSystem<EntityStore> {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    public static volatile boolean ENABLED = true;

    /** Last (stateIndex, subStateIndex) key seen per creature. */
    private final Map<UUID, Long> lastKey = new ConcurrentHashMap<>();

    @Override
    public Query<EntityStore> getQuery() {
        return Query.any();
    }

    @Override
    public void tick(float dt, int index, ArchetypeChunk<EntityStore> chunk,
                     Store<EntityStore> store, CommandBuffer<EntityStore> cmd) {
        if (!ENABLED) return;

        NPCEntity npc = chunk.getComponent(index, NPCEntity.getComponentType());
        if (npc == null) return;
        if (!CreatureDietRegistry.isKnownCreature(npc.getRoleName())) return;

        Ref<EntityStore> ref = chunk.getReferenceTo(index);
        UUIDComponent uuidComp = store.getComponent(ref, UUIDComponent.getComponentType());
        if (uuidComp == null || uuidComp.getUuid() == null) return;
        UUID id = uuidComp.getUuid();

        StateSupport ss = StateSupport.get(ref, store);
        if (ss == null) return;

        logLookInfo(npc, ref, store, id);

        long key = ((long) ss.getStateIndex() << 32) | (ss.getSubStateIndex() & 0xffffffffL);
        Long prev = lastKey.put(id, key);
        if (prev != null && prev == key) return;

        String stateName = ss.getStateName();
        String nearest = nearestPlayerInfo(store, ref);
        LOGGER.at(java.util.logging.Level.INFO).log(
                "[state] %s (%s) tier=%s %s state=%s | %s",
                npc.getRoleName(), id, CreatureBondManager.getTier(id).displayName,
                prev == null ? "initial" : "now", stateName, nearest);
    }

    /** Last time (ms) a [look] line was logged per creature. */
    private final Map<UUID, Long> lastLookLog = new ConcurrentHashMap<>();

    /**
     * Diagnoses "the creature doesn't watch its owner": for Tame/Bonded creatures with their
     * owner within 12 blocks, logs (at most every 2s) the head and body yaw, the bearing to
     * the owner in both common yaw conventions, and which owner slots are filled in the
     * role. If the head yaw tracks the bearing, head tracking works and only the body is not
     * turning; if it does not, the slot/sensor isn't driving the head.
     */
    private void logLookInfo(NPCEntity npc, Ref<EntityStore> ref, Store<EntityStore> store, UUID id) {
        CreatureBondManager.BondTier tier = CreatureBondManager.getTier(id);
        if (tier != CreatureBondManager.BondTier.TAME && tier != CreatureBondManager.BondTier.BONDED) return;

        long now = System.currentTimeMillis();
        Long last = lastLookLog.get(id);
        if (last != null && now - last < 2000L) return;

        UUID ownerUuid = me.Polgora.CreatureRegistry.getOwnerUuid(id);
        World world = ((EntityStore) store.getExternalData()).getWorld();
        if (ownerUuid == null || world == null) return;
        Ref<EntityStore> ownerRef = world.getEntityRef(ownerUuid);
        if (ownerRef == null) return;

        TransformComponent ct = store.getComponent(ref, TransformComponent.getComponentType());
        TransformComponent ot = store.getComponent(ownerRef, TransformComponent.getComponentType());
        com.hypixel.hytale.server.core.modules.entity.component.HeadRotation hr =
                store.getComponent(ref, com.hypixel.hytale.server.core.modules.entity.component.HeadRotation.getComponentType());
        if (ct == null || ot == null || hr == null || ct.getPosition() == null || ot.getPosition() == null) return;

        double dx = ot.getPosition().x - ct.getPosition().x;
        double dz = ot.getPosition().z - ct.getPosition().z;
        double dist = Math.sqrt(dx * dx + dz * dz);
        // Only inside the role's head-watch range (Tame 8, Bonded 10) — beyond it no tracking is expected.
        double watchRange = (tier == CreatureBondManager.BondTier.TAME) ? 8.0 : 10.0;
        if (dist > watchRange) return;
        double bearingA = Math.toDegrees(Math.atan2(dx, dz));    // yaw 0 = +Z (not the game's convention)
        double bearingB = Math.toDegrees(Math.atan2(-dx, -dz));  // yaw 0 = -Z (confirmed the game's convention)
        double headDeg = Math.toDegrees(hr.getRotation().yaw());
        double bodyDeg = Math.toDegrees(ct.getRotation().yaw());

        // Only log when the creature is visibly NOT facing the owner (body or head >15 deg off).
        double bodyErr = Math.abs(angleDiff(bodyDeg, bearingB));
        double headErr = Math.abs(angleDiff(headDeg, bearingB));
        if (bodyErr <= 15.0 && headErr <= 15.0) return;
        lastLookLog.put(id, now);

        com.hypixel.hytale.server.npc.role.support.MarkedEntitySupport mes =
                com.hypixel.hytale.server.npc.role.support.MarkedEntitySupport.get(ref, store);
        String slots = mes == null ? "no-marked-support"
                : "Tame=" + mes.hasMarkedEntityInSlot(CreatureTierSlotSystem.SLOT_TAME)
                + " Bonded=" + mes.hasMarkedEntityInSlot(CreatureTierSlotSystem.SLOT_BONDED)
                + " BondedStill=" + mes.hasMarkedEntityInSlot(CreatureTierSlotSystem.SLOT_BONDED_STILL);

        StateSupport ss = StateSupport.get(ref, store);
        LOGGER.at(java.util.logging.Level.INFO).log(
                "[look] %s tier=%s state=%s dist=%.1f | head=%.0f body=%.0f bearing=%.0f | headErr=%.0f bodyErr=%.0f | slots: %s",
                npc.getRoleName(), tier.displayName, ss == null ? "?" : ss.getStateName(),
                dist, headDeg, bodyDeg, bearingB, headErr, bodyErr, slots);
    }

    /** Smallest signed difference between two angles in degrees, in (-180, 180]. */
    private static double angleDiff(double a, double b) {
        double d = (a - b) % 360.0;
        if (d > 180.0) d -= 360.0;
        if (d <= -180.0) d += 360.0;
        return d;
    }

    private static String nearestPlayerInfo(Store<EntityStore> store, Ref<EntityStore> creatureRef) {
        TransformComponent ct = store.getComponent(creatureRef, TransformComponent.getComponentType());
        World world = ((EntityStore) store.getExternalData()).getWorld();
        if (ct == null || ct.getPosition() == null || world == null) return "nearest player: unknown";

        Vector3d cp = ct.getPosition();
        double best = Double.MAX_VALUE;
        String bestName = null;
        for (PlayerRef pr : world.getPlayerRefs()) {
            Ref<EntityStore> pref = pr.getReference();
            if (pref == null) continue;
            TransformComponent pt = store.getComponent(pref, TransformComponent.getComponentType());
            if (pt == null || pt.getPosition() == null) continue;
            Vector3d pp = pt.getPosition();
            double dx = pp.x - cp.x, dy = pp.y - cp.y, dz = pp.z - cp.z;
            double d = Math.sqrt(dx * dx + dy * dy + dz * dz);
            if (d < best) { best = d; bestName = pr.getUsername(); }
        }
        return bestName == null
                ? "nearest player: none"
                : String.format("nearest player: %s at %.1f blocks", bestName, best);
    }
}
