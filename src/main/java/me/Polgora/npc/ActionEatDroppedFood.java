package me.Polgora.npc;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.AnimationSlot;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.universe.world.ParticleUtil;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.asset.builder.BuilderSupport;
import com.hypixel.hytale.server.npc.corecomponents.ActionBase;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import com.hypixel.hytale.server.npc.instructions.ExecutionSupport;
import com.hypixel.hytale.server.npc.sensorinfo.InfoProvider;
import me.Polgora.CreatureBondManager;
import me.Polgora.CreatureDietRegistry;
import me.Polgora.CreatureFeedManager;
import me.Polgora.CreatureRegistry;
import me.Polgora.FoodIngredientValueRegistry;
import org.joml.Vector3d;

import java.util.UUID;

/**
 * Runs right after {@code PickUpItem} in a role's thrown-food instruction. Looks for diet-matching
 * food in the NPC's hotbar and eats it: removes one item, plays {@code Eat}, spawns hearts.
 * Only creatures that already have an owner and are off feed cooldown earn bond points (same
 * values as the info-page feed); wild creatures just eat. Uneaten/unmatched items are left alone.
 */
public class ActionEatDroppedFood extends ActionBase {

    ActionEatDroppedFood(BuilderActionEatDroppedFood builder, BuilderSupport support) {
        super(builder);
    }

    @Override
    @SuppressWarnings("removal")
    public boolean execute(Ref<EntityStore> ref, ExecutionSupport execution, InfoProvider sensorInfo,
                           double dt, Store<EntityStore> store) {
        super.execute(ref, execution, sensorInfo, dt, store);

        NPCEntity npc = store.getComponent(ref, NPCEntity.getComponentType());
        if (npc == null) return false;

        String roleName = npc.getRoleName();
        CreatureDietRegistry.DietType diet = CreatureDietRegistry.getDiet(roleName);
        if (diet == null) return false;

        ItemContainer hotbar = npc.getInventory().getHotbar();
        for (short slot = 0; slot < hotbar.getCapacity(); slot++) {
            ItemStack stack = hotbar.getItemStack(slot);
            if (stack == null || stack.isEmpty()) continue;
            String itemId = stack.getItemId();
            if (!CreatureDietRegistry.isAcceptedFood(diet, itemId)) continue;

            // Eat one; anything left over is eaten on the next pass.
            hotbar.removeItemStackFromSlot(slot, 1);

            UUID creatureUuid = npc.getUuid();
            if (CreatureRegistry.getOwnerUuid(creatureUuid) != null && CreatureFeedManager.canFeed(creatureUuid)) {
                int bondPoints = FoodIngredientValueRegistry.getCount(itemId)
                        * CreatureBondManager.POINTS_PER_INGREDIENT;
                CreatureFeedManager.recordFeed(creatureUuid);
                CreatureBondManager.recordFeed(creatureUuid, bondPoints);
            }

            npc.playAnimation(ref, AnimationSlot.Status, "Eat", store);
            Vector3d pos = npc.getOldPosition();
            if (pos != null) {
                ParticleUtil.spawnParticleEffect("Hearts_Subtle",
                        new Vector3d(pos.x, pos.y + 1.0, pos.z), store);
            }
            return true;
        }
        return false;
    }
}
