package me.Polgora;

import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import org.bson.BsonArray;
import org.bson.BsonDocument;
import org.bson.BsonValue;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Loads and provides per-creature anchor resource drop tables.
 *
 * <p>Drop configs live in {@code Server/Drops/NPCs/Critter/Drop_{RoleName}.json}.
 * Each file is a JSON object with a {@code "Drops"} array. Call {@link #load(String)}
 * once per creature role at startup.
 */
public class CreatureDropManager {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    public static class DropEntry {
        public final String itemId;
        public final int    quantityMin;
        public final int    quantityMax;
        public final double chance;

        public DropEntry(String itemId, int quantityMin, int quantityMax, double chance) {
            this.itemId      = itemId;
            this.quantityMin = quantityMin;
            this.quantityMax = quantityMax;
            this.chance      = chance;
        }
    }

    private static final Map<String, List<DropEntry>> DROPS = new HashMap<>();

    // ── Load ──────────────────────────────────────────────────────────────────

    /**
     * Reads the drop config for {@code roleName} from the plugin resources.
     * Safe to call at startup; silently does nothing if the file is missing.
     */
    public static void load(String roleName) {
        String path = "/Server/Drops/NPCs/Critter/Drop_" + roleName + ".json";
        try (InputStream is = CreatureDropManager.class.getResourceAsStream(path)) {
            if (is == null) {
                LOGGER.at(java.util.logging.Level.WARNING).log(
                        "CreatureDropManager: no drop config found at %s", path);
                return;
            }
            String json = new String(is.readAllBytes(), StandardCharsets.UTF_8);
            BsonDocument doc = BsonDocument.parse(json);
            BsonArray array = doc.getArray("Drops");

            List<DropEntry> entries = new ArrayList<>();
            for (BsonValue val : array) {
                BsonDocument d = val.asDocument();
                entries.add(new DropEntry(
                        d.getString("ItemId").getValue(),
                        d.getInt32("QuantityMin").getValue(),
                        d.getInt32("QuantityMax").getValue(),
                        d.getDouble("Chance").getValue()
                ));
            }
            DROPS.put(roleName, Collections.unmodifiableList(entries));
            LOGGER.at(java.util.logging.Level.INFO).log(
                    "CreatureDropManager: loaded %d drop entries for %s", entries.size(), roleName);
        } catch (Exception e) {
            LOGGER.at(java.util.logging.Level.WARNING).log(
                    "CreatureDropManager: failed to load drop config for %s — %s", roleName, e.getMessage());
        }
    }

    // ── Query ─────────────────────────────────────────────────────────────────

    public static boolean hasDrops(String roleName) {
        return DROPS.containsKey(roleName);
    }

    /** Returns the full drop table for {@code roleName}, or an empty list if none is registered. */
    public static List<DropEntry> getDrops(String roleName) {
        List<DropEntry> list = DROPS.get(roleName);
        return list != null ? list : Collections.emptyList();
    }

    /**
     * Rolls drops for {@code roleName} and returns the resulting item stacks.
     * Each entry's {@code chance} is an independent [0, 1] probability.
     * Returns an empty list if no config is registered.
     */
    public static List<ItemStack> rollDrops(String roleName, Random random) {
        return rollDrops(roleName, random, 1.0);
    }

    /**
     * Rolls drops for {@code roleName} with a bond-level quantity multiplier.
     * The rolled quantity is scaled by {@code multiplier} and rounded to the
     * nearest integer (minimum 1 when a roll succeeds).
     */
    public static List<ItemStack> rollDrops(String roleName, Random random, double multiplier) {
        List<DropEntry> entries = DROPS.get(roleName);
        if (entries == null) return Collections.emptyList();

        List<ItemStack> result = new ArrayList<>();
        for (DropEntry e : entries) {
            if (random.nextDouble() < e.chance) {
                int qty = e.quantityMin;
                if (e.quantityMax > e.quantityMin) {
                    qty += random.nextInt(e.quantityMax - e.quantityMin + 1);
                }
                if (multiplier != 1.0) {
                    qty = Math.max(1, (int) Math.round(qty * multiplier));
                }
                result.add(new ItemStack(e.itemId, qty));
            }
        }
        return result;
    }
}