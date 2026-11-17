package me.Polgora.npc;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.asset.builder.BuilderSupport;
import com.hypixel.hytale.server.npc.corecomponents.ActionBase;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import com.hypixel.hytale.server.npc.instructions.ExecutionSupport;
import com.hypixel.hytale.server.npc.sensorinfo.InfoProvider;
import me.Polgora.CreatureFeedManager;
import me.Polgora.CreatureRegistry;

import java.util.UUID;

/** Keeps the {@code NotHungry} role flag in step with the owner's feed cooldown. */
public class ActionUpdateHungerFlag extends ActionBase {

    private final int notHungryFlagSlot;

    ActionUpdateHungerFlag(BuilderActionUpdateHungerFlag builder, BuilderSupport support) {
        super(builder);
        this.notHungryFlagSlot = support.getFlagSlot("NotHungry");
    }

    @Override
    public boolean execute(Ref<EntityStore> ref, ExecutionSupport execution, InfoProvider sensorInfo,
                           double dt, Store<EntityStore> store) {
        super.execute(ref, execution, sensorInfo, dt, store);
        NPCEntity npc = store.getComponent(ref, NPCEntity.getComponentType());
        if (npc == null) return true;
        UUID id = npc.getUuid();
        boolean notHungry = CreatureRegistry.getOwnerUuid(id) != null && !CreatureFeedManager.canFeed(id);
        execution.getFlagsComponent().setFlag(notHungryFlagSlot, notHungry);
        return true;
    }
}
