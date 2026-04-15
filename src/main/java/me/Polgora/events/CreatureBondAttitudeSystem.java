package me.Polgora.events;

import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.system.StoreSystem;
import com.hypixel.hytale.server.core.asset.type.attitude.Attitude;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.blackboard.Blackboard;
import com.hypixel.hytale.server.npc.blackboard.view.attitude.AttitudeView;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import me.Polgora.CreatureBondManager;
import me.Polgora.CreatureDietRegistry;

import java.util.UUID;

/**
 * Registers a world-level attitude provider that overrides the NPC attitude
 * toward a player to {@link Attitude#FRIENDLY} when both of the following are
 * true:
 * <ul>
 *   <li>The NPC is a Menagerie creature tracked by {@link CreatureDietRegistry}.
 *   <li>The player is the creature's owner AND the bond tier is at least
 *       {@link CreatureBondManager.BondTier#TAME}.
 * </ul>
 *
 * <p>This prevents Timid creatures from fleeing their owner once the bond has
 * progressed past Wild. The provider is registered at priority 100 — after
 * entity-specific overrides (priority 0) but before the NPC role's general
 * attitude group (priority 200), following the same pattern as the built-in
 * {@code ReputationAttitudeSystem}.
 *
 * <p>Register via:
 * {@code getEntityStoreRegistry().registerSystem(new CreatureBondAttitudeSystem())}
 */
public class CreatureBondAttitudeSystem extends StoreSystem<EntityStore> {

    private static final int PROVIDER_PRIORITY = 100;

    @Override
    public void onSystemAddedToStore(Store<EntityStore> store) {
        Blackboard blackboard = store.getResource(Blackboard.getResourceType());
        if (blackboard == null) return;
        AttitudeView attitudeView = (AttitudeView) blackboard.getView(AttitudeView.class, 0L);
        if (attitudeView == null) return;

        attitudeView.registerProvider(PROVIDER_PRIORITY,
                (npcRef, role, targetRef, accessor) -> {

                    // Target must be a player
                    Player player = accessor.getComponent(targetRef, Player.getComponentType());
                    if (player == null) return null;

                    // NPC must be a tracked Menagerie creature
                    NPCEntity npc = accessor.getComponent(npcRef, NPCEntity.getComponentType());
                    if (npc == null) return null;
                    if (!CreatureDietRegistry.isKnownCreature(npc.getRoleName())) return null;

                    // Resolve creature entity UUID
                    UUIDComponent uuidComp = accessor.getComponent(npcRef, UUIDComponent.getComponentType());
                    if (uuidComp == null) return null;
                    UUID creatureUuid = uuidComp.getUuid();
                    if (creatureUuid == null) return null;

                    // Bond must be at least Tame for any player to be treated as Friendly.
                    // This prevents all flee-from-player behaviour at Tame/Bonded/Devoted.
                    CreatureBondManager.BondTier tier = CreatureBondManager.getTier(creatureUuid);
                    if (tier == CreatureBondManager.BondTier.WILD) return null;

                    return Attitude.FRIENDLY;
                });
    }

    @Override
    public void onSystemRemovedFromStore(Store<EntityStore> store) {
        // Providers registered on AttitudeView have no removal API; the view is
        // discarded when the world is torn down.
    }
}