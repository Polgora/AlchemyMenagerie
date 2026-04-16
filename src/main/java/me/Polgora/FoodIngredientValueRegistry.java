package me.Polgora;

import java.util.HashMap;
import java.util.Map;

/**
 * Maps vanilla food item IDs to their recursive raw-ingredient count.
 *
 * <p>The count is the total number of raw food items (crops, fruits, meats, fish,
 * eggs, milk) that go into crafting one unit, recursing through sub-ingredients
 * (e.g. bread → dough → flour → wheat + eggs). Non-food crafting materials such
 * as sticks, salt, spices, and fuel are not counted.
 *
 * <p>Items absent from the map (raw crops, fruits, fish, meats, eggs) default to
 * {@code 1}. Only prepared foods whose raw-ingredient total differs from 1 need
 * an explicit entry.
 *
 * <h3>Derivation of notable values</h3>
 * <ul>
 *   <li>Ingredient_Flour: 10× Plant_Crop_Wheat_Item → 10
 *   <li>Ingredient_Dough: 1× Flour (10) + 2× Food_Egg → 12
 *   <li>Food_Bread:               1× Dough (12) + fuel(0)                   = 12
 *   <li>Food_Kebab_Mushroom:      3× Mushrooms + stick(0)                   =  3
 *   <li>Food_Salad_Mushroom:      1× Lettuce + 3× Mushrooms                 =  4
 *   <li>Food_Kebab_Fruit/Veg/Meat: 4× ingredient + stick(0)                 =  4
 *   <li>Food_Pie_Apple:           1× Dough(12) + 3× Apple + spices/fuel(0) = 15
 *   <li>Food_Pie_Pumpkin/Meat:    1× Dough(12) + 1× filling                = 13
 *   <li>Food_Salad_Berry:         1× Lettuce + 5× Berries                  =  6
 *   <li>Food_Salad_Caesar:        1× Lettuce + Cheese(1 milk) + 1× Meat    =  3
 *   <li>Food_Popcorn:             2× Corn + salt/fuel(0)                   =  2
 * </ul>
 */
public class FoodIngredientValueRegistry {

    private static final Map<String, Integer> COUNTS = new HashMap<>();

    static {
        // ── Prepared herbivore foods ──────────────────────────────────────────
        COUNTS.put("Food_Bread",            12); // 1×Dough(12) + fuel
        COUNTS.put("Food_Kebab_Fruit",       4); // 4×Fruits + stick
        COUNTS.put("Food_Kebab_Mushroom",    3); // 3×Mushrooms + stick
        COUNTS.put("Food_Kebab_Vegetable",   4); // 4×Vegetables + stick
        COUNTS.put("Food_Pie_Apple",        15); // 1×Dough(12) + 3×Apple + spices+fuel
        COUNTS.put("Food_Pie_Pumpkin",      13); // 1×Dough(12) + 1×Pumpkin + spices+fuel
        COUNTS.put("Food_Popcorn",           2); // 2×Corn + salt+fuel
        COUNTS.put("Food_Salad_Berry",       6); // 1×Lettuce + 5×Berries
        COUNTS.put("Food_Salad_Caesar",      3); // 1×Lettuce + Cheese(1 milk) + 1×Meat + salt+spices
        COUNTS.put("Food_Salad_Mushroom",    4); // 1×Lettuce + 3×Mushrooms

        // ── Prepared carnivore foods ──────────────────────────────────────────
        COUNTS.put("Food_Kebab_Meat",        4); // 4×Meats + stick
        COUNTS.put("Food_Pie_Meat",         13); // 1×Dough(12) + 1×Meat + spices+salt+fuel
    }

    private FoodIngredientValueRegistry() {}

    /**
     * Returns the raw food ingredient count for {@code itemId}.
     * Returns {@code 1} for any item not in the map (raw crops, fruits, meats,
     * fish, eggs, etc.).
     */
    public static int getCount(String itemId) {
        return COUNTS.getOrDefault(itemId, 1);
    }
}