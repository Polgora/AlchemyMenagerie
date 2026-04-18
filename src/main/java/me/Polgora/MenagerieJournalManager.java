package me.Polgora;

import com.hypixel.hytale.logger.HytaleLogger;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * Tracks per-player journal data not covered by {@link JournalManager}:
 * bestiary unlocks (species discovered by hatching) and deceased creature history.
 */
public class MenagerieJournalManager {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    public record DeceasedEntry(UUID stableId, String species, String displayName, String bondTier) {}

    private static Path dataDir;

    private static final Map<String, Set<String>>         BESTIARY = new ConcurrentHashMap<>();
    private static final Map<String, List<DeceasedEntry>> DECEASED = new ConcurrentHashMap<>();

    public static void init(Path dir) {
        dataDir = dir;
        try { Files.createDirectories(dir.resolve("bestiary")); } catch (IOException ignored) {}
        try { Files.createDirectories(dir.resolve("deceased")); } catch (IOException ignored) {}
    }

    // ── Bestiary ──────────────────────────────────────────────────────────────

    public static void unlockBestiary(UUID ownerUuid, String species) {
        if (ownerUuid == null || species == null) return;
        String id = ownerUuid.toString();
        if (!BESTIARY.computeIfAbsent(id, k -> ConcurrentHashMap.newKeySet()).add(species)) return;
        Path file = dataDir.resolve("bestiary").resolve(id + ".txt");
        try {
            Files.writeString(file, species + System.lineSeparator(),
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException e) {
            LOGGER.at(Level.SEVERE).withCause(e).log("Failed to save bestiary for %s", id);
        }
    }

    public static Set<String> getBestiary(String playerId) {
        return Collections.unmodifiableSet(BESTIARY.getOrDefault(playerId, Collections.emptySet()));
    }

    public static void loadBestiary(String playerId) {
        if (dataDir == null) return;
        Path file = dataDir.resolve("bestiary").resolve(playerId + ".txt");
        if (!Files.exists(file)) return;
        try {
            Set<String> entries = BESTIARY.computeIfAbsent(playerId, k -> ConcurrentHashMap.newKeySet());
            Files.readAllLines(file).stream().filter(l -> !l.isBlank()).forEach(entries::add);
        } catch (IOException e) {
            LOGGER.at(Level.SEVERE).withCause(e).log("Failed to load bestiary for %s", playerId);
        }
    }

    public static void unloadBestiary(String playerId) {
        BESTIARY.remove(playerId);
    }

    // ── Deceased ──────────────────────────────────────────────────────────────

    public static void recordDeath(UUID ownerUuid, UUID stableId, String species,
                                   String displayName, String bondTier) {
        if (ownerUuid == null || stableId == null) return;
        String id = ownerUuid.toString();
        List<DeceasedEntry> list = DECEASED.computeIfAbsent(id, k ->
                Collections.synchronizedList(new ArrayList<>()));
        synchronized (list) {
            if (list.stream().anyMatch(e -> e.stableId().equals(stableId))) return;
            list.add(new DeceasedEntry(stableId, species, displayName, bondTier));
        }
        Path file = dataDir.resolve("deceased").resolve(id + ".txt");
        try {
            String line = stableId + "|" + species + "|" + displayName + "|" + bondTier;
            Files.writeString(file, line + System.lineSeparator(),
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException e) {
            LOGGER.at(Level.SEVERE).withCause(e).log("Failed to save deceased for %s", id);
        }
    }

    public static List<DeceasedEntry> getDeceased(String playerId) {
        List<DeceasedEntry> list = DECEASED.get(playerId);
        if (list == null) return Collections.emptyList();
        synchronized (list) { return List.copyOf(list); }
    }

    public static void deleteDeceasedEntry(String playerId, UUID stableId) {
        List<DeceasedEntry> list = DECEASED.get(playerId);
        if (list == null) return;
        synchronized (list) {
            list.removeIf(e -> e.stableId().equals(stableId));
            rewriteDeceasedFile(playerId, list);
        }
    }

    private static void rewriteDeceasedFile(String playerId, List<DeceasedEntry> entries) {
        if (dataDir == null) return;
        Path file = dataDir.resolve("deceased").resolve(playerId + ".txt");
        try {
            List<String> lines = new ArrayList<>();
            for (DeceasedEntry e : entries) {
                lines.add(e.stableId() + "|" + e.species() + "|" + e.displayName() + "|" + e.bondTier());
            }
            Files.write(file, lines);
        } catch (IOException e) {
            LOGGER.at(Level.SEVERE).withCause(e).log("Failed to rewrite deceased for %s", playerId);
        }
    }

    public static void loadDeceased(String playerId) {
        if (dataDir == null) return;
        Path file = dataDir.resolve("deceased").resolve(playerId + ".txt");
        if (!Files.exists(file)) return;
        try {
            List<DeceasedEntry> entries = DECEASED.computeIfAbsent(playerId, k ->
                    Collections.synchronizedList(new ArrayList<>()));
            for (String line : Files.readAllLines(file)) {
                if (line.isBlank()) continue;
                String[] parts = line.split("\\|", 4);
                if (parts.length < 4) continue;
                try {
                    entries.add(new DeceasedEntry(UUID.fromString(parts[0]), parts[1], parts[2], parts[3]));
                } catch (IllegalArgumentException ignored) {}
            }
        } catch (IOException e) {
            LOGGER.at(Level.SEVERE).withCause(e).log("Failed to load deceased for %s", playerId);
        }
    }

    public static void unloadDeceased(String playerId) {
        DECEASED.remove(playerId);
    }
}