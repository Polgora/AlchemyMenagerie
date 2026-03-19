package me.Polgora;

import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.inventory.ItemStack;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * Tracks which world-spawned chest positions have been enriched with
 * AlchemyMenagerie loot, and which positions were placed by players (excluded).
 * Both sets are persisted to disk so enrichment survives server restarts.
 */
public class ChestLootManager {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    /**
     * Items injected into eligible world-spawned chests the first time they are opened.
     * Edit this list to control what the mod adds.
     */
    public static final List<ItemStack> LOOT_ITEMS = List.of(
            new ItemStack("Alchemy_Flask", 1)
            // Add more items here as the mod grows
    );

    /** Positions of chests placed by players — never enriched. Key format: worldName:x:y:z */
    private static final Set<String> playerPlacedChests = ConcurrentHashMap.newKeySet();

    /** Positions of world-spawned chests that have already been enriched. */
    private static final Set<String> enrichedChests = ConcurrentHashMap.newKeySet();

    private static Path playerChestsFile;
    private static Path enrichedChestsFile;

    public static void init(Path dataDir) {
        playerChestsFile = dataDir.resolve("player_chests.txt");
        enrichedChestsFile = dataDir.resolve("enriched_chests.txt");
        try {
            Files.createDirectories(dataDir);
        } catch (IOException e) {
            LOGGER.at(Level.SEVERE).withCause(e).log("Failed to create chest loot data directory: %s", dataDir);
        }
        loadSet(playerChestsFile, playerPlacedChests);
        loadSet(enrichedChestsFile, enrichedChests);
        LOGGER.at(Level.INFO).log("ChestLootManager: %d player-placed, %d enriched chest positions loaded.",
                playerPlacedChests.size(), enrichedChests.size());
    }

    static String posKey(String worldName, int x, int y, int z) {
        return worldName + ":" + x + ":" + y + ":" + z;
    }

    public static boolean isPlayerPlaced(String worldName, int x, int y, int z) {
        return playerPlacedChests.contains(posKey(worldName, x, y, z));
    }

    public static void markPlayerPlaced(String worldName, int x, int y, int z) {
        if (playerPlacedChests.add(posKey(worldName, x, y, z))) {
            saveSet(playerChestsFile, playerPlacedChests);
        }
    }

    public static void unmarkPlayerPlaced(String worldName, int x, int y, int z) {
        if (playerPlacedChests.remove(posKey(worldName, x, y, z))) {
            saveSet(playerChestsFile, playerPlacedChests);
        }
    }

    public static boolean isEnriched(String worldName, int x, int y, int z) {
        return enrichedChests.contains(posKey(worldName, x, y, z));
    }

    /**
     * Atomically claims enrichment for a chest position.
     * @return true if this call claimed the enrichment (i.e. it was not already enriched).
     */
    public static boolean claimEnrichment(String worldName, int x, int y, int z) {
        boolean claimed = enrichedChests.add(posKey(worldName, x, y, z));
        if (claimed) saveSet(enrichedChestsFile, enrichedChests);
        return claimed;
    }

    public static void unmarkEnriched(String worldName, int x, int y, int z) {
        if (enrichedChests.remove(posKey(worldName, x, y, z))) {
            saveSet(enrichedChestsFile, enrichedChests);
        }
    }

    private static void loadSet(Path file, Set<String> target) {
        if (!Files.exists(file)) return;
        try {
            Files.readAllLines(file).stream()
                    .filter(l -> !l.isBlank())
                    .forEach(target::add);
        } catch (IOException e) {
            LOGGER.at(Level.SEVERE).withCause(e).log("Failed to load %s", file);
        }
    }

    private static void saveSet(Path file, Set<String> source) {
        try {
            Files.write(file, source);
        } catch (IOException e) {
            LOGGER.at(Level.SEVERE).withCause(e).log("Failed to save %s", file);
        }
    }
}