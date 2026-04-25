package me.Polgora;

import java.util.*;

/**
 * Maps creature species to their diet type and defines which vanilla food items
 * belong to each diet category.
 *
 * <p>Herbivores eat only plant/grain/fruit/vegetable-based foods.
 * Carnivores eat only meat and fish. Omnivores accept everything both diets accept.
 */
public class CreatureDietRegistry {

    public enum DietType { HERBIVORE, OMNIVORE, CARNIVORE }

    // ── Species → diet ────────────────────────────────────────────────────────

    private static final Map<String, DietType> SPECIES_DIET;
    static {
        Map<String, DietType> m = new HashMap<>();
        m.put("Verdant_Sprout", DietType.HERBIVORE);
        m.put("Ember_Wyrmling", DietType.CARNIVORE);
        SPECIES_DIET = Collections.unmodifiableMap(m);
    }

    // ── Herbivore foods ───────────────────────────────────────────────────────
    // Plant, grain, fruit, vegetable, and mushroom-based foods.

    private static final Set<String> HERBIVORE_FOODS = Set.of(
            // Prepared foods
            "Food_Bread",
            "Food_Candy_Cane",
            "Food_Cheese",
            "Food_Kebab_Fruit",
            "Food_Kebab_Mushroom",
            "Food_Kebab_Vegetable",
            "Food_Pie_Apple",
            "Food_Pie_Pumpkin",
            "Food_Popcorn",
            "Food_Salad_Berry",
            "Food_Salad_Caesar",
            "Food_Salad_Mushroom",
            "Food_Vegetable_Cooked",
            // Raw fruits
            "Plant_Fruit_Apple",
            "Plant_Fruit_Azure",
            "Plant_Fruit_Berries_Red",
            "Plant_Fruit_Coconut",
            "Plant_Fruit_Mango",
            "Plant_Fruit_Pinkberry",
            "Plant_Fruit_Spiral",
            "Plant_Fruit_Windwillow",
            // Raw crop harvests
            "Plant_Crop_Aubergine_Item",
            "Plant_Crop_Carrot_Item",
            "Plant_Crop_Cauliflower_Item",
            "Plant_Crop_Chilli_Item",
            "Plant_Crop_Corn_Item",
            "Plant_Crop_Lettuce_Item",
            "Plant_Crop_Onion_Item",
            "Plant_Crop_Potato_Item",
            "Plant_Crop_Pumpkin_Item",
            "Plant_Crop_Rice_Item",
            "Plant_Crop_Tomato_Item",
            "Plant_Crop_Turnip_Item",
            "Plant_Crop_Wheat_Item"
    );

    // ── Carnivore foods ───────────────────────────────────────────────────────
    // Raw/cooked meat, fish drops, and meat-based prepared foods.

    private static final Set<String> CARNIVORE_FOODS = Set.of(
            // Meat
            "Food_Beef_Raw",
            "Food_Chicken_Raw",
            "Food_Pork_Raw",
            "Food_Wildmeat_Raw",
            "Food_Wildmeat_Cooked",
            "Food_Kebab_Meat",
            "Food_Pie_Meat",
            // Fish (processed)
            "Food_Fish_Raw",
            "Food_Fish_Raw_Uncommon",
            "Food_Fish_Raw_Rare",
            "Food_Fish_Raw_Epic",
            "Food_Fish_Raw_Legendary",
            "Food_Fish_Grilled",
            // Fish drops
            "Fish_Bluegill_Item",
            "Fish_Catfish_Item",
            "Fish_Clownfish_Item",
            "Fish_Crab_Item",
            "Fish_Eel_Moray_Item",
            "Fish_Frostgill_Item",
            "Fish_Jellyfish_Blue_Item",
            "Fish_Jellyfish_Cyan_Item",
            "Fish_Jellyfish_Green_Item",
            "Fish_Jellyfish_Man_Of_War_Item",
            "Fish_Jellyfish_Red_Item",
            "Fish_Jellyfish_Yellow_Item",
            "Fish_Lobster_Item",
            "Fish_Minnow_Item",
            "Fish_Pike_Item",
            "Fish_Piranha_Black_Item",
            "Fish_Piranha_Item",
            "Fish_Pufferfish_Item",
            "Fish_Salmon_Item",
            "Fish_Shark_Hammerhead_Item",
            "Fish_Shellfish_Lava_Item",
            "Fish_Snapjaw_Item",
            "Fish_Tang_Blue_Item",
            "Fish_Tang_Chevron_Item",
            "Fish_Tang_Lemon_Peel_Item",
            "Fish_Tang_Sailfin_Item",
            "Fish_Trilobite_Black_Item",
            "Fish_Trilobite_Item",
            "Fish_Trout_Rainbow_Item",
            "Fish_Whale_Humpback_Item"
    );

    // ── Omnivore foods ────────────────────────────────────────────────────────
    // Accepts herbivore + carnivore foods, plus egg (cross-diet).

    private static final Set<String> OMNIVORE_FOODS;
    static {
        Set<String> all = new HashSet<>();
        all.addAll(HERBIVORE_FOODS);
        all.addAll(CARNIVORE_FOODS);
        all.add("Food_Egg");
        OMNIVORE_FOODS = Collections.unmodifiableSet(all);
    }

    // ── Public API ────────────────────────────────────────────────────────────

    /** Returns the diet type for the given NPC role name, or {@code null} if unknown. */
    public static DietType getDiet(String roleName) {
        return SPECIES_DIET.get(roleName);
    }

    /** Returns {@code true} if the given role name is a registered Menagerie creature. */
    public static boolean isKnownCreature(String roleName) {
        return roleName != null && SPECIES_DIET.containsKey(roleName);
    }

    /**
     * Returns {@code true} if {@code itemId} is an accepted food for {@code diet}.
     * Omnivore accepts all food items that herbivore or carnivore diets accept, plus egg.
     */
    public static boolean isAcceptedFood(DietType diet, String itemId) {
        if (diet == null || itemId == null) return false;
        return switch (diet) {
            case HERBIVORE -> HERBIVORE_FOODS.contains(itemId);
            case CARNIVORE -> CARNIVORE_FOODS.contains(itemId);
            case OMNIVORE  -> OMNIVORE_FOODS.contains(itemId);
        };
    }

    /** Human-readable diet label for display in the UI. */
    public static String getDietDisplayName(DietType diet) {
        if (diet == null) return "Unknown";
        return switch (diet) {
            case HERBIVORE -> "Herbivore";
            case CARNIVORE -> "Carnivore";
            case OMNIVORE  -> "Omnivore";
        };
    }
}