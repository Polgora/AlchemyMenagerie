package me.Polgora.npc;

import com.hypixel.hytale.component.AddReason;
import com.hypixel.hytale.component.Holder;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.math.vector.Rotation3f;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.modules.entity.item.ItemComponent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.protocol.packets.interface_.NotificationStyle;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.util.NotificationUtil;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import me.Polgora.SpatFoodRegistry;
import me.Polgora.ThrownFoodTracker;
import com.hypixel.hytale.component.Store;
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
import me.Polgora.CreatureNameManager;
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

    /** How long head/body tracking is suspended after eating so it doesn't override the Eat animation. */
    private static final long EATING_HOLD_MS = 4000;

    /** Role flag slot for "Eating"; the role JSON skips head/body tracking while it is set. */
    private final int eatingFlagSlot;

    ActionEatDroppedFood(BuilderActionEatDroppedFood builder, BuilderSupport support) {
        super(builder);
        this.eatingFlagSlot = support.getFlagSlot("Eating");
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

            // Take one; anything left over is handled on the next pass.
            hotbar.removeItemStackFromSlot(slot, 1);

            UUID creatureUuid = npc.getUuid();
            UUID ownerUuid = CreatureRegistry.getOwnerUuid(creatureUuid);
            UUID thrower = ThrownFoodTracker.consume(itemId);
            Vector3d pos = npc.getOldPosition();

            if (ownerUuid == null && thrower != null) {
                // Ownerless creature eating a player's food: that player becomes its owner.
                PlayerRef claimer = Universe.get().getPlayer(thrower);
                if (claimer != null) {
                    CreatureRegistry.registerExternal(creatureUuid, thrower, claimer.getUsername(), roleName);
                    String cname = CreatureNameManager.getName(creatureUuid);
                    if (cname == null) cname = roleName.replace('_', ' ');
                    NotificationUtil.sendNotification(claimer.getPacketHandler(),
                            Message.raw(cname + " has no owner. You are now their owner!"), NotificationStyle.Success);
                    ownerUuid = thrower;
                }
            }

            if (ownerUuid != null) {
                boolean stranger = thrower != null && !thrower.equals(ownerUuid);
                boolean onCooldown = !CreatureFeedManager.canFeed(creatureUuid);
                if (stranger || onCooldown) {
                    // Spit it back out onto the ground (with a pickup delay) instead of wasting it.
                    UUID giveTo = stranger ? thrower : (thrower != null ? thrower : ownerUuid);
                    String msg = stranger
                            ? "This creature belongs to someone else and won't eat your food."
                            : "That creature isn't hungry yet.";
                    refuseFood(store, giveTo, itemId, pos, msg);
                    return false; // refused: stops the role action list so the Eat animation is skipped
                }
                int bondPoints = FoodIngredientValueRegistry.getCount(itemId)
                        * CreatureBondManager.POINTS_PER_INGREDIENT;
                CreatureFeedManager.recordFeed(creatureUuid);
                CreatureBondManager.recordFeed(creatureUuid, bondPoints);
            }

            EatAnimation.play(npc, ref, store);
            execution.getFlagsComponent().setFlag(eatingFlagSlot, true);
            store.getExternalData().getWorld().scheduleAfter(() -> {
                try {
                    var flags = ref.isValid() ? store.getComponent(ref,
                            com.hypixel.hytale.server.npc.role.support.FlagsComponent.getComponentType()) : null;
                    if (flags != null) flags.setFlag(eatingFlagSlot, false);
                } catch (Exception ignored) { }
            }, EATING_HOLD_MS, java.util.concurrent.TimeUnit.MILLISECONDS);
            if (pos != null) {
                ParticleUtil.spawnParticleEffect("Hearts_Subtle",
                        new Vector3d(pos.x, pos.y + 1.0, pos.z), store);
            }
            return true;
        }
        return false;
    }

    /** Drops the refused food at the creature's feet with a pickup delay, and tells the player why. */
    private static void refuseFood(Store<EntityStore> store, UUID notifyUuid, String itemId,
                                   Vector3d pos, String message) {
        if (pos != null) {
            // Toss it 1 block from the creature, toward the player it came from (random if unknown).
            double dx = 0, dz = 0;
            PlayerRef target = notifyUuid == null ? null : Universe.get().getPlayer(notifyUuid);
            var tt = target == null ? null : target.getTransform();
            if (tt != null && tt.getPosition() != null) {
                dx = tt.getPosition().x - pos.x;
                dz = tt.getPosition().z - pos.z;
            }
            double len = Math.sqrt(dx * dx + dz * dz);
            if (len < 0.01) {
                double a = Math.random() * Math.PI * 2;
                dx = Math.cos(a);
                dz = Math.sin(a);
                len = 1;
            }
            Vector3d dropPos = new Vector3d(pos.x + dx / len, pos.y + 0.5, pos.z + dz / len);
            @SuppressWarnings("unchecked")
            Holder<EntityStore>[] holders = ItemComponent.generateItemDrops(store,
                    java.util.List.of(new ItemStack(itemId, 1)), dropPos, Rotation3f.ZERO);
            for (Holder<EntityStore> holder : holders) {
                if (holder == null) continue;
                ItemComponent ic = holder.getComponent(ItemComponent.getComponentType());
                if (ic != null) ic.setPickupDelay(SpatFoodRegistry.LOCK_SECONDS);
                Ref<EntityStore> itemRef = store.addEntity(holder, AddReason.SPAWN);
                if (itemRef != null) {
                    UUIDComponent uc = store.getComponent(itemRef, UUIDComponent.getComponentType());
                    if (uc != null) SpatFoodRegistry.add(uc.getUuid());
                }
            }
        }
        PlayerRef playerRef = notifyUuid == null ? null : Universe.get().getPlayer(notifyUuid);
        if (playerRef != null) playerRef.sendMessage(Message.raw(message));
    }
}
