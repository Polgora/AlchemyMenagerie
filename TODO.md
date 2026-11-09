# TODO

Backlog identified during in-game testing on 2026-11-07 (post Server 0.7.6 API migration).

.2. **Add a close ("X") button to UI menus.** Custom UI pages (Journal, Arcanoscope, Menagerie Table/Nest, Anchor Info, Runebound Whistle, etc.) need a consistent close affordance, per the UI Style Guide conventions in `CLAUDE.md`.

3. **Redesign the Discoveries tab UI.** Replace the current layout with a simple clickable list of plain item names (e.g. "Dirt") that open a sub-page showing that item's detail info (element composition / description), rather than the current inline display. Needs its own design pass against `UI_STYLE_GUIDE.md` and the Discoveries page width (640, per CLAUDE.md's Standard Container Widths table).

4. **Add a UI button/shortcut to open the Menagerie Journal** — currently only accessible via the physical book item; add another access point (e.g. a hotkey or keybind-triggered UI shortcut) so players don't have to carry/find the book.

5. **Lower the movement margin when scanning with the Arcanoscope.** User wants the look-direction tolerance that cancels a scan on movement (`LOOK_THRESHOLD` in `ArcanoscopePacketWatcher`) reduced.

6. **Arcanoscope scan particles should not activate if the targeted block can't be scanned.** Currently the particle effect fires regardless of whether the block is actually eligible (e.g. not in `JournalManager.isScannableItem`), which is misleading — particles should only play once the target is confirmed scannable.

7. **Add loot items that help players discover bestiary/creature recipes.**

8. **Goggles to see the elements of scanned materials.**

9. **Droppers to remove all of one essence from a flask.**

10. **All elements in UI use periodic table of elements icons.**

11. **The in-game speed multiplier should affect AlchemyMenagerie functions** — e.g. hatch time (`NestManager`), bond/feed timers, anchor leash-tracking intervals, and so on, rather than running on a fixed real-time clock regardless of the server's configured game speed.

12. **Runebound Whistle UI text.** The species-select phase's help text should say "Select a creature from above, then press Continue." (see `RuneboundWhistlePage.populateSpeciesStepper`'s `#HelpText.Text` lines). Also revisit how creatures of the same species are listed on that page — current per-creature slot display needs a change (specifics TBD).

13. **Creature temperament classification system.** Discussed 2026-11-08 — species-specific bonding/behavior variety, organized as a `CreatureTemperament` enum (TIMID, BOLD, CURIOUS, ALOOF, PLAYFUL) + a `CreatureTemperamentRegistry` mapping role name → temperament, mirroring `CreatureDietRegistry`'s existing pattern. Verdant Sprout = TIMID; other species not yet classified.

    **Note:** the universal hearts effect (`CreatureHeartParticleSystem`: subtle hearts at Bonded, streaming hearts at Devoted, within 10 blocks of the owner) already exists. Proximity cues (and any other particle/sound effects keyed to bond state) should eventually differ *by temperament class*, not be one universal heart effect — see the per-class notes below.

    ### Implementation status (updated 2026-11-09) — first in-game testing done; Sprout accepted, Ember facing still being tuned (see "Resume here")

    **Built:**
    - `CreatureTemperament` enum + `CreatureTemperamentRegistry` (Verdant Sprout = TIMID, Ember Wyrmling = BOLD).
    - Proximity cue per class via `CreatureHeartParticleSystem`, using **vanilla placeholder particles** (Timid hearts, Bold `Alerted`, Curious `Question`/`Question_Subtle`, Aloof none at Bonded + rare heart at Devoted, Playful hearts at 2.5x frequency).
    - `CreatureTierSlotSystem`: publishes the owner into `TameOwner` / `BondedOwner` marked-target slots (Devoted already uses `OwnerTarget`) so role JSON can branch per bond tier.
    - `CreatureTierSlotSystem` also tracks owner movement and fills a `BondedOwnerStill` slot once the owner has stood still ~1.5s (tunable constants `STILL_AFTER_MS` / `MOVE_EPSILON` in that class).
    - **Temperament shown in the UI:** creature info page and journal bestiary detail now read `Diet: Herbivore  |  Temperament: Timid` (appended to the existing diet label; no layout changes).
    - **Startup consistency check:** `Main.setup()` calls `CreatureTemperamentRegistry.validate(...)`, which logs a WARNING for any creature in `CreatureDietRegistry` with no temperament entry (and for stale temperament entries). Check the server log after adding a creature.
    - **Verdant Sprout (Timid):** Tame head-watch; Bonded head-watch + occasional step toward owner (a few slow walking steps, not a jump), **only while the owner is standing still** (otherwise it wanders/grazes/naps normally; while the owner stands still nearby it will not wander, graze or nap); Wild flee sensitivity scaled by player speed (running/sprinting flee at 8 blocks, walking/standing at 6, crouching never); Devoted recovers from a flee back to following in 2-3s.
    - **Ember Wyrmling (Bold):** `Timid` is now false; it never flees from players. Strangers within `WarnRange` (5 blocks, Wild) / `WarnRangeOwned` (3.5, Tame+) get a warning posture (placeholder: `Alerted` animation + vanilla `Angry` emote), owner never does. Idle detection uses smaller Bold-specific alert ranges (`AlertViewRange` 10 / `AlertHearingRange` 8, vs. ViewRange 24 / HearingRange 12) so it settles into Idle and wanders when nobody is close; in Alerted the whole body turns to face the target (`MatchLook`). Tame/Bonded head-watch, plus a body turn (`MatchLook`) when the owner is more than ~25 degrees off its facing (view-sector 50) — **this is the part still being tuned, see "Resume here" below.** Devoted close-follow is unchanged.
    - **Tier changes now reset cleanly** (any tier to any other): `CreatureLeashTrackingSystem` clears the Devoted follow target and leash bookkeeping, and sets the leash to the creature's anchor if it has one, otherwise to its current position. Each reset logs `bond tier X -> Y: ... leash set to ...`. (Earlier version restored the original spawn point, which sent far-wandered creatures walking home ~20s after a tier change.)
    - **Debug aid left in the tree:** `CreatureStateDebugSystem` logs every creature state change (`[state] ...`, with bond tier and distance to the nearest player) and, for Tame/Bonded creatures inside their watch range, a `[look]` line whenever head or body is more than 15 degrees off the owner. Set `CreatureStateDebugSystem.ENABLED = false` (or remove its registration in `Main`) before a release build.
    - `docs/TEMPERAMENT_ROLE_TEMPLATES.md`: untested paste-in role-JSON drafts for Curious, Aloof and Playful, for when their first species exists.

    **NOT built (blocked on assets, decisions, or an existing species):**
    - Curious / Aloof / Playful role JSON — no species of those classes exists yet (templates only).
    - Bold: owner-escort positioning (ahead of owner), interposing against hostile mobs, "quicker to warn at strangers approaching the owner" (needs owner-relative geometry — not attempted).
    - Timid: Tame "prefers the owner's food" (no way found to tell whose dropped item is whose), Devoted trailing gait / affection idle / interposing against threats.
    - Wild startle on sudden movement or loud sound (no sensor identified; proximity-only today).
    - Custom particle effects (currently vanilla placeholders).
    - Aloof Devoted tone and Playful rival-caution question (design decisions below).

    ### Resume here (paused 2026-11-09)

    **Verdant Sprout: leave as is.** The Sprout's behavior and look were fine; the experimental body-turn (`MatchLook`) added to it tonight was removed again. It keeps only head tracking for Tame/Bonded, plus the accepted step-toward-owner, speed-scaled flee and Devoted flee recovery.

    **Ember Wyrmling: facing works but is very twitchy — open.**
    - What the `[look]` log showed: the head tracks the owner exactly, but the head can only swing **45 degrees either side of the body** (measured, repeatedly: head-minus-body was exactly 45). Beyond that the head stops and the body has to turn.
    - Body-turn trigger tuning so far, in the role JSON `ViewSector` of the Tame/Bonded body-turn branch in `Ember_Wyrmling.json`: 80 (turn only when >40 degrees off) left the body parked ~38 degrees off, which looked like it wasn't watching; 30 (>15 degrees off) was very twitchy; **50 (>25 degrees off) is the current value and has NOT been tested yet.**
    - Unexplained: once, Ember sat with its body 39 degrees off, completely still, for ~25 seconds (owner also still, 6.2 blocks away) even though the body turn should have engaged. Cleared once the owner moved. Possible cause: Idle's daytime `Random` has `ExecuteFor [20, 30]`, so a chosen child may hold for 20-30s; not confirmed.
    - **Your hypothesis to check first: it may be Ember's model.** Sprout and Ember behaved differently with near-identical role JSON, so compare the two: `Server/Models/Critter/Ember_Wyrmling.json` vs `Verdant_Sprout.json` (head yaw limits, hitbox, scale) and the `.blockymodel` head node/bone names. If the model has no usable head bone, head-only tracking will always look wrong and the answer may be a model fix, or dropping the head-watch for Ember in favor of body-only turning.
    - Options if 50 is still twitchy: widen further (60-70), or only body-turn when the owner is *close* (it was ~5 blocks originally), or accept head-only for Ember if the model is the cause.

    **Still to verify in-game (not yet confirmed):**
    - Wild Ember now wanders/settles to Idle once you're more than ~10 blocks away (new smaller alert ranges), and doesn't flee.
    - The tier-reset leash fix: after `/bondset`, a creature with no anchor stays put instead of walking away ~20s later (log said `leash set to its current position`, and no ReturnHome followed in the one test done, but re-check on Sprout too).
    - The remaining unchecked items in the testing checklist below (needs a second player).

    **Dev process notes (learned the hard way):**
    - **Never run `./gradlew build` while `devServer` is running** — the server watches `build/resources/main`, sees every mod asset "deleted", unloads the roles and kicks the connected player. Stop the server (TaskStop the gradle task *and* kill the JVM holding UDP 5520), then start it again with `./gradlew devServer` (that builds first).
    - NPC role JSON is only validated when the server **boots**. After any role edit, check the boot log for `[NPC|P] FAIL` lines and `Loaded 2 NPC configurations, Generic: 2`; a role that fails is silently missing (`Reloading nonexistent role X`, can't spawn).
    - A single filter list can't contain two `MovementState` filters; every child of a `Random` instruction needs its own `Sensor`.

    ### Needed from you (assets & decisions)

    **Animations missing from BOTH existing creatures** (`Server/Models/Critter/*.json` `AnimationSets` + `.blockyanim` in `Common/NPC/Critter/{Role}/Animations/Default/`):
    - `Curious` and `Eat` — the role JSONs already `PlayAnimation` these (food-following, grazing) but neither creature defines them, so they currently show nothing. Needed for **Verdant Sprout** and **Ember Wyrmling**.
    - Neither creature's model file lists `Eat`/`Curious`/`Flinch`/`Warn`/`Affection` in `AnimationSets` — each new animation needs both the `.blockyanim` file *and* a model-JSON entry.

    **Per creature:**
    - **Verdant Sprout (Timid):** `Flinch` (startle, Wild); `Affection` (Devoted idle — nuzzle/look-up when owner stands still); a distinct trailing-gait Walk variant for Devoted heel-following (optional).
    - **Ember Wyrmling (Bold):** `Warn` (warning posture — currently reusing `Alerted`); `Affection` (Devoted idle, if wanted); confident-gait Walk variant for Bonded (optional).
    - **Any future Playful creature:** `Play` and `Hop`, plus `Affection`.
    - **Any future Aloof creature:** `Affection` only if you choose to have its Devoted tier "break character".

    **Particles** (`Server/Particles/*.particlesystem` + `.particlespawner`; currently vanilla placeholders — tell me when you have custom ones and I'll swap the names in `CreatureTemperament`):
    - Bold: low rumble/stomp effect (replaces vanilla `Alerted` "!").
    - Curious: sparkle/"!" effect (replaces vanilla `Question`).
    - Playful: bouncy, colorful burst (replaces frequent hearts).
    - Aloof: rare, subtle Devoted cue (currently a plain heart, 4x less frequent).
    - Also the Bold warning-posture emote (currently vanilla `Angry`, used in `Ember_Wyrmling.json`).

    **Sounds:** startle (Timid), warn growl (Bold), chime (Playful, optional) — plus hooking them into the role JSON; tell me the sound IDs.

    **Decisions needed:**
    1. Aloof Devoted: looser/slower follow than other classes (my draft), or the one tier where aloofness visibly breaks with a rare affection cue? (Enum currently does the rare-heart version; role template does the loose-follow version — pick one or both.)
    2. Bonded Sprout step-toward-owner: it now only engages while the owner stands still, but then lingers rather than wandering/grazing/napping. Is that OK? Easy to shrink the range, lower the linger weight, or change `STILL_AFTER_MS` after you see it in-game.
    3. Is the vanilla `Angry` emote acceptable as a stop-gap for Ember's warning, or would you rather it do nothing until you have a custom effect?
    4. Which species to make first for Curious, Aloof and Playful, so those classes get real role JSON (suggestions from item 15: Volt Strider / Duskwing Moth = Curious, Frostveil Stalker / Quakeback Tortoise = Aloof, Glimmerfin Ray / Zephyr Sprite = Playful).

    ### In-game testing checklist (items already verified in-game have been removed; what's left needs a second player or a log check)

    **Setup:** `./gradlew devServer`, connect to `localhost:5520`, `/op Polgora`. Spawn with `/npc spawn Verdant_Sprout` and `/npc spawn Ember_Wyrmling`. Own a creature by right-clicking it and pressing **Feed** (Sprout = herbivore: apple/carrot/bread; Ember = carnivore: raw meat/fish). Set tiers with `/bondlist` then `/bondset Verdant_Sprout#1 TAME|BONDED|DEVOTED|WILD` (same for `Ember_Wyrmling#1`). **Do not hit your creatures — death is permanent.**

    **Expected, not bugs:** no animation plays while following food or grazing (`Curious` / `Eat` don't exist yet); Ember's warning reuses the `Alerted` pose + vanilla `Angry` emote; server log shows `Animation Curious/Eat does not exist` for both creatures.

    **4. Verdant Sprout (Timid) — Devoted** (`/bondset ... DEVOTED`)
    - [ ] (Needs a second player) after being scared by a stranger it returns to following within ~2-3s.

    **7. Ember Wyrmling — owned**
    - [ ] (Needs a second player) strangers are warned at about **3.5 blocks** instead of 5.

    **10. Server log, during play**
    - [ ] Any new `SEVERE` / `WARN` lines on spawn or first use (esp. about `Angry`, `Alerted`, `Question`, unknown slots/particles) — paste them.

    **Report back per section:** works as described / doesn't / partly, what you actually saw, and the bond tier + distance when something looks wrong.

    **Timid (Verdant Sprout) — deferred additions, need new assets/AI work:**
    - Wild: skittish flinch/startle reaction to sudden nearby movement or loud sounds (not just proximity); prefers idling in shaded/dense plant cover; flee sensitivity scales with player speed (sprint vs. walk).
    - Tame: head-turn "recognize" cue when the owner approaches; prefers the owner's dropped food over a stranger's.
    - Bonded: occasional small unprompted step toward the owner during idle, when owner is nearby but not moving.
    - Devoted: distinct "trailing" gait/animation at heel distance; positions itself between the owner and a perceived threat despite being naturally timid; unique idle "affection" animation (nuzzle/look-up) when the owner stands still nearby; faster recovery from a flee/startle state back to following.
    - *Stranger reaction (any tier):* unchanged from Wild — flees from any non-crouching stranger regardless of how bonded it is with its owner. Its skittishness toward everyone but its owner never goes away; that's the point of being Timid.
    - *Proximity cue:* keep the existing soft `Hearts_Subtle`/`Hearts` effect (already implemented) — gentle hearts fit a shy, affectionate plant creature.

    **Bold — full spec, not yet implemented:**
    - Wild: doesn't flee from standing players; tracks an approaching player with a watchful idle rather than fleeing; crouching has no effect; brief warning posture/sound if a stranger gets too close uninvited.
    - Tame: no warning posture toward the owner; still postures toward strangers, but at a shorter trigger range than Wild; stands down faster near strangers if the owner is nearby and calm.
    - Bonded: generic leash-drift, but with a confident gait (cosmetic); may position itself slightly ahead of the owner rather than behind, like an escort.
    - Devoted: close-follow, but may interpose itself between the owner and a hostile mob or posturing stranger (mild guard instinct); quicker to warn-posture at strangers approaching the owner.
    - *Stranger reaction (any tier):* never flees — that's the defining trait, same at Wild and Devoted alike. What changes with bond is the *warning* behavior: a lone Wild Bold creature postures readily at any stranger who crowds it, but once Devoted to an owner, it reserves posturing for strangers who approach *the owner*, largely ignoring strangers who keep their distance from both of them.
    - *Proximity cue:* a low rumble/stomp effect rather than hearts — something that reads as confident/territorial rather than affectionate.

    **Curious — full spec, not yet implemented:**
    - Wild: approaches unfamiliar players/items to investigate at moderate distance; "inspects" newly placed blocks/items nearby; backs off (not a full flee) only on sudden aggressive player movement.
    - Tame: actively seeks out the owner when they do something new (place a block, open a UI); will investigate strangers' items too, but prefers the owner's.
    - Bonded: generic leash drift, plus curious head-tracking of whatever the owner is doing within the anchor radius.
    - Devoted: close-follow, but may wander briefly off-path to inspect something interesting before catching back up — a personality quirk rather than a strict beeline.
    - *Stranger reaction (any tier):* essentially constant regardless of bond — it investigates strangers the same way at Wild and Devoted alike, since curiosity toward the unfamiliar is just its nature, not something that fades as it bonds with an owner. The only difference once Devoted: it gives any *new* person a quick once-over, then returns its attention to the owner, rather than lingering.
    - *Proximity cue:* a sparkle/"!"-style curiosity effect instead of hearts, reflecting fascination rather than affection.

    **Aloof — full spec, not yet implemented:**
    - Wild: ignores players entirely — no flee, no approach, continues its normal routine regardless of proximity.
    - Tame: still mostly indifferent; tolerates feeding but shows minimal reaction to the owner.
    - Bonded: leash still drifts toward the owner, but with no enthusiasm — same pace/animation as always, just recentered.
    - Devoted: open design question — either keeps a notably looser/slower follow than other temperaments even at max bond (staying true to its independence), or this is the one tier where the aloofness visibly breaks and it shows a rare, subtle affection cue. Decide the tone deliberately when scoped.
    - *Stranger reaction (any tier):* identical indifference toward strangers and owner alike at every tier except Devoted — if Devoted is chosen as the tier where it finally shows affection, that reaction should still be owner-exclusive; strangers always get ignored, never acknowledged, regardless of bond.
    - *Proximity cue:* minimal-to-none at Bonded (fits its reduced-expression personality); if Devoted is chosen as the "breaks character" tier, that's the one place a rare, subtle, low-frequency cue could appear — notably sparser than every other class's.

    **Playful — full spec, not yet implemented:**
    - Wild: skittish-but-curious hybrid — approaches, darts away, circles back repeatedly, rather than a clean flee or stand-ground; drawn to flocking with other nearby creatures over players.
    - Tame: initiates play-like behavior near the owner — short hops, bursts of movement, circling, when idle together.
    - Bonded: generic leash drift, but movement within the anchor radius is bouncier/less direct than other temperaments; may "play" with other Bonded creatures if several are present.
    - Devoted: close-follow with energetic flourishes — bursts ahead then circles back rather than calm heel-following; likely the liveliest "excitement" reaction when the owner returns after being away.
    - *Stranger reaction (any tier):* keeps darting/circling around strangers the same way at any bond tier — treats any nearby entity, owner or not, as a potential playmate. Possibly becomes more cautious/skittish toward a stranger specifically if that stranger is near a perceived rival or threat to the owner — open design question for later.
    - *Proximity cue:* the bounciest, most frequent, most colorful particle burst of all the classes — reflects its high-energy personality; should feel noticeably livelier than Timid's gentle hearts.

14. **Lower `ChestLootEvent.EnrichmentSystem`'s container-open log line from INFO to FINE/debug level.** It currently logs at INFO on every single `UseBlockEvent.Post` for any container, which would add routine noise to production logs at scale. Not a performance issue (the player-placed check itself is a cheap in-memory `Set.contains()`, no disk I/O on read) — purely a log-hygiene cleanup.

15. **Future creatures to implement.** Visual descriptions and elemental compositions proposed 2026-11-08. (Ember Wyrmling already exists — Silicon 52% / Oxygen 28% / Iron 20%, matches exactly — so it's excluded here.) Each needs the full new-creature pipeline from CLAUDE.md's "Adding a New Creature" checklist (Java registrations, role/model/egg/drop JSON, lang entries, model/animation/icon assets) before it's playable — these are design starting points only, nothing is implemented yet.

    - **Glimmerfin Ray** — a graceful, manta-like creature that "swims" through open air as if it were water. Semi-transparent body refracts light into rainbow patterns; long trailing fins ripple like silk in slow motion; emits a faint chiming sound while gliding. *Composition:* Oxygen 65%, Hydrogen 25%, Phosphorus 10%.
    - **Thornhide Brute** — a massive quadruped with thick, bark-like plating resembling a walking fortress of wood and stone. Jagged thorns jut from its shoulders and spine; moss and vines grow along its limbs. Moves slowly but with immense force. *Composition:* Carbon 48%, Oxygen 32%, Calcium 20%.
    - **Volt Strider** — a tall, spindly creature with multiple jointed legs, moving in sharp, stuttering motions. Glowing circuitry-like lines pulse across its body, occasionally arcing electricity between its limbs; the air around it crackles constantly. *Composition:* Copper 40%, Silicon 35%, Neon 25%.
    - **Frostveil Stalker** — a silent predator appearing as a shifting outline within a cloud of freezing mist. Fine, crystalline fur-like structures glint like ice shards; each step leaves spreading frost, and its breath forms dense freezing fog. *Composition:* Oxygen 70%, Hydrogen 20%, Nitrogen 10%.
    - **Duskwing Moth** — an enormous moth with wide, velvety wings that seem to absorb surrounding light, creating a dim aura wherever it flies; subtle shadow-like patterns ripple across its wings. Drifts slowly and silently, often drawn to dim or dying light sources. *Composition:* Carbon 55%, Nitrogen 30%, Sulfur 15%.
    - **Quakeback Tortoise** — a colossal tortoise with a shell of layered stone plates resembling tectonic slabs. Its weight trembles the ground slightly as it walks, shaking dust loose from its shell; small cracks in the plates glow faintly, hinting at pressure building within. *Composition:* Silicon 60%, Oxygen 25%, Aluminum 15%.
    - **Radiant Bloomling** — a small, plant-like entity with a soft bioluminescent glow. Petal-like appendages slowly open and close around a bright, pulsing core; drifts or lightly steps across terrain, leaving faint glowing spore trails that fade after a few moments. *Composition:* Carbon 50%, Oxygen 30%, Phosphorus 20%.
    - **Ironclad Ravager** — a muscular, wolf-like predator with metallic, interlocking plates instead of fur. Movement produces a faint grinding sound with occasional sparks where plates rub together; eyes burn with a steady red glow, jaws able to shear through most materials. *Composition:* Iron 65%, Carbon 20%, Nickel 15%.
    - **Zephyr Sprite** — a tiny, elusive spirit composed of swirling currents of air, vaguely humanoid in shape. Flickers in and out of visibility, marked only by glimmers of light and the movement of dust or leaves; darts around playfully, faster than the eye can follow. *Composition:* Nitrogen 78%, Oxygen 21%, Argon 1%.

    **Design rule (decided 2026-11-08): every species has its own distinct function, and no two species share one.** Beyond the shared anchor resource drops, each creature gets one unique active/passive perk. Proposed functions (starting points, nothing implemented; temperament suggestions tie into item 13):

    | Creature | Proposed function | Suggested temperament |
    |---|---|---|
    | Glimmerfin Ray | Living lantern — lights up the pen with a faint light trail; at Devoted it follows the owner as a portable light source | Playful |
    | Thornhide Brute | Guardian — hostile mobs inside the anchor radius target it first, high HP; possible pack animal with extra inventory slots at Bonded+ | Bold |
    | Volt Strider | Bench booster — speeds up smelting/alchemy at nearby benches; shocks mobs that get close | Curious |
    | Frostveil Stalker | Preservation aura — food/potions in chests inside its radius spoil more slowly; frost-based protection for the owner | Aloof |
    | Duskwing Moth | Danger detector — drawn to dim light, gathers near dark spots/spawn risks in caves; drops night-themed items (dust, silk) | Curious |
    | Quakeback Tortoise | Stone breaker — slowly loosens ore from nearby stone; stomps knock back enemies | Aloof |
    | Radiant Bloomling | Gardener — crops near it grow faster; spore trails boost regeneration | Timid |
    | Ironclad Ravager | Combat companion — fights alongside the owner at Devoted; metal drops (iron, nickel) | Bold |
    | Zephyr Sprite | Scout — speeds up owner movement, reveals nearby chests/creatures, carries items to the anchor | Playful |

    **Cross-cutting system ideas (to evaluate when scoping):**
    - **Bond-tier perks, not just drop rates.** Each creature's unique function unlocks at Bonded and gets a stronger version at Devoted. Currently bond only scales drops, so this gives bonding a visible payoff.
    - **Role categories.** Tag each species as Producer, Guardian, Utility or Companion, so a good Menagerie is a mix rather than a pen of identical producers.
    - **Synergies.** Pairs in the same anchor radius could combine (e.g. Bloomling + Zephyr Sprite spreads growth boosts wider; Volt Strider + Ironclad Ravager yields a charged drop).
    - **Elemental drops.** Tie drops to composition (Frostveil Stalker's Oxygen/Hydrogen → ice/water items; Quakeback's Silicon → stone/gems).
    - **Needs beyond food.** Some species require a biome, light level, or nearby water, giving reasons to place them deliberately.

16. **Follow-ups from the `_Known` variant audit (2026-11-08).** (a) 14 documented portal blocks (`Portal_Device`, hub portals, `PortalKey_*`, etc.) were skipped along with the other portal items — add `_Known` variants if wanted. (b) `Furniture_Ancient_Chest_Large_Treasure_Known` has no matching vanilla item (stale) — delete or keep. (c) `_Known` files are copies of the vanilla item JSON, so they can drift from vanilla after a game update; re-audit after major updates.
