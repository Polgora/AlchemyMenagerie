# TODO

Open items only, ordered by importance: fixing and finishing what's already in the game comes first, then new features, then future content.

## 1. Bugs & Broken Behavior

## 2. Verify In-Game (already built, not yet confirmed)

1. Wild Ember wanders/settles to Idle once you're more than ~10 blocks away, and doesn't flee.
2. Tier-reset leash fix: after `/bondset`, a creature with no anchor stays put instead of walking away ~20s later (re-check on Sprout too).
3. `Flinch` plays when a Wild Sprout breaks into a flee (if cut off by the clear-Status action, move the trigger later in Flee).
4. `Curious` plays once per food offer (including the Alerted-state follow branch) and replays after the food is put away.
5. `Eat` plays when the owner feeds a creature from the info page and when it eats thrown food (a diet-matching item dropped on the ground, wild or owned), and at no other time.
6. (Needs a second player) Devoted Sprout returns to following within ~2-3s after being scared by a stranger.
7. (Needs a second player) Owned Ember warns strangers at about 3.5 blocks instead of 5.
8. Ember Wyrmling `Curious` and `Eat` animations now exist: confirm they play on food-follow and on feeding / thrown food.

## 3. Finish & Improve Existing Features

1. Add a close ("X") button to the UI menus (journal pages have a footer Close button; Arcanoscope, Flask Pouch, Nest, Table, Result, Whistle, Anchor and Creature Info pages have none).
2. Redesign the Discoveries tab: a simple clickable list of plain item names (e.g. "Dirt") that opens a sub-page with that item's detail info (element composition / description). Page width 640.
3. Add a UI button/shortcut to open the Menagerie Journal (currently only via the physical book item; the `/journal` command just prints a chat list of scanned items, it doesn't open the UI).
4. Lower the movement margin when scanning (`LOOK_THRESHOLD` in `ArcanoscopePacketWatcher`).
5. Make the in-game speed multiplier affect mod timers (hatch time in `NestManager`, bond/feed timers, anchor leash-tracking intervals, etc.).
6. Runebound Whistle: change how creatures of the same species are listed on the species-select page (specifics TBD).
7. Ember Wyrmling: replace the vanilla `Angry` particle stop-gap (spawned when the posture starts and every `PostureParticleInterval` = 3s while it is held); the custom posture animations and hold/exit logic are done and verified.
8. Replace the vanilla placeholder particles in `CreatureTemperament` with custom ones: Bold low rumble/stomp, Curious sparkle/"!", Playful bouncy colorful burst, Aloof rare subtle Devoted cue.
9. Add temperament sounds and hook them into the role JSON: Timid startle, Bold warn growl, Playful chime (optional).

## 4. Decisions Needed

1. Bonded Sprout step-toward-owner: it now lingers (no wander/nap) while the owner stands still nearby. Acceptable, or shrink the range / lower the linger weight / change `STILL_AFTER_MS`?
2. Is the vanilla `Angry` emote acceptable as a stop-gap for Ember's warning, or should it do nothing until a custom effect exists?
3. Aloof Devoted: looser/slower follow than other classes, or the one tier where aloofness breaks with a rare affection cue (or both)?
4. Which species to make first for Curious, Aloof and Playful (suggestions: Volt Strider / Duskwing Moth = Curious, Frostveil Stalker / Quakeback Tortoise = Aloof, Glimmerfin Ray / Zephyr Sprite = Playful)?

## 5. Unfinished Behavior For Existing Creatures

1. Timid (Sprout) Wild: startle on sudden nearby movement or loud sound (no sensor identified); prefers idling in shaded/dense plant cover.
2. Timid Tame: prefers the owner's dropped food over a stranger's (no way found to tell whose item is whose).
3. Timid Devoted: trailing gait at heel distance, interposing between owner and a threat.
4. Bold (Ember): owner-escort positioning (ahead of owner), interposing against hostile mobs or posturing strangers, quicker warning toward strangers approaching the owner.
5. Optional animations: Ember confident-gait Walk for Bonded; Sprout trailing-gait Walk for Devoted.

## 6. New Features

1. Add loot items that help players discover bestiary/creature recipes (chest loot currently only adds Alchemy Flask and Menagerie Journal).
2. Add goggles that show the elements of scanned materials.
3. Add droppers that remove all of one essence from a flask.
4. Use periodic table icons for all elements in the UI.

## 7. Cleanup & Maintenance

1. Set `CreatureStateDebugSystem.ENABLED = false` before a release build.
2. Lower `ChestLootEvent.EnrichmentSystem`'s container-open log line from INFO to FINE/debug.
3. Migrate off the deprecated (`[removal]`) `Inventory` methods: `getItemInHand`/`getUtilityItem` in `CreatureLureHintSystem`, `getActiveHotbarItem` in `CreatureInfoPage`, `getCombinedArmorHotbarUtilityStorage` in `MenagerieNestBreakEvent`, plus the suppression in `PouchManager`.
4. Move `Verdant_Sprout_Icon.png` from `Common/UI/Custom/` to `Common/Icons/ModelsGenerated/` like Ember's (check the Sprout bestiary icon still renders after the move).
5. Update the stale "API Migration Notes" in `CLAUDE.md` (several listed breakages, e.g. `World.getBlockType`, are now handled via `WorldBlockUtil`).
6. Fix stale "TODO.md item 13" / "see TODO.md" references in `CLAUDE.md`, `CreatureTemperament.java`, `docs/TEMPERAMENT_ROLE_TEMPLATES.md` and the Ember role JSON comment; the old numbered items no longer exist.
7. `_Known` variants: add them for the 14 skipped portal blocks (`Portal_Device`, hub portals, `PortalKey_*`, etc.) if wanted.
8. `_Known` variants: delete or keep stale `Furniture_Ancient_Chest_Large_Treasure_Known` (no matching vanilla item).
9. `_Known` variants: re-audit after major game updates, since the files are copies of vanilla JSON and can drift.

## 8. Future Temperament Classes (no species exists yet)

Drafts for Curious, Aloof and Playful role JSON are in `docs/TEMPERAMENT_ROLE_TEMPLATES.md`.

1. Curious: approach/investigate unfamiliar players, items and newly placed blocks; Tame seeks out owner on new actions; Bonded head-tracks owner activity; Devoted detours to inspect things; quick once-over of new people. Needs a sparkle/"!" particle.
2. Aloof: ignores players at all tiers; minimal reaction to owner; loose Bonded recentering; Devoted tone per the decision above; strangers always ignored.3. Playful: Wild approach/dart/circle and flocking; Tame hops and circling near owner; Bonded bouncy movement and play with other Bonded creatures; Devoted bursts ahead then circles back, excited greeting on owner's return; open question on caution toward strangers near a rival/threat. Needs `Play` and `Hop` animations.

## 9. Future Creatures

Each needs the full new-creature pipeline from CLAUDE.md (Java registrations, role/model/egg/drop JSON, lang entries, model/animation/icon assets). Design rule: every species has its own distinct function, and no two share one.

1. **Glimmerfin Ray** — Oxygen 65 / Hydrogen 25 / Phosphorus 10. Living lantern: lights the pen, portable light at Devoted. Playful.
2. **Thornhide Brute** — Carbon 48 / Oxygen 32 / Calcium 20. Guardian: hostile mobs in the anchor radius target it first; possible pack animal at Bonded+. Bold.
3. **Volt Strider** — Copper 40 / Silicon 35 / Neon 25. Bench booster: speeds nearby smelting/alchemy; shocks close mobs. Curious.
4. **Frostveil Stalker** — Oxygen 70 / Hydrogen 20 / Nitrogen 10. Preservation aura: food/potions in nearby chests spoil slower; frost protection for owner. Aloof.
5. **Duskwing Moth** — Carbon 55 / Nitrogen 30 / Sulfur 15. Danger detector: gathers near dark spots/spawn risks; night-themed drops. Curious.
6. **Quakeback Tortoise** — Silicon 60 / Oxygen 25 / Aluminum 15. Stone breaker: slowly loosens ore from nearby stone; stomps knock back enemies. Aloof.
7. **Radiant Bloomling** — Carbon 50 / Oxygen 30 / Phosphorus 20. Gardener: crops near it grow faster; spores boost regeneration. Timid.
8. **Ironclad Ravager** — Iron 65 / Carbon 20 / Nickel 15. Combat companion at Devoted; metal drops. Bold.
9. **Zephyr Sprite** — Nitrogen 78 / Oxygen 21 / Argon 1. Scout: speeds owner, reveals nearby chests/creatures, carries items to the anchor. Playful.

## 10. Cross-Cutting Ideas To Evaluate

1. Bond-tier perks: each creature's unique function unlocks at Bonded and strengthens at Devoted (bond currently only scales drops).
2. Role categories (Producer, Guardian, Utility, Companion) so a Menagerie is a mix.
3. Synergies between pairs in the same anchor radius (e.g. Bloomling + Zephyr Sprite; Volt Strider + Ironclad Ravager).
4. Elemental drops tied to composition (Frostveil → ice/water items; Quakeback → stone/gems).
5. Needs beyond food (biome, light level, nearby water).
