package me.Polgora.events;

import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.asset.LoadAssetEvent;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.bench.CraftingBench;

import java.lang.reflect.Field;
import java.util.Arrays;

public class AlchemyBenchCategorySetup {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    public static void onLoadAsset(LoadAssetEvent event) {
        BlockType benchBlock = BlockType.fromString("Bench_Alchemy");
        if (benchBlock == null || benchBlock.isUnknown()) {
            LOGGER.atWarning().log("Bench_Alchemy not found — AlchemyMenagerie tab will not be injected.");
            return;
        }

        try {
            Field benchField = BlockType.class.getDeclaredField("bench");
            benchField.setAccessible(true);
            Object bench = benchField.get(benchBlock);

            if (!(bench instanceof CraftingBench)) {
                LOGGER.atWarning().log("Bench_Alchemy does not use CraftingBench — skipping tab injection.");
                return;
            }

            CraftingBench craftingBench = (CraftingBench) bench;

            Field categoriesField = CraftingBench.class.getDeclaredField("categories");
            categoriesField.setAccessible(true);
            CraftingBench.BenchCategory[] existing =
                    (CraftingBench.BenchCategory[]) categoriesField.get(craftingBench);

            CraftingBench.BenchCategory newCategory = new CraftingBench.BenchCategory(
                    "Alchemybench_AlchemyMenagerie",
                    "server.benchCategories.alchemyMenagerie.name",
                    "Icons/ItemsGenerated/Alchemy_Flask.png",
                    new CraftingBench.BenchItemCategory[0]
            );

            CraftingBench.BenchCategory[] updated;
            if (existing == null) {
                updated = new CraftingBench.BenchCategory[]{newCategory};
            } else {
                updated = Arrays.copyOf(existing, existing.length + 1);
                updated[existing.length] = newCategory;
            }

            categoriesField.set(craftingBench, updated);
            LOGGER.atInfo().log("AlchemyMenagerie tab injected into Bench_Alchemy.");

        } catch (NoSuchFieldException | IllegalAccessException e) {
            LOGGER.atSevere().withCause(e).log("Failed to inject AlchemyMenagerie tab into Bench_Alchemy.");
        }
    }
}