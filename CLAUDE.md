# AlchemyMenagerie - Claude Instructions

A Hytale server-side mod by Polgora. The mod centers around analyzing creatures and materials through Alchemy to discover their elemental composition, and befriending/creating creatures.

## Reference Docs
- Hytale modding guides (NPC, UI, world gen, plugin setup): https://hytalemodding.dev/en/docs
- Hytale Java API reference (classes, events, commands): https://raw.githubusercontent.com/GalacticOrgOfDev/HytaleCompleteAPI/main/Java%20ReadMe.md

When writing or modifying Java code, check the API reference above for correct class names, method signatures, and patterns.

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