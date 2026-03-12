# AlchemyMenagerie - Claude Instructions

A Hytale server-side mod by Polgora. The mod centers around analyzing creatures and materials through Alchemy to discover their elemental composition, and befriending/creating creatures.

## Reference Docs
- Hytale modding guides (NPC, UI, world gen, plugin setup): https://hytalemodding.dev/en/docs
- Hytale Java API reference (classes, events, commands): https://raw.githubusercontent.com/GalacticOrgOfDev/HytaleCompleteAPI/main/Java%20ReadMe.md

When writing or modifying Java code, check the API reference above for correct class names, method signatures, and patterns.

## Hytale API Quick Reference

### Plugin & Commands
- `com.hypixel.hytale.server.core.plugin.JavaPlugin` — base class for the mod
- `com.hypixel.hytale.server.core.plugin.JavaPluginInit` — passed to plugin constructor
- `com.hypixel.hytale.server.core.command.AbstractCommand` — base class for commands
- `com.hypixel.hytale.server.core.command.CommandRegistry` — registers commands
- `com.hypixel.hytale.server.core.command.CommandSender` — player or console sender
- `com.hypixel.hytale.server.core.command.CommandManager` — manages command execution

### Events
- `com.hypixel.hytale.server.core.event.events.player.PlayerReadyEvent`
- `com.hypixel.hytale.server.core.event.events.player.PlayerChatEvent`
- `com.hypixel.hytale.server.core.event.events.player.PlayerInteractEvent`
- `com.hypixel.hytale.server.core.event.events.server.PlayerConnectEvent`
- `com.hypixel.hytale.server.core.event.events.server.PlayerDisconnectEvent`
- `com.hypixel.hytale.server.core.event.events.ecs.BreakBlockEvent`
- `com.hypixel.hytale.server.core.event.events.ecs.PlaceBlockEvent`
- `com.hypixel.hytale.server.core.event.events.entity.EntityRemoveEvent`
- `com.hypixel.hytale.server.core.event.events.asset.LoadAssetEvent`

### Entities & Players
- `com.hypixel.hytale.server.core.entity.entities.player.Player` — connected player
- `com.hypixel.hytale.server.core.entity.EntitySnapshot` — immutable snapshot of entity state
- `com.hypixel.hytale.server.core.entity.BlockEntity` — block-tied entity base class
- `com.hypixel.hytale.server.core.entity.group.EntityGroup` — logical grouping of entities
- `com.hypixel.hytale.server.core.entity.interaction.InteractionChain` — multi-step interactions
- `com.hypixel.hytale.server.core.universe.world.entity.EntityStore` — manages all world entities

### World & Blocks
- `com.hypixel.hytale.server.core.universe.world.World` — world state, entities, chunks, players
- `com.hypixel.hytale.server.core.universe.world.meta.BlockState` — block type and metadata
- `com.hypixel.hytale.server.core.universe.world.block.BlockChunk` — chunk block data
- `com.hypixel.hytale.server.core.universe.world.block.BlockAccessor` — cross-chunk block access
- `com.hypixel.hytale.server.core.universe.world.WorldConfig` — world configuration
- `com.hypixel.hytale.server.core.universe.world.path.WorldPath` — paths with waypoints
- `com.hypixel.hytale.server.core.universe.world.spawn.ISpawnProvider` — spawn point interface

### Inventory & Items
- `com.hypixel.hytale.server.core.inventory.ItemStack` — item stack with metadata/durability
- `com.hypixel.hytale.server.core.inventory.container.ItemContainer` — item slot collection
- `com.hypixel.hytale.server.core.inventory.ItemContext` — source/destination during transfers
- `com.hypixel.hytale.server.core.inventory.MaterialQuantity` — material resource quantity
- `com.hypixel.hytale.server.core.inventory.ResourceQuantity` — generic resource quantity
- `com.hypixel.hytale.server.core.inventory.transaction.MoveTransaction` — item movement between containers
- `com.hypixel.hytale.server.core.inventory.transaction.ItemStackTransaction` — add/remove operations

### Assets & Components
- `com.hypixel.hytale.server.core.asset.HytaleAssetStore` — central asset store
- `com.hypixel.hytale.server.core.modules.asset.AssetModule` — asset lifecycle management
- `com.hypixel.hytale.server.core.asset.util.AssetUtil` — asset utility methods
- `com.hypixel.hytale.component.Store` — entity data storage and operations
- `com.hypixel.hytale.component.ComponentAccessor` — generic component data access
- `com.hypixel.hytale.component.AddReason` — enum for entity addition reasons

### Permissions & Cosmetics
- `com.hypixel.hytale.server.core.permission.HytalePermissions` — permission management
- `com.hypixel.hytale.server.core.permission.PermissionHolder` — individual permission holder
- `com.hypixel.hytale.server.core.modules.cosmetic.CosmeticsModule` — player appearance
- `com.hypixel.hytale.server.core.entity.entities.player.cosmetic.PlayerSkin`
- `com.hypixel.hytale.server.core.entity.entities.player.cosmetic.Emote`

### Networking
- `com.hypixel.hytale.server.core.io.adapter.PacketFilter` — server-level packet filtering
- `com.hypixel.hytale.server.core.io.adapter.PlayerPacketFilter` — per-player packet filtering
- `com.hypixel.hytale.server.core.io.adapter.PacketWatcher` — read-only packet monitoring
- `com.hypixel.hytale.server.core.io.adapter.PacketAdapters` — factory for packet adapters
- `com.hypixel.hytale.server.core.io.NetworkSerializable` — interface for network transmission
- `com.hypixel.hytale.server.core.io.NetworkSerializer` — network serialization
- `com.hypixel.hytale.server.core.io.ProtocolVersion` — protocol version enum

### Utilities
- `com.hypixel.hytale.server.core.util.BsonUtil` — BSON document utilities
- `com.hypixel.hytale.server.core.util.ConfigUtil` — config file loading/management
- `com.hypixel.hytale.logger.HytaleLogger` — SLF4J-compatible plugin logger
- `org.joml.Vector3i` — integer 3D vectors (block positions)
- `org.joml.Vector3d` — double-precision 3D vectors (entity positions)
- `org.joml.Vector3f` — float 3D vectors (rotations, directions)

## Build
```
./gradlew build
```
Output JAR is named `AlchemyMenagerie-{version}.jar`. Version is read from `manifest.json` automatically via `build.gradle.kts`.

## Project Structure
- `src/main/java/me/Polgora/` — Java plugin code
  - `Main.java` — Plugin entry point, extends `JavaPlugin`. Register commands and events in `setup()`
  - `commands/` — One class per command, extends `AbstractCommand`
  - `events/` — Static event handler classes, registered in `Main.setup()`
- `src/main/resources/`
  - `manifest.json` — Mod metadata including version (IMPORTANT: always update version here)
  - `Server/Item/Items/` — Item JSON definitions
  - `Server/Item/Qualities/` — Item quality definitions (Common, Rare, Legendary, etc.)
  - `Server/Languages/en-US/server.lang` — All display strings (names, descriptions)
  - `Server/Particles/` — Particle system and spawner definitions
  - `Common/Items/` — 3D models (`.blockymodel`) and textures

## Conventions

### Java
- Package root: `me.Polgora`
- Commands: extend `AbstractCommand`, registered in `Main.setup()` via `getCommandRegistry().registerCommand()`
- Events: static handler methods, registered via `getEventRegistry().registerGlobal(EventClass.class, Handler::method)`
- Use `Message.raw()` for simple player messages

### Item JSON
- Place under `Server/Item/Items/{Category}/{ItemName}/{ItemName}.json`
- Required fields: `Texture`, `Model`, `Icon`, `Quality`, `Parent`, `TranslationProperties`
- Texture/Model/Icon paths are relative to `Common/`
- Localization keys in `TranslationProperties` must exist in `server.lang`

### Localization
- All item names and descriptions go in `Server/Languages/en-US/server.lang`
- Key format: `items.{ItemName}.name` and `items.{ItemName}.description`
- In `server.lang`, reference keys are prefixed with `server.` (e.g., `server.items.Foo.name`)

### Version
- Version is defined only in `manifest.json` under the `"Version"` key
- Never hardcode the version anywhere else