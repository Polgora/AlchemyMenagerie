package me.Polgora;

import me.Polgora.PouchManager.FlaskState;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Registry of all creature egg recipes and matching logic for the Menagerie Table.
 *
 * Each recipe defines the required element composition (percentages summing to 100)
 * and a tolerance window within which a flask's contents are accepted as a match.
 */
public class CreatureRecipeManager {

    /** Per-element tolerance: flask amount must be within ±TOLERANCE% of recipe amount. */
    public static final int TOLERANCE = 5;

    /**
     * Maximum single-element deviation that still counts as a "close miss".
     * If the closest recipe exceeds this on any element, the player gets the
     * generic "no creature matches" message instead of a targeted hint.
     */
    public static final int CLOSE_THRESHOLD = 30;

    /** Inverse-rarity weights used for random rolls when multiple recipes match. */
    private static final Map<String, Integer> RARITY_WEIGHTS;
    static {
        Map<String, Integer> w = new LinkedHashMap<>();
        w.put("Common",    100);
        w.put("Uncommon",   60);
        w.put("Rare",       30);
        w.put("Epic",       10);
        w.put("Legendary",   3);
        RARITY_WEIGHTS = Collections.unmodifiableMap(w);
    }

    // ── Recipe definition ─────────────────────────────────────────────────────

    public static class CreatureRecipe {
        public final String eggItemId;
        public final String displayName;
        public final String rarity;
        /** Element name → target percentage (values sum to 100). */
        public final Map<String, Integer> elements;

        public CreatureRecipe(String eggItemId, String displayName, String rarity,
                              Map<String, Integer> elements) {
            this.eggItemId   = eggItemId;
            this.displayName = displayName;
            this.rarity      = rarity;
            this.elements    = Collections.unmodifiableMap(new LinkedHashMap<>(elements));
        }

        /** Returns true if every element (in either the recipe or the flask) is within TOLERANCE. */
        public boolean matches(FlaskState flask) {
            Map<String, Integer> flaskMap = toMap(flask);
            Set<String> allKeys = allKeys(flaskMap);
            for (String el : allKeys) {
                int diff = Math.abs(elements.getOrDefault(el, 0) - flaskMap.getOrDefault(el, 0));
                if (diff > TOLERANCE) return false;
            }
            return true;
        }

        /** Returns the maximum single-element deviation between this recipe and the flask. */
        public int maxDeviation(FlaskState flask) {
            Map<String, Integer> flaskMap = toMap(flask);
            int max = 0;
            for (String el : allKeys(flaskMap)) {
                int diff = Math.abs(elements.getOrDefault(el, 0) - flaskMap.getOrDefault(el, 0));
                if (diff > max) max = diff;
            }
            return max;
        }

        /**
         * Returns a hint message pointing to the most deviant element.
         * E.g. "Transmutation failed! You need more Carbon."
         */
        public String getHint(FlaskState flask) {
            Map<String, Integer> flaskMap = toMap(flask);
            String worstEl   = null;
            int    worstDiff = 0;
            boolean needMore = false;
            for (String el : allKeys(flaskMap)) {
                int recipeAmt = elements.getOrDefault(el, 0);
                int flaskAmt  = flaskMap.getOrDefault(el, 0);
                int diff      = Math.abs(recipeAmt - flaskAmt);
                if (diff > worstDiff) {
                    worstDiff = diff;
                    worstEl   = el;
                    needMore  = recipeAmt > flaskAmt;
                }
            }
            if (worstEl == null) return "Transmutation failed!";
            return "Transmutation failed! You need " + (needMore ? "more" : "less") + " " + worstEl + ".";
        }

        /** E.g. "Carbon: 59%, Oxygen: 38%, Magnesium: 3%" */
        public String formatElements() {
            StringBuilder sb = new StringBuilder();
            for (Map.Entry<String, Integer> e : elements.entrySet()) {
                if (sb.length() > 0) sb.append(", ");
                sb.append(e.getKey()).append(": ").append(e.getValue()).append("%");
            }
            return sb.toString();
        }

        private Set<String> allKeys(Map<String, Integer> flaskMap) {
            Set<String> keys = new HashSet<>(elements.keySet());
            keys.addAll(flaskMap.keySet());
            return keys;
        }
    }

    // ── Recipe registry ───────────────────────────────────────────────────────

    private static final List<CreatureRecipe> RECIPES = new ArrayList<>();

    static {
        // Verdant Sprout — derived from Plant_Bush_Green_Known (Carbon 72%, Oxygen 46%, Magnesium 4%)
        // normalized to 100%: Carbon 59%, Oxygen 38%, Magnesium 3%
        Map<String, Integer> verdant = new LinkedHashMap<>();
        verdant.put("Carbon",    59);
        verdant.put("Oxygen",    38);
        verdant.put("Magnesium",  3);
        RECIPES.add(new CreatureRecipe("Egg_Verdant_Sprout", "Verdant Sprout", "Common", verdant));
    }

    // ── Matching API ──────────────────────────────────────────────────────────

    /** Returns all recipes whose element requirements are met by the flask within TOLERANCE. */
    public static List<CreatureRecipe> findMatches(FlaskState flask) {
        return RECIPES.stream().filter(r -> r.matches(flask)).collect(Collectors.toList());
    }

    /**
     * Randomly selects a winner from the matching recipes, weighted by rarity.
     * Rarer creatures have lower weight and are therefore less likely to be chosen.
     */
    public static CreatureRecipe rollWinner(List<CreatureRecipe> matches, Random random) {
        if (matches.isEmpty()) return null;
        if (matches.size() == 1) return matches.get(0);
        int totalWeight = matches.stream()
                .mapToInt(r -> RARITY_WEIGHTS.getOrDefault(r.rarity, 100))
                .sum();
        int roll        = random.nextInt(totalWeight);
        int accumulated = 0;
        for (CreatureRecipe recipe : matches) {
            accumulated += RARITY_WEIGHTS.getOrDefault(recipe.rarity, 100);
            if (roll < accumulated) return recipe;
        }
        return matches.get(matches.size() - 1);
    }

    /**
     * Returns the recipe with the smallest max-element deviation for this flask.
     * Used to determine whether a failure is a "close miss" and to generate hints.
     */
    public static CreatureRecipe findClosest(FlaskState flask) {
        CreatureRecipe closest   = null;
        int            minMaxDev = Integer.MAX_VALUE;
        for (CreatureRecipe recipe : RECIPES) {
            int dev = recipe.maxDeviation(flask);
            if (dev < minMaxDev) {
                minMaxDev = dev;
                closest   = recipe;
            }
        }
        return closest;
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static Map<String, Integer> toMap(FlaskState flask) {
        Map<String, Integer> map = new HashMap<>();
        for (FlaskState.ElementEntry e : flask.elements) {
            map.put(e.name, e.amount);
        }
        return map;
    }
}