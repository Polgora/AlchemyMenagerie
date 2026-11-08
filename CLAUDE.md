# AlchemyMenagerie — Claude Instructions

A Hytale server-side mod by Polgora. Players analyze creatures and materials through Alchemy to discover elemental composition, then befriend or create creatures using that knowledge.

## Adding a New Creature

Every creature requires exactly **4 Java registrations**, **4 JSON config files**, **3 lang entries**, and **13+ asset files**. Replace `{RoleName}` with the creature's snake_case identifier (e.g. `Verdant_Sprout`).

### 1 — Java registrations (all in existing files, no new files needed)

**`CreatureRecipeManager.java`** — elemental recipe for the Menagerie Table:
```java
Map<String, Integer> myCreature = new LinkedHashMap<>();
myCreature.put("{Element1}", 60);  // any element name → percentage; values must sum to 100
myCreature.put("{Element2}", 30);
myCreature.put("{Element3}", 10);
RECIPES.add(new CreatureRecipe("Egg_{RoleName}", "{Display Name}", "Common", myCreature));
```
Each creature has its own unique element combination derived from the blocks/materials associated with that creature in the world. Any number of elements can be used as long as percentages sum to 100. Element names match exactly what the Arcanoscope reports (e.g. `"Carbon"`, `"Oxygen"`, `"Magnesium"`, `"Iron"`, etc.).

Rarity controls weighted selection when multiple recipes match the same flask. All five tiers have corresponding Quality JSON files in `Server/Item/Qualities/`:

| Rarity | Roll weight | When to use |
|--------|-------------|-------------|
| `Common` | 100 | Default — most creatures |
| `Uncommon` | 60 | Slightly rarer |
| `Rare` | 30 | Hard to craft |
| `Epic` | 10 | Very hard to craft |
| `Legendary` | 3 | Exceptionally rare |

Element tolerance at the table is ±5% per element (`CreatureRecipeManager.TOLERANCE`).

**`CreatureDietRegistry.java`** — what food items the creature accepts:
```java
m.put("{RoleName}", DietType.HERBIVORE);
// DietType options: HERBIVORE (plants/fruits/grains), CARNIVORE (meat), OMNIVORE (both)
```

**`CreatureBestiaryRegistry.java`** — journal bestiary lore:
```java
register("{RoleName}",
    "Egg_{RoleName}",                   // egg item ID (used as fallback icon)
    "{RoleName}_Icon.png",              // filename under Icons/ModelsGenerated/
    "First lore line.",
    "Second lore line.",
    "Third lore line."
);
```

**`Main.java`** — load the drop table at startup:
```java
CreatureDropManager.load("{RoleName}");
```

### 2 — JSON config files

**`Server/NPC/Roles/Creature/Critter/{RoleName}.json`** — NPC behavior (copy and adapt from `Verdant_Sprout.json`):
- `"Appearance": "{RoleName}"` — links to the model definition
- Do **not** set a `"DropList"` value — creatures never drop items on death by design; drops only happen via the Menagerie Anchor's periodic resource-drop timer (see `AlchemyMenagerie/CreatureDrops/Drop_{RoleName}.json` below). Setting `DropList` here would point at a vanilla `ItemDropList` asset that our drop-config schema doesn't actually populate (it gets silently ignored with an "Unused key(s): Drops" warning at boot) — leaving it unset makes the "no death drops" behavior explicit instead of incidental.
- `"FlockArray": ["{RoleName}"]` — enables flock behavior
- `"NameTranslationKey": "server.npcRoles.{RoleName}.name"`
- Contains the full AI state machine: Idle, Walk, Alerted/Flee, Sleep, interaction instructions

**`Server/Models/Critter/{RoleName}.json`** — 3D model binding:
```json
{
  "Model":     "NPC/Critter/{RoleName}/Models/Model.blockymodel",
  "Texture":   "NPC/Critter/{RoleName}/Models/Texture.png",
  "EyeHeight": H,
  "MinScale":  0.75,
  "MaxScale":  1.25,
  "HitBox": {
    "Min": {"X": -W, "Y": 0.0, "Z": -D},
    "Max": {"X":  W, "Y":  H,  "Z":  D}
  },
  "AnimationSets": { /* Idle, Walk, Run, Laydown, Alerted, Hurt, Sleep, Wake, Death */ }
}
```
`EyeHeight`, `HitBox` dimensions (W/D = half-widths, H = full height), and scale range are all creature-dependent — measure them from the creature's `.blockymodel`. Do not copy Verdant Sprout's values.

**`Server/Item/Items/Creature/Eggs/Egg_{RoleName}/Egg_{RoleName}.json`** — egg item:
```json
{
  "TranslationProperties": {
    "Name":        "server.items.Egg_{RoleName}.name",
    "Description": "server.items.Egg_{RoleName}.description"
  },
  "Quality":    "Common",
  "ItemLevel":  1,
  "Model":      "Items/{RoleName}_Egg.blockymodel",
  "Texture":    "Items/{RoleName}_Egg_Texture.png",
  "Icon":       "Icons/ItemsGenerated/{RoleName}_Egg.png",
  "MaxStack":   16,
  "DropOnDeath": true
}
```

**`AlchemyMenagerie/CreatureDrops/Drop_{RoleName}.json`** — anchor resource drops **only** (creatures never drop on death — see the NPC role note above):
```json
{
  "Drops": [
    { "ItemId": "{ItemId}", "QuantityMin": 1, "QuantityMax": 3, "Chance": 1.00 },
    { "ItemId": "{ItemId}", "QuantityMin": 1, "QuantityMax": 2, "Chance": 0.35 },
    { "ItemId": "{ItemId}", "QuantityMin": 1, "QuantityMax": 1, "Chance": 0.15 }
  ]
}
```
Each creature has up to 3 drops, all creature-dependent — choose items that fit its nature. `Chance` is 0.0–1.0. The first entry is typically a guaranteed common drop (`Chance: 1.00`), with subsequent entries being rarer.

This file lives under `AlchemyMenagerie/`, **not** `Server/` — it's read directly as a classpath resource by `CreatureDropManager`, not through the vanilla asset pipeline. Keeping it out of `Server/` is deliberate: anything under `Server/Drops/` gets auto-classified by the vanilla asset loader as an `ItemDropList` asset, and our `{"Drops": [...]}` schema doesn't match that type's real format (a nested `Container`/`Choice`/`Single` structure), which previously produced a confusing "Unused key(s): Drops" warning at boot for no benefit — this file was never meant to be read by the vanilla pipeline at all.

**Drop quality must not exceed creature rarity:**
- `Common` creature → drops are Common quality only, Uncommon at most for the rarest slot
- `Uncommon` creature → drops up to Rare for the rarest slot
- `Rare` and above → drops up to the matching quality tier

**Choosing drop items — usefulness rule:**
All drops should have real gameplay value. Two categories always qualify:
- **Ingredients** — only use ones that appear in at least one crafting recipe. Check before choosing (see below).
- **Blocks** — always valid regardless of recipes, since players use them for building.

**Finding vanilla item IDs:**
All vanilla items live in the game's `Assets.zip`:
`C:\Users\Malissa\AppData\Roaming\Hytale\install\release\package\game\latest\Assets.zip`

Browse item categories:
```bash
jar tf Assets.zip | grep "Server/Item/Items/" | grep "\.json$" | sed 's|Server/Item/Items/||;s|/.*||' | sort -u
```
List items in a category (e.g. Ingredient):
```bash
jar tf Assets.zip | grep "Server/Item/Items/Ingredient/" | grep "\.json$"
```
Look up display names:
```bash
jar xf Assets.zip "Server/Languages/en-US/server.lang"
grep "MyItemId" Server/Languages/en-US/server.lang
```
Check if an ingredient has crafting uses (extract all server files first via `jar xf Assets.zip`, then):
```bash
grep -rl "MyItemId" /tmp/Server/ | grep -v "MyItemId.json\|server.lang"
```

### 3 — Localization (`Server/Languages/en-US/server.lang`)

```
items.Egg_{RoleName}.name = {Display Name} Egg
items.Egg_{RoleName}.description = A creature egg suffused with ... essence.
npcRoles.{RoleName}.name = {Display Name}
```

### 4 — Asset files

```
Common/NPC/Critter/{RoleName}/
  Models/
    Model.blockymodel
    Texture.png
  Animations/Default/
    Idle.blockyanim
    Walk.blockyanim
    Run.blockyanim
    Laydown.blockyanim
    Alerted.blockyanim
    Hurt.blockyanim
    Sleep.blockyanim
    Wake.blockyanim
    Death.blockyanim

Common/Items/
  {RoleName}_Egg.blockymodel
  {RoleName}_Egg_Texture.png

Icons/ItemsGenerated/
  {RoleName}_Egg.png          — inventory icon for the egg item

Icons/ModelsGenerated/
  {RoleName}_Icon.png         — bestiary icon in the Menagerie Journal
```

### How the egg → creature hatch works

`NestManager` derives the NPC role by stripping `Egg_` from the egg item ID: `"Egg_Verdant_Sprout"` → spawns `"Verdant_Sprout"`. No extra code needed as long as the naming convention is followed.

### Checklist

- [ ] `CreatureRecipeManager` — element recipe + rarity
- [ ] `CreatureDietRegistry` — diet type
- [ ] `CreatureBestiaryRegistry` — lore entry
- [ ] `Main.setup()` — `CreatureDropManager.load("{RoleName}")`
- [ ] `Server/NPC/Roles/Creature/Critter/{RoleName}.json`
- [ ] `Server/Models/Critter/{RoleName}.json` (hitbox from model)
- [ ] `Server/Item/Items/Creature/Eggs/Egg_{RoleName}/Egg_{RoleName}.json`
- [ ] `AlchemyMenagerie/CreatureDrops/Drop_{RoleName}.json`
- [ ] 3 lang entries (egg name, egg description, creature name)
- [ ] Model + texture
- [ ] 9 animation files
- [ ] Egg model + texture + icon
- [ ] Creature bestiary icon

---

## Inspecting the Hytale API

When uncertain about a class, method, or signature, inspect the server JAR directly. **Do not hardcode the JAR path** — the version and cache hash change on every game update (see API Migration Notes below). Find it fresh each time:

```bash
find ~/.gradle/caches/modules-2/files-2.1/com.hypixel.hytale/Server -name "Server-*.jar"
```

Use `jar tf <path>` to list contents, `jar xf <jar> <class/path>` + `javap <ClassFile.class>` to inspect method signatures, before writing code that depends on them. The most reliable check of all is just running `./gradlew build` — it compiles against whatever `useVersion("latest")` resolves right now, so any drift from this doc surfaces immediately as compiler errors.

### API Migration Notes

Hytale's versioning scheme changed from date+commit-hash (e.g. `2026.02.19-1a311a592`) to semver (`0.7.6` as of 2026-11-07). A build after a long gap between sessions should be expected to fail — the server API moves fast and this doc will lag behind it. When that happens, treat the steps below as the starting checklist, then record what you find here so the next session doesn't have to re-derive it.

**Confirmed breaking changes as of Server 0.7.6** (found by diffing a failed `./gradlew build` against the jar via `javap`):

| Old (pre-0.7.6) | New / status |
|---|---|
| `com.hypixel.hytale.math.vector.Vector3d` / `Vector3f` | Package removed entirely. Use `org.joml.Vector3d` / `org.joml.Vector3f` (joml is bundled in the jar; `org.joml.Vector3i` was already in use and is unaffected). |
| `com.hypixel.hytale.server.core.inventory.InventoryChangeEvent` | Moved to `com.hypixel.hytale.server.core.event.events.ecs.InventoryChangeEvent`. |
| `World.getBlockType(int,int,int)` | Removed from `World`. Not yet relocated — `World` has no direct block-read/write methods anymore; likely moved behind `World.getChunkStore()` (`ChunkStore`). Needs further investigation before migrating callers (`ArcanoscopePacketWatcher`, `ChestLootEvent`, `Main.java`). |
| `World.breakBlock(int,int,int,int)` | Removed from `World`, same as above — likely also moved to `ChunkStore`. Affects `ArcanoscopePage`. |
| `Role.getStateSupport()` | Removed. `Role` no longer exposes a `StateSupport` accessor directly — NPC role internals were restructured around `ExecutionSupport` (`Role.acquireExecutionSupport(...)`). The `support.StateSupport` class itself still exists in the jar, just not reachable this way. Affects `CreatureLureHintSystem`, `ActionOpenCreatureInfo`. |
| `Role.setMarkedTarget(String, Ref)` | Still exists but signature changed — now takes an extra `ComponentAccessor` parameter: `setMarkedTarget(Ref, ComponentAccessor, String, Ref)`. Affects `CreatureLeashTrackingSystem`. |
| `Inventory.getItemInHand()`, `getUtilityItem()`, `getCombinedArmorHotbarUtilityStorage()`, `getActiveHotbarItem()`, `getActiveHotbarSlot()`, `getHotbar()` | Still present but compiler-flagged `[removal]` (deprecated, will be deleted in a future update). Not yet broken, but migrate opportunistically. |

**Not yet investigated:** `ActionOpenCreatureInfo` also fails with "method does not override or implement a method from a supertype" — the NPC builder-action base class signature likely changed too; needs its own look before fixing.

---

## Build

```
./gradlew build
```

Output: `AlchemyMenagerie-{version}.jar`. Version is read automatically from `manifest.json` via `build.gradle.kts`. Never hardcode the version anywhere else.

---

## Project Structure

```
src/main/java/me/Polgora/
  Main.java                        — Entry point. Extends JavaPlugin. All registration in setup().
  commands/                        — One class per command, each extends AbstractCommand
  events/                          — Event handlers and ECS systems, registered in Main.setup()
  pages/                           — InteractiveCustomUIPage subclasses (one per UI screen)
  npc/                             — NPC builder action types (BuilderActionOpenCreatureInfo)
  interactions/                    — Custom Interaction types (ArcanoscopeParticleInteraction)
  *Manager.java / *Registry.java   — Singleton managers with static init() + persistent state

src/main/resources/
  manifest.json                    — Mod metadata and version
  Server/Item/Items/               — Item JSON definitions (one folder per item)
  Server/Item/Qualities/           — Quality tier definitions
  Server/Languages/en-US/server.lang — All display strings
  Server/Particles/                — Particle system definitions
  Common/Items/                    — 3D models (.blockymodel) and textures
  Common/UI/Custom/                — All .ui page files
```

---

## API Quick Reference

### Plugin Entry Point

```java
public class Main extends JavaPlugin {
    public Main(@Nonnull JavaPluginInit init) { super(init); }

    @Override protected void setup() { /* register everything here */ }
    @Override protected void shutdown() { /* cleanup */ }
}
```

### Commands

Extend `AbstractCommand`. Return `CompletableFuture.completedFuture(null)` always.

```java
public class MyCommand extends AbstractCommand {
    public MyCommand() { super("name", "description"); }

    @Nullable
    @Override
    protected CompletableFuture<Void> execute(@Nonnull CommandContext context) {
        if (!context.isPlayer()) {
            context.sendMessage(Message.raw("Players only."));
            return CompletableFuture.completedFuture(null);
        }
        String playerId = context.sender().getUuid().toString();
        context.sendMessage(Message.raw("Hello!"));
        return CompletableFuture.completedFuture(null);
    }
}
```

Register in `Main.setup()`:
```java
this.getCommandRegistry().registerCommand(new MyCommand());
```

Resolving i18n display names from a block/item ID:
```java
String key  = "server.items." + itemId + ".name";
String name = I18nModule.get().getMessage(I18nModule.DEFAULT_LANGUAGE, key);
return (name != null && !name.isBlank()) ? name : itemId;
```

### Events (global, static methods)

```java
public class MyEvent {
    public static void onPlayerReady(PlayerReadyEvent event) {
        Player player = event.getPlayer();
        player.sendMessage(Message.raw("Hello " + player.getDisplayName()));
    }
}
```

Register in `Main.setup()`:
```java
this.getEventRegistry().registerGlobal(PlayerReadyEvent.class, MyEvent::onPlayerReady);
// With priority:
this.getEventRegistry().registerGlobal(EventPriority.LAST, LoadAssetEvent.class, MyEvent::onLoad);
```

### ECS Ticking Systems

Used for per-tick logic (resource drops, bond erosion, leash tracking, etc.).

```java
public class MySystem extends EntityTickingSystem<EntityStore> {
    @Override
    public Query<EntityStore> getQuery() { return Query.any(); }

    @Override
    public void tick(float dt, int index, ArchetypeChunk<EntityStore> chunk,
                     Store<EntityStore> store, CommandBuffer<EntityStore> cmd) {
        NPCEntity npc = chunk.getComponent(index, NPCEntity.getComponentType());
        if (npc == null) return;
        // ...
    }
}
```

Register in `Main.setup()`:
```java
this.getEntityStoreRegistry().registerSystem(new MySystem());
```

### Packet Watchers

For inspecting inbound packets (e.g. ArcanoscopePacketWatcher reads block-scan interactions):

```java
public class MyWatcher implements PlayerPacketWatcher {
    @Override
    public void accept(PlayerRef playerRef, Packet packet) {
        if (!(packet instanceof MyPacketType p)) return;
        // ...
    }
}
```

Register:
```java
PacketAdapters.registerInbound(new MyWatcher());
```

### UI Pages (InteractiveCustomUIPage)

```java
public class MyPage extends InteractiveCustomUIPage<MyPage.MyAction> {
    public static class MyAction {
        public static final BuilderCodec<MyAction> CODEC = /* ... */;
        public String action;
    }

    @Override
    public void build(@Nonnull Ref<EntityStore> ref, @Nonnull UICommandBuilder cmd,
                      @Nonnull UIEventBuilder evt, @Nonnull Store<EntityStore> store) {
        cmd.append("MyUI.ui");
        cmd.set("#SomeLabel.Text", "Hello");
        evt.addEventBinding(CustomUIEventBindingType.Activating, "#MyButton",
                EventData.of("Action", "my_action"));
    }

    @Override
    public void handleDataEvent(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store,
                                @Nonnull MyAction data) {
        switch (data.action) {
            case "my_action" -> { /* ... */ sendRefresh(true); }
        }
    }
}
```

Register a page supplier in `Main.setup()`:
```java
OpenCustomUIInteraction.registerSimple(this, MyPage.class, "MySupplier",
        playerRef -> new MyPage(playerRef));
```

### Persistent Managers

All managers follow the same init+Path pattern. Data lives under the plugin folder:

```java
public class MyManager {
    private static Path dataDir;

    public static void init(Path dir) {
        dataDir = dir;
        try { Files.createDirectories(dataDir); } catch (IOException e) { /* log */ }
        // load existing state from disk
    }
}
```

Initialised in `Main.setup()`:
```java
MyManager.init(getFile().getParent().resolve("AlchemyMenagerie").resolve("mydata"));
```

### NPC Actions

Custom NPC builder action types are registered by name:
```java
NPCPlugin.get().registerCoreComponentType("OpenCreatureInfo", BuilderActionOpenCreatureInfo::new);
```

### Custom Interaction Types

```java
Interaction.CODEC.register("ArcanoscopeParticle", ArcanoscopeParticleInteraction.class,
        ArcanoscopeParticleInteraction.CODEC);
```

### Logging

```java
private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
LOGGER.at(java.util.logging.Level.INFO).log("Message %s", value);
LOGGER.at(java.util.logging.Level.WARNING).log("Warning %s", value);
```

### Key Classes in Use

| Purpose | Class |
|---------|-------|
| Plugin base | `com.hypixel.hytale.server.core.plugin.JavaPlugin` |
| Command base | `com.hypixel.hytale.server.core.command.system.AbstractCommand` |
| Command context | `com.hypixel.hytale.server.core.command.system.CommandContext` |
| Player entity | `com.hypixel.hytale.server.core.entity.entities.Player` |
| NPC entity | `com.hypixel.hytale.server.npc.entities.NPCEntity` |
| Item stack | `com.hypixel.hytale.server.core.inventory.ItemStack` |
| Item container | `com.hypixel.hytale.server.core.inventory.container.ItemContainer` |
| World | `com.hypixel.hytale.server.core.universe.world.World` |
| Universe | `com.hypixel.hytale.server.core.universe.Universe` |
| Entity store | `com.hypixel.hytale.server.core.universe.world.storage.EntityStore` |
| ECS store | `com.hypixel.hytale.component.Store` |
| Packet adapters | `com.hypixel.hytale.server.core.io.adapter.PacketAdapters` |
| Packet watcher | `com.hypixel.hytale.server.core.io.adapter.PlayerPacketWatcher` |
| I18n | `com.hypixel.hytale.server.core.modules.i18n.I18nModule` |
| Logger | `com.hypixel.hytale.logger.HytaleLogger` |
| Notifications | `com.hypixel.hytale.server.core.util.NotificationUtil` |
| Vector (block pos) | `org.joml.Vector3i` |
| Vector (entity pos) | `org.joml.Vector3d` (was `com.hypixel.hytale.math.vector.Vector3d` pre-0.7.6 — see API Migration Notes) |

### Events in Use (do not use deprecated alternatives)

| Event | Notes |
|-------|-------|
| `PlayerReadyEvent` | Player fully loaded into world |
| `PlayerConnectEvent` | Player connects to server |
| `PlayerDisconnectEvent` | Player leaves |
| `PlayerChatEvent` | Chat message (IAsyncEvent) |
| `EntityRemoveEvent` | Any entity removed from world |
| `AllWorldsLoadedEvent` | Server startup complete |
| `LoadAssetEvent` | Asset loading lifecycle |
| `UseBlockEvent` | Block right-click — **use this, not deprecated PlayerInteractEvent** |
| `BreakBlockEvent` | Block broken (EcsEvent, cancellable) |
| `PlaceBlockEvent` | Block placed (EcsEvent, cancellable) |
| `PrefabPasteEvent` | Prefab pasted in editor (EcsEvent, cancellable) |
| `InteractivelyPickupItemEvent` | Player picks up item (EcsEvent, cancellable) |

**Never use:** `PlayerInteractEvent`, `LivingEntityUseBlockEvent`, `PlayerCraftEvent`, `PrepareUniverseEvent` — all deprecated.

---

## Item JSON Conventions

Location: `Server/Item/Items/{Category}/{ItemName}/{ItemName}.json`

```json
{
  "TranslationProperties": {
    "Name": "server.items.MyItem.name",
    "Description": "server.items.MyItem.description"
  },
  "Categories": ["Items.Ingredients"],
  "Model": "Resources/MyFolder/MyItem.blockymodel",
  "Texture": "Resources/MyFolder/MyItem.png",
  "Quality": "Common",
  "ItemLevel": 10,
  "PlayerAnimationsId": "Item",
  "Icon": "Icons/ItemsGenerated/MyItem.png",
  "Tags": {
    "Type": ["Ingredient"],
    "Family": ["Crystal"]
  },
  "ResourceTypes": [{ "Id": "Crystal_Fragment" }],
  "ItemSoundSetId": "ISS_Items_Gems",
  "DropOnDeath": true
}
```

- `Texture`, `Model`, `Icon` paths are relative to `Common/`
- Every key in `TranslationProperties` must have a corresponding entry in `server.lang`
- `Quality` values: `Common`, `Rare`, `Legendary` (see `Server/Item/Qualities/`)

---

## Localization (`server.lang`)

File: `Server/Languages/en-US/server.lang`

```
items.MyItem.name = My Item
items.MyItem.description = This item does something.

benchCategories.alchemyMenagerie.name = Alchemy Menagerie
npcRoles.Verdant_Sprout.name = Verdant Sprout
resourceType.Crystal_Fragment.name = Any Crystal Shard
interactionHints.creature_info = Press [{key}] to view info
```

- Item keys: `items.{ItemId}.name` and `items.{ItemId}.description`
- In JSON `TranslationProperties`, reference as `server.items.{ItemId}.name` (prefixed with `server.`)
- Every item JSON that declares a `Name` or `Description` translation key **must** have a matching entry here or it will display as untranslated in-game

---

## UI Style Guide

All journal/tool UI files live in `Common/UI/Custom/` and follow these conventions.

### File Structure

```
$C = "Common.ui";

$C.@PageOverlay {}

$C.@Container {
    Anchor: (Width: 520, Height: 405);

    #Title {
        Label {
            Text: "MY PAGE TITLE";
            Style: (...$C.@DefaultLabelStyle, HorizontalAlignment: Center,
                    VerticalAlignment: Center, RenderBold: true);
        }
    }

    #Content {
        Group {
            LayoutMode: Top;
            FlexWeight: 1;     // required — makes content fill #Content's full height

            // ... content elements ...

            Group { FlexWeight: 1; }  // spacer — pushes footer to bottom of UI

            // ... separator + pagination/close always at bottom ...
        }
    }
}

$C.@BackButton {}
```

### Colors

| Use | Hex |
|-----|-----|
| Panel/button background (default) | `#1a2840` |
| Button hovered | `#2a4870` |
| Button pressed | `#122038` |
| Active/selected tab or highlight | `#2a4870` bg + `#c9a050` text |
| Gold accent (active label, highlight button hover) | `#c9a050` |
| Gold button pressed | `#a08040` |
| Primary body text | `#c0d0e0` |
| Muted/secondary text | `#6a7a8a` |
| Lore/flavour text (purple) | `#a080c0` |
| Element/magic text (purple) | `#c890f0` |
| Success/positive text | `#60d080` |
| Warning/negative text | `#c06070` |
| Drop/item text | `#90a0b0` |
| Separator line | `#2b3542` (via `$C.@ContentSeparator`) or `#4a5a6a` for higher contrast |
| Footer button background | `#1a2530` |

### Standard Button (three-state)

```
ItemSlotButton #MyButton {
    Anchor: (Width: 160, Height: 30);
    Style: (Default: (Background: #1a2530),
            Hovered:  (Background: #c9a050),
            Pressed:  (Background: #a08040));
    Label {
        Anchor: (Full: 0);
        HitTestVisible: false;
        Style: (FontSize: 16, TextColor: #c0d0e0,
                HorizontalAlignment: Center, VerticalAlignment: Center);
        Text: "Click Me";
    }
}
```

### Tab Bar Pattern

Active tab: `#2a4870` bg, `#c9a050` text, `RenderBold: true`
Inactive tab: `#1a2840` bg, `#6a7a8a` text, no bold

```
Group {
    LayoutMode: CenterMiddle;
    Anchor: (Height: 30, Bottom: 4);
    Group {
        LayoutMode: Left;
        Anchor: (Width: 496, Height: 30);

        ItemSlotButton #TabXButton {
            Anchor: (Width: 96, Height: 30);
            Style: (Default: (Background: #2a4870), Hovered: (Background: #3a5880),
                    Pressed: (Background: #1a3060));
            Label { ... Text: "Tab"; Style: (... TextColor: #c9a050, RenderBold: true); }
        }
        // inactive tabs use #1a2840 / #6a7a8a
    }
}
```

### Slot Row Pattern

```
Group {
    LayoutMode: CenterMiddle;
    Anchor: (Height: 38, Bottom: 2);
    ItemSlotButton #Slot0Button {
        Anchor: (Left: 8, Right: 8, Full: 0);
        Style: (Default: (Background: #1a2840),
                Hovered:  (Background: #2a4870),
                Pressed:  (Background: #122038));
        Label #Slot0Label {
            Anchor: (Full: 0);
            Padding: (Horizontal: 12);
            HitTestVisible: false;
            Style: (FontSize: 16, TextColor: #c0d0e0, VerticalAlignment: Center);
            Text: "";
        }
    }
}
```

### Separator

```
$C.@ContentSeparator {
    Anchor: (Top: 8, Bottom: 8);
}
```

For a more visible separator (e.g. between sections with similar backgrounds):
```
Group {
    Anchor: (Height: 1, Bottom: 10);
    Background: (Color: #4a5a6a);
}
```

### Pagination Footer

Always placed after `Group { FlexWeight: 1; }` so it anchors to the bottom:

```
Group { FlexWeight: 1; }

$C.@ContentSeparator {
    Anchor: (Top: 8, Bottom: 8);
}

Group {
    LayoutMode: CenterMiddle;
    Anchor: (Height: 28, Bottom: 6);
    Group {
        LayoutMode: Left;
        Anchor: (Width: 300, Height: 28);
        ItemSlotButton #PrevButton { Anchor: (Width: 80, Height: 28); ... }
        Label #PageLabel { Anchor: (Width: 140, Height: 28); ... }
        ItemSlotButton #NextButton { Anchor: (Width: 80, Height: 28); ... }
    }
}

Group {
    LayoutMode: CenterMiddle;
    Anchor: (Height: 34);
    ItemSlotButton #CloseButton { Anchor: (Width: 160, Height: 30); ... }
}
```

### Layout Modes Available

`Top`, `Bottom`, `Left`, `Right`, `CenterMiddle`, `Middle`, `Center`, `Full`, `TopScrolling`, `BottomScrolling`, `LeftCenterWrap`

Use `FlexWeight: 1` on a child of any flow layout (`Top`, `Left`, etc.) to make it absorb remaining space.

### Standard Container Widths

| Page type | Width |
|-----------|-------|
| Standard journal page | 520 |
| Discoveries (wider, has filter row) | 640 |
| Recipes Failed (wider) | 640 |
| Detail pages | 520 |