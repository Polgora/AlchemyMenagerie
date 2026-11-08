# DRAFT — Compositions for blocks missing a `_Known` variant

**Status: proposal for review. Nothing here has been applied to `ELEMENT_ASSIGNMENTS.md`, the `_Known` JSONs or `server.lang`.**

Conventions followed (taken from the existing 2,500 entries): 3–4 elements per block, percentages sum to 100, element names exactly as the Arcanoscope reports them, every variant of one material (stairs, half slab, roof, pipe...) shares one composition unless noted. Real-world chemistry first; balance second.

Excluded as non-scannable (VFX spawners, debug, lore notes, puzzle/portal parts, category filters), plus dev/test items: `Template_Crop`, `Template_Tool_Watering_Can`, `Plant_Test_Tree`, `Glass_Test_*`, `Prototype_Window_*` (12). The `Prototype_Rock_*` pipe pieces stay: they share their stone family's composition.

**Decisions locked in (2026-11-08):** keep Mercury (Legendary fish) and Tellurium (Incandescent rock); skip Prototype windows; `Stalactite_Smoke_Floor` reuses the stone stalactite; rare earths spread across separate blocks.

---

## A. Reuse an existing composition (no new decisions)

| New ID(s) | Reuses | Composition |
|---|---|---|
| `Furniture_Desert_Lantern_Ceiling` | `Furniture_Desert_Lantern` | Iron 35, Silicon 30, Aluminum 25, Barium 10 |
| `Furniture_Temple_Light_Lantern_Ceiling` | `Furniture_Temple_Light_Lantern` | Gold 40, Silicon 30, Oxygen 20, Neon 10 |
| `Furniture_Royal_Magic_Potion_Glow_Ceiling` | `Furniture_Royal_Magic_Potion_Glow` | Carbon 35, Oxygen 25, Phosphorus 20, Europium 20 |
| `Furniture_Temple_Light_Door_Medilum` | `Furniture_Temple_Light_Door` | Silicon 45, Oxygen 30, Gold 15, Calcium 10 |
| `Furniture_Human_Ruins_Lantern_Ceiling` | `Furniture_Human_Ruins_Lantern` | Iron 45, Silicon 25, Carbon 20, Copper 10 |
| `Furniture_Kweebec_Lantern_Ceiling` | `Furniture_Kweebec_Lantern` | Iron 40, Carbon 30, Oxygen 20, Sulfur 10 |
| `Deco_Lantern_Ceiling` | `Deco_Lantern` | Iron 45, Carbon 25, Oxygen 20, Silicon 10 |
| `Furniture_Temple_Emerald_Statue_Head` | `Furniture_Temple_Emerald_Statue` | Carbon 60, Oxygen 25, Hydrogen 10, Iron 5 |
| `Furniture_Goblin_Chest_Large`, `Furniture_Goblin_ChestStolen_Small` | `Furniture_Goblin_Chest_Small` | Carbon 50, Oxygen 25, Iron 15, Copper 10 |
| `Plant_Flower_Common_Poisoned2` | `Plant_Flower_Common_Poisoned` | Carbon 55, Oxygen 25, Hydrogen 15, Iron 5 |
| `Plant_Grass_Dry` | `Plant_Grass_Arid` | Carbon 45, Oxygen 30, Hydrogen 15, Nitrogen 10 |
| `Soil_Grass_Dry` | `Soil_Grass_Dry_Full` | Carbon 45, Oxygen 30, Hydrogen 15, Nitrogen 10 |
| `Soil_Leaves_Full` | `Soil_Leaves` | Silicon 35, Oxygen 30, Carbon 20, Nitrogen 15 |
| `Plant_Leaves_Goldentree` | `Plant_Leaves_Amber` | Carbon 50, Oxygen 30, Hydrogen 15, Nitrogen 5 |
| `Plant_Crop_Apple_Block`, `_Block_Wall` | `Plant_Fruit_Apple` | Carbon 45, Oxygen 35, Hydrogen 15, Potassium 5 |
| `Plant_Crop_Berry_Wet_Block`, `Plant_Crop_Berry_Winter_Block` | `Plant_Crop_Berry_Block` | Carbon 45, Oxygen 35, Hydrogen 15, Nitrogen 5 |
| `Plant_Crop_Mushroom_Block` | `Plant_Crop_Mushroom_Block_Brown` | Carbon 40, Oxygen 35, Hydrogen 15, Nitrogen 10 |
| `Rock_Stone_Aqua` | `Rock_Aqua` | Silicon 45, Oxygen 40, Aluminum 10, Iron 5 |
| `Rock_Stone_Aqua_Brick` ("Smooth Half Slab") | `Rock_Aqua_Brick_Smooth_Half` | Silicon 40, Oxygen 35, Calcium 15, Copper 10 |
| `Rock_Crystal_{Blue,Cyan,Green,Pink,Purple,Red,White,Yellow}_Block` | matching `_Large` crystal | per colour (e.g. Blue: Silicon 30, Oxygen 30, Gallium 20, Copper 20) |
| `Wood_Bamboo_Trunk_Deco`, `Wood_Bamboo_Branch_Long_Deco` | `Wood_Bamboo_Trunk` / `_Branch_Long` | Carbon 48, Oxygen 32, Hydrogen 15, Silicon 5 |
| `Deco_Halloween_Pumpkin_Hypixel`, `_Scary` | `Deco_Halloween_Pumpkin_Cute` | Carbon 50, Oxygen 30, Hydrogen 15, Nitrogen 5 |
| `Deco_SpiderWeb_Diagonal`, `_Straight`, `Cocoon_Spider_Cave`, `Spider_Cocoon_Falling` | `Deco_SpiderWeb` | Carbon 60, Hydrogen 25, Nitrogen 10, Oxygen 5 |
| `Stalactite_Smoke_Floor` (plain stone stalactite with a smoke particle) | `Rock_Stone_Stalactite_Small` | Silicon 55, Oxygen 30, Iron 10, Aluminum 5 |
| `Scrap_Hook` | `Metal_Iron` | Iron 80, Carbon 15, Oxygen 5 |
| `Ingredient_Fishbone` | `Deco_Bone_Full` | Calcium 45, Phosphorus 25, Oxygen 20, Carbon 10 |
| `Deco_Trash_Eaten_Fish` | `Food_Fish_Raw` | Carbon 50, Oxygen 25, Hydrogen 15, Phosphorus 10 |
| `Plant_Crop_{Health,Mana,Stamina}{1,2,3}_Block`, `Plant_Crop_Mushroom_Common_Lime_Large`, `Soil_Snow_Half` | their documented base item | (as base) |

## B. New compositions

### Stone (≈111 items)

| Family | Items | Composition | Why |
|---|---|---|---|
| Lime Brick | 16 | Calcium 42, Oxygen 38, Carbon 15, **Strontium** 5 | Limestone; Sr substitutes for Ca |
| Lime Cobble | 12 | Calcium 40, Oxygen 38, Carbon 17, **Barium** 5 | Limestone with barite traces |
| Ledge Brick (+ pipes) | 11 | Silicon 42, Oxygen 38, Aluminum 12, **Holmium** 8 | Granitic stone with rare-earth minerals |
| Ledge Cobble (+ proto pipes) | 6+3 | Silicon 43, Oxygen 37, Aluminum 12, **Lithium** 8 | Pegmatite-style; Li is rare in the mod |
| Peach Brick (+ proto pipes) | 7+2 | Silicon 45, Oxygen 35, Potassium 12, **Erbium** 8 | Pink feldspar granite; erbium oxide is pink |
| Peach Cobble (+ proto pipes) | 7+2 | Silicon 45, Oxygen 38, Potassium 12, **Rubidium** 5 | Rb follows K in feldspar |
| Runic Blue Brick | 9 | Silicon 42, Oxygen 33, Aluminum 15, **Cobalt** 10 | Cobalt = blue |
| Runic Teal Brick | 9 | Silicon 42, Oxygen 33, Aluminum 15, **Vanadium** 10 | Vanadium oxides = green/teal |
| Runic Dark Brick (+ proto pipes) | 7+2 | Silicon 40, Oxygen 30, Iron 18, Titanium 12 | Dark ilmenite-bearing stone |
| Cracked Volcanic - Incandescent | 1 | Silicon 40, Oxygen 25, Iron 25, **Tellurium** 10 | Matches Lava/Poisoned siblings; Te occurs in volcanic gases |
| Kenolite Rock | 1 | Silicon 45, Oxygen 35, **Gadolinium** 20 | Rare-earth ore |
| Kenolite Stalactite | 1 | Silicon 45, Oxygen 35, **Dysprosium** 12, **Ytterbium** 8 | Rare-earth ore |
| Void Crystal (Block, Large, Medium, Small) | 4 | Silicon 30, Oxygen 25, **Iridium** 25, **Tantalum** 20 | Dense "void" crystal |
| Dark Green Clay - Smooth | 1 | Silicon 35, Oxygen 35, Aluminum 20, Nickel 10 | Clay pattern; Ni = green |

### Metal (≈75 items)

| Family | Items | Composition | Why |
|---|---|---|---|
| Goblin Iron (solid, bars, chains, stack, roof, stairs...) | ~28 | Iron 70, Carbon 15, Manganese 10, **Niobium** 5 | Crude alloy steel |
| Goblin Iron pipes (not glowing) | ~9 | Iron 65, Carbon 15, Manganese 10, Lead 10 | Pipe pattern matches Bronze pipes (adds Pb) |
| Goblin Iron glowing pipes | 5 | Iron 60, Carbon 15, Phosphorus 15, Manganese 10 | P gives the glow |
| Enkindled Copper | ~15 | Copper 80, Zinc 10, Nickel 5, **Ruthenium** 5 | Hardened copper alloy |
| Enkindled Copper pipes | 4 | Copper 75, Zinc 10, Lead 10, **Ruthenium** 5 | Pipe pattern |
| Enkindled Iron | ~14 | Iron 75, Carbon 10, Chromium 10, **Hafnium** 5 | Superalloy steel |
| Enkindled Iron pipes | 4 | Iron 70, Carbon 10, Chromium 10, Lead 10 | Pipe pattern |
| Gold pipes (Large, Large Mouthpiece, Long) | 3 | Gold 75, Silver 10, Copper 10, Lead 5 | Pipe pattern |
| Iron Gutter | 1 | Iron 80, Carbon 10, Zinc 10 | Galvanized iron |

### Plants & soil

| Family | Items | Composition | Why |
|---|---|---|---|
| Aubergine | 3 | Carbon 45, Oxygen 35, Hydrogen 15, Manganese 5 | |
| Carrot | 3 | Carbon 45, Oxygen 35, Hydrogen 15, Sodium 5 | Na-rich root veg |
| Cauliflower | 3 | Carbon 45, Oxygen 30, Hydrogen 15, Sulfur 10 | Glucosinolates |
| Chilli | 3 | Carbon 45, Oxygen 30, Hydrogen 15, Nitrogen 10 | Capsaicin |
| Corn | 3 | Carbon 45, Oxygen 35, Hydrogen 15, Phosphorus 5 | |
| Cotton | 3 | Carbon 45, Oxygen 45, Hydrogen 10 | Cellulose |
| Lettuce | 3 | Carbon 40, Oxygen 35, Hydrogen 15, Magnesium 10 | Chlorophyll |
| Onion | 3 | Carbon 42, Oxygen 33, Hydrogen 15, Sulfur 10 | |
| Potato | 3 | Carbon 45, Oxygen 35, Hydrogen 15, Potassium 5 | |
| Pumpkin | 3 | Carbon 45, Oxygen 35, Hydrogen 15, Zinc 5 | Zinc-rich seeds |
| Rice | 3 | Carbon 40, Oxygen 40, Hydrogen 10, Silicon 10 | Silica accumulator |
| Tomato | 3 | Carbon 43, Oxygen 35, Hydrogen 15, Boron 7 | Boron-sensitive crop |
| Turnip | 3 | Carbon 45, Oxygen 33, Hydrogen 15, Calcium 7 | |
| Wheat | 3 | Carbon 45, Oxygen 35, Hydrogen 15, Selenium 5 | Se-rich cereal |
| Wild Grass crop | 1 | Carbon 45, Oxygen 30, Hydrogen 15, Nitrogen 10 | Matches Grass |
| Lavender | 1 | Carbon 55, Oxygen 25, Hydrogen 15, Boron 5 | |
| Sunflower | 1 | Carbon 50, Oxygen 30, Hydrogen 15, **Cesium** 5 | Sunflowers absorb Cs from soil |
| Soil_Grass_Wet | 1 | Carbon 45, Oxygen 30, Hydrogen 20, Nitrogen 5 | |
| Void Liana | 1 | Carbon 45, Oxygen 25, Hydrogen 15, **Tantalum** 15 | "Void" theme |
| Poisoned Roots (`Wood_Poisoned_Roots`, `Soil_Roots_Poisoned`) | 2 | Carbon 50, Oxygen 25, Hydrogen 15, Arsenic 10 | |
| Sadwillow wood (trunk, full, half, stairs, branches, roots) | ~11 | Carbon 55, Oxygen 30, Hydrogen 10, Zinc 5 | Willows accumulate Zn |
| Sadwillow leaves, sapling, hanging vine | 3 | Carbon 50, Oxygen 30, Hydrogen 15, Zinc 5 | |

### Fluids, food, fauna

| Item | Composition | Why |
|---|---|---|
| Fluid_Fire | Oxygen 50, Carbon 30, Hydrogen 15, Nitrogen 5 | Combustion gases |
| Fluid_Tar_Void | Carbon 70, Hydrogen 10, Sulfur 10, **Hafnium** 10 | Matches Tar; void trace |
| Dead_Fish | Carbon 45, Oxygen 25, Nitrogen 20, Sulfur 10 | Decay |
| Food_Fish_Raw_Uncommon | Carbon 50, Oxygen 25, Hydrogen 15, **Iodine** 10 | Base Raw Fish with P swapped for a trace by rarity |
| Food_Fish_Raw_Rare | Carbon 50, Oxygen 25, Hydrogen 15, **Selenium** 10 | |
| Food_Fish_Raw_Epic | Carbon 50, Oxygen 25, Hydrogen 15, **Bromine** 10 | |
| Food_Fish_Raw_Legendary | Carbon 50, Oxygen 25, Hydrogen 15, **Mercury** 10 | Apex-predator bioaccumulation (flavor — easy to change) |
| Deco_Trash_Banana_Peel | Carbon 45, Oxygen 35, Hydrogen 15, Potassium 5 | |

### Furniture, deco, misc

| Item(s) | Composition | Why |
|---|---|---|
| Bookshelf (Left/Middle/Right/Single/base) | Carbon 55, Oxygen 30, Hydrogen 10, Calcium 5 | Wood + paper filler |
| Goblin Bed | Carbon 45, Oxygen 35, Hydrogen 10, Iron 10 | |
| Goblin Brazier | Iron 55, Carbon 25, Sulfur 10, Oxygen 10 | |
| Goblin Candle | Carbon 60, Hydrogen 20, Oxygen 10, Phosphorus 10 | Tallow |
| Veilbound Coffer | Iron 50, Carbon 20, Oxygen 20, **Niobium** 10 | |
| Goblin Door (2) | Carbon 50, Oxygen 30, Iron 15, Manganese 5 | |
| Goblin Ladder, Stool, Table, Sign; Klops sign | Carbon 55, Oxygen 30, Hydrogen 10, Iron 5 | Wood + iron fittings |
| Goblin Loot Pot | Silicon 40, Oxygen 30, Aluminum 20, Iron 10 | Ceramic (matches Dark Temple Jar) |
| Goblin Window | Silicon 45, Oxygen 35, Sodium 10, Calcium 10 | Soda-lime glass |
| Defaced Statue (graffiti) | Silicon 50, Oxygen 30, Iron 10, Lead 10 | Lead paint |
| Bearer of Fire Statue | Silicon 50, Oxygen 30, Calcium 10, Iron 10 | Matches Ancient Statue style |
| Dungeon Checkpoint Statue | Silicon 50, Oxygen 30, Gold 10, Iron 10 | |
| Deepworks Power Generator | Iron 40, Copper 25, Silicon 20, Neodymium 15 | Magnets |
| Runebinder's Lattice (`Bench_Abilities`) | Iron 40, Silicon 25, Gold 20, **Praseodymium** 15 | Nd/Pr magnets |
| Green Build Lightsource | Silicon 40, Oxygen 25, Phosphorus 20, Barium 15 | Matches Blue/Cyan pattern; Ba = green |
| Construction Sign | Carbon 50, Oxygen 30, Hydrogen 10, Titanium 10 | White paint |
| Small Red Flag | Carbon 55, Hydrogen 25, Oxygen 15, Iron 5 | Matches Orange Flag; iron-oxide red |
| Goblin Plushie / Bat Plushie | Carbon 50, Oxygen 30, Hydrogen 10, Nitrogen 10 | Textile + stuffing |
| Halloween Pumpkin Lamp - Green | Carbon 45, Oxygen 30, Hydrogen 15, Barium 10 | Flame colour test: Ba = green |
| Halloween Pumpkin Lamp - Orange | Carbon 45, Oxygen 30, Hydrogen 15, Calcium 10 | Ca = orange |
| Halloween Pumpkin Lamp - Yellow | Carbon 45, Oxygen 30, Hydrogen 15, Sodium 10 | Na = yellow |
| Shiny Scrap Pile | Iron 45, Aluminum 20, Copper 20, Tin 15 | |
| Goblin Boom Barrel | Potassium 30, Oxygen 25, Carbon 25, Sulfur 20 | Black powder |
| Gobliterator Barrel | Potassium 30, Sulfur 25, Magnesium 25, Oxygen 20 | Powder + magnesium flash |
| Claw Marks, Predator Tracks | Silicon 40, Oxygen 35, Aluminum 15, Iron 10 | Disturbed soil |
| Voidheart Bear Trap | Iron 60, Carbon 20, Manganese 10, **Tantalum** 10 | |
| Fire / Overheat graffiti (3) | Carbon 45, Oxygen 30, Hydrogen 15, **Cadmium** 10 | Cadmium red |
| Spider / Skeleton graffiti (3) | Carbon 45, Oxygen 30, Hydrogen 15, Titanium 10 | Titanium white |
| Goblin Paintings (7 + 7 replicas) | Carbon 45, Oxygen 35, Hydrogen 10, + 10 pigment: Breach **Chromium**, Failed Excursions **Lead**, Strange Place **Copper**, Veil of Void **Cobalt**, Traveling Goblin **Antimony**, Required Repair **Zinc**, (Orbis = Veil of Void) | Pigments by colour theme |

## C. Effect on element balance

Newly used (never appeared before): Niobium, Ruthenium, Hafnium, Tantalum, Iridium, Praseodymium, Gadolinium, Dysprosium, Erbium, Holmium, Ytterbium.

Boosted from near-zero: Lithium, Barium, Rubidium, Cesium, Tellurium, Vanadium, Mercury, Antimony, Selenium, Strontium, Bromine, Lead, Cobalt, Cadmium, Tin, Sodium.

Still unused: **Technetium** only. It has no stable natural occurrence, so skipping it is deliberate.

Rare-earth placement: Gadolinium (Kenolite Rock), Dysprosium + Ytterbium (Kenolite Stalactite), Erbium (Peach Brick), Holmium (Ledge Brick), Praseodymium (Runebinder's Lattice).
