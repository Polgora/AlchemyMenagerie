package me.Polgora;

import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.inventory.ItemStack;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * Stores per-player Flask Pouch contents (up to 3 flask ItemStacks).
 * Persists to disk on disconnect and reloads on connect.
 */
public class PouchManager {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    public static final int MAX_SLOTS = 3;
    public static final String FLASK_ITEM_PREFIX = "Alchemy_Flask";

    private static Path dataDir;
    private static final Map<String, List<ItemStack>> pouchContents = new ConcurrentHashMap<>();

    public static void init(Path dir) {
        dataDir = dir;
        try {
            Files.createDirectories(dataDir);
        } catch (IOException e) {
            LOGGER.at(Level.SEVERE).withCause(e).log("Failed to create pouch data directory: %s", dataDir);
        }
    }

    public static List<ItemStack> getContents(String playerId) {
        return Collections.unmodifiableList(pouchContents.getOrDefault(playerId, Collections.emptyList()));
    }

    public static int getSlotCount(String playerId) {
        return pouchContents.getOrDefault(playerId, Collections.emptyList()).size();
    }

    public static boolean isFull(String playerId) {
        return getSlotCount(playerId) >= MAX_SLOTS;
    }

    /**
     * Attempts to add a flask to the pouch.
     * @return false if the pouch is full or the item is not a flask.
     */
    public static boolean addFlask(String playerId, ItemStack stack) {
        if (!stack.getItemId().startsWith(FLASK_ITEM_PREFIX)) return false;
        List<ItemStack> contents = pouchContents.computeIfAbsent(playerId, k -> new ArrayList<>());
        if (contents.size() >= MAX_SLOTS) return false;
        contents.add(stack);
        return true;
    }

    /**
     * Removes and returns the flask at the given slot index, or null if out of range.
     */
    public static ItemStack removeFlask(String playerId, int index) {
        List<ItemStack> contents = pouchContents.get(playerId);
        if (contents == null || index < 0 || index >= contents.size()) return null;
        return contents.remove(index);
    }

    public static void loadPlayer(String playerId) {
        if (dataDir == null) return;
        Path file = dataDir.resolve(playerId + ".txt");
        if (!Files.exists(file)) return;

        try {
            List<String> lines = Files.readAllLines(file);
            List<ItemStack> contents = pouchContents.computeIfAbsent(playerId, k -> new ArrayList<>());
            contents.clear();
            for (String line : lines) {
                if (line.isBlank()) continue;
                String[] parts = line.split(",", 2);
                String id = parts[0].trim();
                int qty = parts.length > 1 ? Integer.parseInt(parts[1].trim()) : 1;
                contents.add(new ItemStack(id, qty));
            }
        } catch (IOException | NumberFormatException e) {
            LOGGER.at(Level.SEVERE).withCause(e).log("Failed to load pouch for player %s", playerId);
        }
    }

    public static void savePlayer(String playerId) {
        if (dataDir == null) return;
        List<ItemStack> contents = pouchContents.remove(playerId);
        if (contents == null || contents.isEmpty()) return;

        Path file = dataDir.resolve(playerId + ".txt");
        try {
            List<String> lines = new ArrayList<>();
            for (ItemStack stack : contents) {
                lines.add(stack.getItemId() + "," + stack.getQuantity());
            }
            Files.write(file, lines);
        } catch (IOException e) {
            LOGGER.at(Level.SEVERE).withCause(e).log("Failed to save pouch for player %s", playerId);
        }
    }
}