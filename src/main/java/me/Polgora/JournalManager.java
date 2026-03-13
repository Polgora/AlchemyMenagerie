package me.Polgora;

import com.hypixel.hytale.logger.HytaleLogger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * Tracks which block type IDs each player has scanned via the Arcanoscope.
 * Keyed by UUID string. Data is persisted to disk per-player on disconnect
 * and loaded back on connect.
 */
public class JournalManager {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    private static Path dataDir;
    private static final Map<String, Set<String>> knownItems = new ConcurrentHashMap<>();

    /** Called once during plugin setup to set the directory for journal files. */
    public static void init(Path dir) {
        dataDir = dir;
        try {
            Files.createDirectories(dataDir);
        } catch (IOException e) {
            LOGGER.at(Level.SEVERE).withCause(e).log("Failed to create journal data directory: %s", dataDir);
        }
    }

    /**
     * Records an item ID as known for a player.
     * @return true if newly discovered, false if already in the journal.
     */
    public static boolean addKnownItem(String playerId, String itemId) {
        Set<String> items = knownItems.computeIfAbsent(playerId, k -> ConcurrentHashMap.newKeySet());
        return items.add(itemId);
    }

    public static boolean isKnown(String playerId, String itemId) {
        Set<String> items = knownItems.get(playerId);
        return items != null && items.contains(itemId);
    }

    public static Set<String> getKnownItems(String playerId) {
        return Collections.unmodifiableSet(
                knownItems.getOrDefault(playerId, Collections.emptySet()));
    }

    /** Loads a player's journal from disk into memory. */
    public static void loadPlayer(String playerId) {
        if (dataDir == null) return;
        Path file = dataDir.resolve(playerId + ".txt");
        if (!Files.exists(file)) return;

        try {
            List<String> lines = Files.readAllLines(file);
            Set<String> items = knownItems.computeIfAbsent(playerId, k -> ConcurrentHashMap.newKeySet());
            lines.stream().filter(l -> !l.isBlank()).forEach(items::add);
        } catch (IOException e) {
            LOGGER.at(Level.SEVERE).withCause(e).log("Failed to load journal for player %s", playerId);
        }
    }

    /** Saves a player's in-memory journal to disk and removes it from memory. */
    public static void savePlayer(String playerId) {
        if (dataDir == null) return;
        Set<String> items = knownItems.remove(playerId);
        if (items == null || items.isEmpty()) return;

        Path file = dataDir.resolve(playerId + ".txt");
        try {
            Files.write(file, items);
        } catch (IOException e) {
            LOGGER.at(Level.SEVERE).withCause(e).log("Failed to save journal for player %s", playerId);
        }
    }
}