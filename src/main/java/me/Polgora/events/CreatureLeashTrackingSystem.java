package me.Polgora.events;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.component.system.tick.EntityTickingSystem;
import org.joml.Vector3d;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import com.hypixel.hytale.server.npc.role.Role;
import me.Polgora.AnchorManager;
import me.Polgora.CreatureAnchorRegistry;
import me.Polgora.CreatureBondManager;
import me.Polgora.CreatureDietRegistry;
import me.Polgora.CreatureRegistry;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Adjusts the leash point of Bonded and Devoted creatures toward their owner,
 * restoring it to the anchor position when the owner is unreachable.
 *
 * <p>Tier behaviour:
 * <ul>
 *   <li><b>Bonded</b> — leash moves to owner position every
 *       {@link #BONDED_INTERVAL_MS} (10 s), but only while the owner is within
 *       the creature's assigned Menagerie Anchor radius.
 *   <li><b>Devoted</b> — leash is set to the owner's position every server
 *       tick so the creature actively follows. If the owner moves more than
 *       {@link #DEVOTED_GIVE_UP_RANGE} blocks from the creature's body (e.g.
 *       blocked by a fence), the creature gives up and returns to wandering
 *       the anchor leash point.
 * </ul>
 *
 * <p>When the owner goes offline the leash is immediately restored to the
 * anchor block centre. Creatures with no assigned anchor or no known owner
 * are skipped entirely.
 */
public class CreatureLeashTrackingSystem extends EntityTickingSystem<EntityStore> {

    // ── Timing / range constants ──────────────────────────────────────────────

    private static final long   BONDED_INTERVAL_MS   = 10_000L;

    /**
     * If the owner's body is more than this many blocks from the Devoted
     * creature's body, the creature stops following and reverts to its anchor
     * leash point. Should be large enough to cover normal movement lag between
     * the creature and a walking owner, while still catching teleports/absences.
     */
    private static final double DEVOTED_GIVE_UP_RANGE = 40.0;

    // ── Per-creature state ────────────────────────────────────────────────────

    /** Timestamp of the last leash update for each creature UUID. */
    private final Map<UUID, Long> lastUpdate    = new ConcurrentHashMap<>();

    /** Creatures whose leash is currently tracking the owner (in-radius). */
    private final Set<UUID> currentlyTracking  = ConcurrentHashMap.newKeySet();

    /** Last bond tier this system saw for each creature, to detect tier changes. */
    private final Map<UUID, CreatureBondManager.BondTier> lastTier = new ConcurrentHashMap<>();

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

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

        // Only process known Menagerie creatures
        if (!CreatureDietRegistry.isKnownCreature(npc.getRoleName())) return;

        Ref<EntityStore> ref      = chunk.getReferenceTo(index);
        UUIDComponent    uuidComp = store.getComponent(ref, UUIDComponent.getComponentType());
        if (uuidComp == null) return;
        UUID entityUuid = uuidComp.getUuid();
        if (entityUuid == null) return;

        CreatureBondManager.BondTier tier = CreatureBondManager.getTier(entityUuid);

        // Any tier change (up OR down, via /bondset, feeding, or erosion) gets a full
        // clean-slate reset before the new tier's logic runs.
        CreatureBondManager.BondTier prevTier = lastTier.put(entityUuid, tier);
        if (prevTier != null && prevTier != tier) {
            resetOnTierChange(ref, store, npc, entityUuid, prevTier, tier);
        }

        // Bond must be at least Bonded to track
        if (tier.level < CreatureBondManager.BondTier.BONDED.level) {
            return;
        }

        // Resolve owner
        UUID ownerUuid = CreatureRegistry.getOwnerUuid(entityUuid);
        if (ownerUuid == null) return;

        // Resolve anchor (required for Bonded radius check; Devoted uses it only
        // as a fallback when giving up, so a missing anchor is tolerated there)
        UUID stableId = CreatureRegistry.getStableId(entityUuid);
        AnchorManager.AnchorPos anchor = (stableId != null)
                ? CreatureAnchorRegistry.getAnchorPos(stableId) : null;

        // Resolve world and owner entity ref
        EntityStore entityStore = (EntityStore) store.getExternalData();
        World world = entityStore.getWorld();
        if (world == null) return;

        Ref<EntityStore> ownerRef = world.getEntityRef(ownerUuid);
        if (ownerRef == null) {
            // Owner offline — restore leash to its home (anchor, or original leash point)
            if (currentlyTracking.remove(entityUuid)) {
                restoreHome(entityUuid, npc);
            }
            return;
        }

        // Get owner position
        TransformComponent ownerTransform = store.getComponent(ownerRef, TransformComponent.getComponentType());
        if (ownerTransform == null) return;
        Vector3d ownerPos = ownerTransform.getPosition();
        if (ownerPos == null) return;

        // ── DEVOTED: follow owner via the role's OwnerTarget marked-entity slot ──
        if (tier == CreatureBondManager.BondTier.DEVOTED) {
            TransformComponent creatureTransform =
                    store.getComponent(ref, TransformComponent.getComponentType());
            if (creatureTransform == null) return;
            Vector3d creaturePos = creatureTransform.getPosition();
            if (creaturePos == null) return;

            Role role = npc.getRole();
            if (role == null) return;

            if (distance(creaturePos, ownerPos) > DEVOTED_GIVE_UP_RANGE) {
                // Owner too far — stop following; clear the target and restore leash
                if (currentlyTracking.remove(entityUuid)) {
                    role.setMarkedTarget(ref, store, "OwnerTarget", null);
                    restoreHome(entityUuid, npc);
                }
                return;
            }

            // Within range — register the owner as the role's follow target every tick.
            // The role JSON SensorTarget("OwnerTarget") activates a Seek body motion
            // that pathfinds directly to the owner with StopDistance=3.
            // We also sync the leash point so the Leash/ReturnHome sensor does not interfere.
            currentlyTracking.add(entityUuid);
            role.setMarkedTarget(ref, store, "OwnerTarget", ownerRef);
            npc.setLeashPoint(new Vector3d(ownerPos.x, ownerPos.y, ownerPos.z));
            return;
        }

        // ── BONDED: track owner within anchor radius on a timer ───────────────
        // Always clear the Devoted follow-target here, unconditionally — a direct
        // tier demotion (e.g. via /bondset, or erosion skipping straight past the
        // Devoted give-up-range check) would otherwise leave OwnerTarget stuck
        // active, since neither the above-Devoted nor below-Bonded branches run.
        Role role = npc.getRole();
        if (role != null) role.setMarkedTarget(ref, store, "OwnerTarget", null);

        // Anchor is required for the radius check
        if (anchor == null) return;

        UUID worldUuid = world.getWorldConfig().getUuid();
        boolean inRadius = anchor.contains(worldUuid, ownerPos.x, ownerPos.y, ownerPos.z);

        if (!inRadius) {
            // Owner outside anchor radius — restore leash if we were tracking
            if (currentlyTracking.remove(entityUuid)) {
                restoreLeashToAnchor(npc, anchor);
            }
            return;
        }

        long now  = System.currentTimeMillis();
        Long last = lastUpdate.get(entityUuid);
        if (last != null && now - last < BONDED_INTERVAL_MS) return;
        lastUpdate.put(entityUuid, now);

        // Move leash toward owner
        currentlyTracking.add(entityUuid);
        npc.setLeashPoint(new Vector3d(ownerPos.x, ownerPos.y, ownerPos.z));
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static void restoreLeashToAnchor(NPCEntity npc, AnchorManager.AnchorPos anchor) {
        npc.setLeashPoint(new Vector3d(anchor.x + 0.5, anchor.y, anchor.z + 0.5));
    }

    /**
     * Puts the creature's leash point back to its "home" and returns a short description of
     * where it went (for logging):
     * <ul>
     *   <li><b>anchor</b> — the creature has an assigned Menagerie Anchor: leash goes back to
     *       it (the intended design: creatures return to their anchor).
     *   <li><b>current position</b> — no anchor: leash is set to wherever the creature is
     *       right now. Without this the leash stays at the owner's old position, and
     *       restoring any older point (e.g. its original spawn) would make a creature that
     *       has wandered or followed far away walk all the way back after ~15-20s.
     * </ul>
     */
    private String restoreHome(UUID entityUuid, NPCEntity npc) {
        UUID stableId = CreatureRegistry.getStableId(entityUuid);
        AnchorManager.AnchorPos anchor = (stableId != null)
                ? CreatureAnchorRegistry.getAnchorPos(stableId) : null;
        if (anchor != null) {
            restoreLeashToAnchor(npc, anchor);
            return "its anchor";
        }
        Vector3d here = npc.getOldPosition();
        if (here == null) return "(unchanged: position unknown)";
        npc.setLeashPoint(new Vector3d(here.x, here.y, here.z));
        return "its current position (no anchor assigned)";
    }

    /**
     * Clean-slate reset when a creature's bond tier changes in either direction:
     * drops the Devoted follow target, forgets leash-tracking bookkeeping, and puts the
     * leash point back at home. The new tier's normal logic then re-applies whatever
     * it needs on this same tick. (The Tame/Bonded role slots are handled every tick by
     * {@link CreatureTierSlotSystem}.)
     */
    private void resetOnTierChange(Ref<EntityStore> ref, Store<EntityStore> store, NPCEntity npc,
                                   UUID entityUuid, CreatureBondManager.BondTier from,
                                   CreatureBondManager.BondTier to) {
        Role role = npc.getRole();
        if (role != null) role.setMarkedTarget(ref, store, "OwnerTarget", null);
        currentlyTracking.remove(entityUuid);
        lastUpdate.remove(entityUuid);
        String where = restoreHome(entityUuid, npc);
        LOGGER.at(java.util.logging.Level.INFO).log(
                "Creature %s (%s) bond tier %s -> %s: follow target cleared, leash set to %s.",
                npc.getRoleName(), entityUuid, from.displayName, to.displayName, where);
    }

    /** Euclidean distance between two world positions. */
    private static double distance(Vector3d a, Vector3d b) {
        double dx = a.x - b.x;
        double dy = a.y - b.y;
        double dz = a.z - b.z;
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }
}