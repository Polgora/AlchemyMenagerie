package me.Polgora;

import java.util.*;

/**
 * Stores per-species bestiary data: lore description, fallback item icon ID,
 * and path to a custom creature icon PNG (relative to Common/).
 * Diet is sourced from {@link CreatureDietRegistry}; drops from {@link CreatureDropManager}.
 */
public class CreatureBestiaryRegistry {

    /**
     * @param descriptionLines  Lore lines shown in the bestiary detail view.
     * @param iconItemId        Item ID used as fallback icon (e.g. the egg item).
     * @param iconPath          Path to the custom creature icon PNG, relative to Common/
     *                          (e.g. "Icons/ModelsGenerated/Verdant_Sprout_Icon.png"),
     *                          or {@code null} if no custom icon exists yet.
     */
    public record BestiaryEntry(List<String> descriptionLines, String iconItemId, String iconPath) {}

    private static final Map<String, BestiaryEntry> ENTRIES = new HashMap<>();

    static {
        register("Verdant_Sprout",
            "Egg_Verdant_Sprout",
            "Verdant_Sprout_Icon.png",
            "A small, leafy critter that wanders grassy meadows in tight-knit flocks.",
            "Gentle by nature, it grazes through the day and dozes in the afternoon warmth.",
            "Though easily startled, patient hands bearing fresh greens may earn its trust."
        );
        register("Ember_Wyrmling",
            "Egg_Ember_Wyrmling",
            "Ember_Wyrmling_Icon.png",
            "Its scales cool and harden like obsidian, veined with fissures of living fire that pulse with an inner furnace.",
            "It slithers low across scorched earth, trailing drifting embers and a shimmer of heat wherever it roams.",
            "When threatened, the cracks across its body blaze white-hot and begin to weep droplets of molten rock."
        );
    }

    private static void register(String roleName, String iconItemId, String iconPath, String... lines) {
        ENTRIES.put(roleName, new BestiaryEntry(List.of(lines), iconItemId, iconPath));
    }

    /** Returns the bestiary entry for a species, or {@code null} if none is registered. */
    public static BestiaryEntry get(String roleName) {
        return ENTRIES.get(roleName);
    }

    /** Returns true if a bestiary entry exists for this species. */
    public static boolean has(String roleName) {
        return ENTRIES.containsKey(roleName);
    }
}