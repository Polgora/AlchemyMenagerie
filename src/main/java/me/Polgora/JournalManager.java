package me.Polgora;

import com.hypixel.hytale.logger.HytaleLogger;
import me.Polgora.KnownItemSwapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.jar.JarFile;
import java.util.logging.Level;

/**
 * Tracks which block type IDs each player has scanned via the Arcanoscope.
 * Keyed by UUID string. Data is persisted to disk per-player on disconnect
 * and loaded back on connect.
 */
public class JournalManager {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    private static Path dataDir;
    private static final Map<String, Set<String>>    knownItems    = new ConcurrentHashMap<>();
    private static final Map<String, List<String>>   creatureLog   = new ConcurrentHashMap<>();

    /**
     * Item IDs from mod resources that have a _Known counterpart, meaning they
     * are eligible to be scanned with the Arcanoscope.
     */
    private static final Set<String> scannableItems = ConcurrentHashMap.newKeySet();

    /**
     * Called once during plugin setup.
     * @param dir        directory for per-player journal files
     * @param pluginFile path to the plugin JAR (production) or src/main directory (dev)
     */
    public static void init(Path dir, Path pluginFile) {
        dataDir = dir;
        try {
            Files.createDirectories(dataDir);
        } catch (IOException e) {
            LOGGER.at(Level.SEVERE).withCause(e).log("Failed to create journal data directory: %s", dataDir);
        }
        loadScannableItems(pluginFile);
        LOGGER.at(Level.INFO).log("Loaded %d scannable item(s) from mod resources.", scannableItems.size());
    }

    /**
     * Populates {@code scannableItems} with every base item ID that has a _Known
     * counterpart defined in the mod resources.
     */
    private static void loadScannableItems(Path pluginFile) {
        String suffix = KnownItemSwapper.KNOWN_SUFFIX + ".json";
        try {
            if (pluginFile.toString().endsWith(".jar")) {
                // Production: scan JAR entries
                try (JarFile jar = new JarFile(pluginFile.toFile())) {
                    jar.stream()
                        .map(e -> e.getName())
                        .filter(n -> n.startsWith("Server/Item/Items/") && n.endsWith(suffix))
                        .forEach(n -> {
                            String filename = n.substring(n.lastIndexOf('/') + 1);
                            String knownId = filename.substring(0, filename.length() - 5); // strip .json
                            scannableItems.add(knownId.substring(0, knownId.length() - KnownItemSwapper.KNOWN_SUFFIX.length()));
                        });
                }
            } else {
                // Development: walk the resources directory
                Path itemsDir = pluginFile.resolve("resources").resolve("Server").resolve("Item").resolve("Items");
                if (!Files.exists(itemsDir)) return;
                Files.walk(itemsDir)
                    .map(p -> p.getFileName().toString())
                    .filter(name -> name.endsWith(suffix))
                    .forEach(name -> {
                        String knownId = name.substring(0, name.length() - 5); // strip .json
                        scannableItems.add(knownId.substring(0, knownId.length() - KnownItemSwapper.KNOWN_SUFFIX.length()));
                    });
            }
        } catch (IOException e) {
            LOGGER.at(Level.SEVERE).withCause(e).log("Failed to scan mod resources for scannable items: %s", pluginFile);
        }
    }

    /**
     * Returns true if the given item ID has a _Known counterpart in the mod resources
     * and is therefore eligible to be scanned with the Arcanoscope.
     */
    public static boolean isScannableItem(String itemId) {
        return scannableItems.contains(itemId);
    }

    /**
     * Clears all scanned entries for a player and deletes their journal file on disk.
     */
    public static void clearPlayer(String playerId) {
        knownItems.remove(playerId);
        if (dataDir == null) return;
        Path file = dataDir.resolve(playerId + ".txt");
        try {
            Files.deleteIfExists(file);
        } catch (IOException e) {
            LOGGER.at(Level.SEVERE).withCause(e).log("Failed to delete journal file for player %s", playerId);
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

    // ── Creature attempt log ─────────────────────────────────────────────────

    /**
     * Appends a creature crafting attempt to the player's log.
     * Each entry is a pipe-delimited string: "SUCCESS|eggItemId|display details"
     * or "FAILURE|eggItemId|(none)|failure message".
     */
    public static void addCreatureAttempt(String playerId, String entry) {
        creatureLog.computeIfAbsent(playerId, k -> new ArrayList<>()).add(entry);
        if (dataDir == null) return;
        Path file = dataDir.resolve(playerId + "_creatures.txt");
        try {
            Files.writeString(file, entry + System.lineSeparator(), StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException e) {
            LOGGER.at(Level.SEVERE).withCause(e).log("Failed to append creature attempt for player %s", playerId);
        }
    }

    public static List<String> getCreatureAttempts(String playerId) {
        return Collections.unmodifiableList(
                creatureLog.getOrDefault(playerId, Collections.emptyList()));
    }

    /** Loads a player's creature attempt log from disk into memory. */
    public static void loadPlayerCreatures(String playerId) {
        if (dataDir == null) return;
        Path file = dataDir.resolve(playerId + "_creatures.txt");
        if (!Files.exists(file)) return;
        try {
            List<String> lines = Files.readAllLines(file);
            List<String> entries = creatureLog.computeIfAbsent(playerId, k -> new ArrayList<>());
            lines.stream().filter(l -> !l.isBlank()).forEach(entries::add);
        } catch (IOException e) {
            LOGGER.at(Level.SEVERE).withCause(e).log("Failed to load creature log for player %s", playerId);
        }
    }

    /** Removes a player's creature log from memory (data is already on disk via append). */
    public static void unloadPlayerCreatures(String playerId) {
        creatureLog.remove(playerId);
    }
}