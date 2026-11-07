package me.Polgora.npc;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.modules.i18n.I18nModule;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.asset.builder.BuilderSupport;
import com.hypixel.hytale.server.npc.corecomponents.ActionBase;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import com.hypixel.hytale.server.npc.instructions.ExecutionSupport;
import com.hypixel.hytale.server.npc.sensorinfo.InfoProvider;
import me.Polgora.CreatureDietRegistry;
import me.Polgora.pages.CreatureInfoPage;

import java.util.UUID;

/**
 * NPC instruction action that opens {@link CreatureInfoPage} for the player
 * who triggered the {@code HasInteracted} sensor on a Menagerie creature.
 *
 * <p>Registered in {@code Main.setup()} under the name {@code "OpenCreatureInfo"}
 * and referenced in {@code Verdant_Sprout.json}'s {@code InteractionInstruction}.
 */
public class ActionOpenCreatureInfo extends ActionBase {

    ActionOpenCreatureInfo(BuilderActionOpenCreatureInfo builder, BuilderSupport support) {
        super(builder);
    }

    @Override
    public boolean execute(Ref<EntityStore> ref, ExecutionSupport execution, InfoProvider sensorInfo,
                           double dt, Store<EntityStore> store) {
        super.execute(ref, execution, sensorInfo, dt, store);

        // The interaction iteration target is the player who right-clicked the NPC.
        Ref<EntityStore> playerEntityRef = execution.getStateSupport().getInteractionIterationTarget();
        if (playerEntityRef == null) return false;

        PlayerRef playerRef = store.getComponent(playerEntityRef, PlayerRef.getComponentType());
        if (playerRef == null) return false;

        Player player = store.getComponent(playerEntityRef, Player.getComponentType());
        if (player == null) return false;

        // Don't open a second page if one is already showing.
        if (player.getPageManager().getCustomPage() != null) return false;

        NPCEntity npc = store.getComponent(ref, NPCEntity.getComponentType());
        if (npc == null) return false;

        String roleName = npc.getRoleName();
        if (!CreatureDietRegistry.isKnownCreature(roleName)) return false;

        @SuppressWarnings("removal")
        UUID   creatureUuid = npc.getUuid();
        String displayName  = resolveDisplayName(roleName);

        player.getPageManager().openCustomPage(ref, store,
                new CreatureInfoPage(playerRef, creatureUuid, roleName, displayName));
        return true;
    }

    private static String resolveDisplayName(String roleName) {
        String key  = "server.npcRoles." + roleName + ".name";
        String name = I18nModule.get().getMessage(I18nModule.DEFAULT_LANGUAGE, key);
        return (name != null && !name.isBlank()) ? name : roleName.replace("_", " ");
    }
}