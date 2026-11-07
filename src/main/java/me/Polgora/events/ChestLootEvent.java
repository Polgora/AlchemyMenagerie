package me.Polgora.events;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.EntityEventSystem;
import com.hypixel.hytale.logger.HytaleLogger;
import org.joml.Vector3i;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.player.windows.ContainerBlockWindow;
import com.hypixel.hytale.server.core.entity.entities.player.windows.ItemContainerWindow;
import com.hypixel.hytale.server.core.event.events.ecs.BreakBlockEvent;
import com.hypixel.hytale.server.core.event.events.ecs.PlaceBlockEvent;
import com.hypixel.hytale.server.core.event.events.ecs.UseBlockEvent;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.modules.block.components.ItemContainerBlock;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import me.Polgora.ChestLootManager;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Level;

/**
 * Enriches world-spawned container blocks (chests, etc.) with AlchemyMenagerie
 * loot the first time a player opens them. Player-placed containers are never touched.
 *
 * <p>Flow:
 * <ol>
 *   <li>{@link PlacementTracker} — listens to {@link PlaceBlockEvent}. After the block
 *       is placed, checks if it is a container type via its {@code BlockType} state data.
 *       If so, marks the position as player-placed so it is excluded from enrichment.</li>
 *   <li>{@link BreakTracker} — listens to {@link BreakBlockEvent}. When a container
 *       block is broken, removes the position from both tracked sets so the slot is
 *       clean if a new chest is later placed or pasted there.</li>
 *   <li>{@link EnrichmentSystem} — listens to {@link UseBlockEvent.Post}. After a
 *       player successfully opens a container, enriches it if eligible by accessing
 *       the open {@link ContainerBlockWindow} from the player's window manager.</li>
 * </ol>
 *
 * <p>All three systems are registered via
 * {@code plugin.getEntityStoreRegistry().registerSystem(...)}.
 */
public class ChestLootEvent {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    // -------------------------------------------------------------------------
    // Shared helper
    // -------------------------------------------------------------------------

    /** Returns true if the given BlockType is a container (chest, etc.). */
    private static boolean isContainerBlock(BlockType blockType) {
        if (blockType == null || blockType.isUnknown()) return false;
        var holder = blockType.getBlockEntity();
        return holder != null && holder.getComponent(ItemContainerBlock.getComponentType()) != null;
    }

    // -------------------------------------------------------------------------
    // PlacementTracker — marks player-placed containers
    // -------------------------------------------------------------------------

    public static class PlacementTracker extends EntityEventSystem<EntityStore, PlaceBlockEvent> {

        public PlacementTracker() {
            super(PlaceBlockEvent.class);
        }

        @Override
        public Query<EntityStore> getQuery() {
            return Query.any();
        }

        @Override
        public void handle(int i, ArchetypeChunk<EntityStore> chunk, Store<EntityStore> store,
                           CommandBuffer<EntityStore> cmd, PlaceBlockEvent event) {
            Ref<EntityStore> ref = chunk.getReferenceTo(i);
            Player player = store.getComponent(ref, Player.getComponentType());
            if (player == null) return;

            Vector3i pos = event.getTargetBlock();
            World world = player.getWorld();
            String worldName = world.getName();
            int x = pos.x, y = pos.y, z = pos.z;

            // PlaceBlockEvent fires before the block is placed; defer the check so the
            // block type is readable from the world when the callback runs.
            world.execute(() -> {
                BlockType placedType = me.Polgora.WorldBlockUtil.getBlockType(world, x, y, z);
                if (isContainerBlock(placedType)) {
                    ChestLootManager.markPlayerPlaced(worldName, x, y, z);
                    LOGGER.at(Level.INFO).log(
                            "ChestLootEvent: marked player-placed container at %s [%d, %d, %d].",
                            worldName, x, y, z);
                }
            });
        }
    }

    // -------------------------------------------------------------------------
    // BreakTracker — cleans up tracked positions when a container is broken
    // -------------------------------------------------------------------------

    public static class BreakTracker extends EntityEventSystem<EntityStore, BreakBlockEvent> {

        public BreakTracker() {
            super(BreakBlockEvent.class);
        }

        @Override
        public Query<EntityStore> getQuery() {
            return Query.any();
        }

        @Override
        public void handle(int i, ArchetypeChunk<EntityStore> chunk, Store<EntityStore> store,
                           CommandBuffer<EntityStore> cmd, BreakBlockEvent event) {
            if (!isContainerBlock(event.getBlockType())) return;

            Ref<EntityStore> ref = chunk.getReferenceTo(i);
            Player player = store.getComponent(ref, Player.getComponentType());
            if (player == null) return;

            Vector3i pos = event.getTargetBlock();
            String worldName = player.getWorld().getName();

            ChestLootManager.unmarkPlayerPlaced(worldName, pos.x, pos.y, pos.z);
            ChestLootManager.unmarkEnriched(worldName, pos.x, pos.y, pos.z);
        }
    }

    // -------------------------------------------------------------------------
    // EnrichmentSystem — injects loot into world-spawned containers on first open
    // -------------------------------------------------------------------------

    public static class EnrichmentSystem extends EntityEventSystem<EntityStore, UseBlockEvent.Post> {

        public EnrichmentSystem() {
            super(UseBlockEvent.Post.class);
        }

        @Override
        public Query<EntityStore> getQuery() {
            return Query.any();
        }

        @Override
        public void handle(int i, ArchetypeChunk<EntityStore> chunk, Store<EntityStore> store,
                           CommandBuffer<EntityStore> cmd, UseBlockEvent.Post event) {
            // Only act on container blocks
            if (!isContainerBlock(event.getBlockType())) return;

            Ref<EntityStore> ref = chunk.getReferenceTo(i);
            Player player = store.getComponent(ref, Player.getComponentType());
            if (player == null) return;

            Vector3i pos = event.getTargetBlock();
            World world = player.getWorld();
            String worldName = world.getName();
            int x = pos.x, y = pos.y, z = pos.z;

            String blockTypeId = event.getBlockType().getId();
            LOGGER.at(Level.INFO).log("ChestLootEvent: UseBlockEvent.Post fired at %s [%d, %d, %d] blockType=%s.",
                    worldName, x, y, z, blockTypeId);

            if (ChestLootManager.isEnriched(worldName, x, y, z)) {
                LOGGER.at(Level.INFO).log("ChestLootEvent: already enriched, skipping.");
                return;
            }
            if (ChestLootManager.isPlayerPlaced(worldName, x, y, z)) {
                LOGGER.at(Level.INFO).log("ChestLootEvent: player-placed, skipping.");
                return;
            }

            // Claim enrichment so this chest is never retried, regardless of per-item rolls.
            if (!ChestLootManager.claimEnrichment(worldName, x, y, z)) {
                LOGGER.at(Level.INFO).log("ChestLootEvent: enrichment claimed by another thread, skipping.");
                return;
            }

            // UseBlockEvent.Post fires before the ContainerBlockWindow is registered in the
            // player's window manager, so defer the container lookup to the next world tick.
            world.execute(() -> {
                ItemContainer container = findOpenChestContainer(player);
                if (container == null) {
                    LOGGER.at(Level.INFO).log("ChestLootEvent: deferred — no open ContainerBlockWindow found, skipping.");
                    return;
                }

                // Collect empty slots; each loot entry rolls independently.
                List<Short> emptySlots = new ArrayList<>();
                short capacity = container.getCapacity();
                for (short s = 0; s < capacity; s++) {
                    ItemStack existing = container.getItemStack(s);
                    if (existing == null || existing.isEmpty()) emptySlots.add(s);
                }
                var rng = ThreadLocalRandom.current();
                int added = 0;
                for (ChestLootManager.LootEntry entry : ChestLootManager.LOOT_ENTRIES) {
                    if (emptySlots.isEmpty()) break;
                    int chance = entry.chanceFor().applyAsInt(blockTypeId);
                    if (rng.nextInt(100) < chance) {
                        int idx = rng.nextInt(emptySlots.size());
                        container.setItemStackForSlot(emptySlots.remove(idx), entry.item());
                        added++;
                    }
                }

                LOGGER.at(Level.INFO).log("ChestLootEvent: enriched container at %s [%d, %d, %d] — %d item(s) added.",
                        worldName, x, y, z, added);
            });
        }

        /** Finds the first open {@link ContainerBlockWindow} in the player's window manager. */
        private static ItemContainer findOpenChestContainer(Player player) {
            for (var window : player.getWindowManager().getWindows()) {
                if (window instanceof ContainerBlockWindow cbw) {
                    return cbw.getItemContainer();
                }
            }
            return null;
        }
    }
}