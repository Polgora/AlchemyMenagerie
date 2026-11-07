package me.Polgora;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.chunk.BlockOperations;
import com.hypixel.hytale.server.core.universe.world.chunk.section.BlockSection;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;

/**
 * Single-block read/write helpers against the chunk-section-based block API
 * that replaced {@code World.getBlockType(int,int,int)} and
 * {@code World.breakBlock(int,int,int,int)} in Server 0.7.6.
 *
 * <p>Mirrors the pattern used by the vanilla {@code /block get} and
 * {@code /block set} commands: resolve the chunk section {@link Ref} at the
 * block position, read/write the {@link BlockSection} component on it, and
 * translate between palette index and {@link BlockType} via
 * {@code BlockType.getAssetMap()}.
 *
 * <p>Assumes the chunk is already loaded — true for every caller here, since
 * each is reacting to a player interacting with a block they can see.
 */
public final class WorldBlockUtil {

    private WorldBlockUtil() {}

    /** Returns the {@link BlockType} at the given world block position, or {@code null} if the chunk isn't loaded. */
    public static BlockType getBlockType(World world, int x, int y, int z) {
        ChunkStore chunkStore = world.getChunkStore();
        Ref<ChunkStore> sectionRef = chunkStore.getChunkSectionReferenceAtBlock(x, y, z);
        if (sectionRef == null) return null;

        Store<ChunkStore> store = chunkStore.getStore();
        BlockSection section = store.getComponent(sectionRef, BlockSection.getComponentType());
        if (section == null) return null;

        int blockId = section.get(x, y, z);
        return (BlockType) BlockType.getAssetMap().getAsset(blockId);
    }

    /** Sets the block at the given world block position. No-op if the chunk isn't loaded. */
    public static void setBlockType(World world, int x, int y, int z, BlockType blockType) {
        ChunkStore chunkStore = world.getChunkStore();
        Ref<ChunkStore> sectionRef = chunkStore.getChunkSectionReferenceAtBlock(x, y, z);
        if (sectionRef == null) return;

        int blockId = BlockType.getAssetMap().getIndex(blockType.getId());
        BlockOperations.setBlock(chunkStore, sectionRef, x, y, z, blockId, blockType, 0, 0, 0);
    }

    /** Clears the block at the given world block position to empty/air. No-op if the chunk isn't loaded. */
    public static void breakBlock(World world, int x, int y, int z) {
        setBlockType(world, x, y, z, BlockType.EMPTY);
    }
}
