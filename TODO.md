# TODO

Backlog identified during in-game testing on 2026-11-07 (post Server 0.7.6 API migration).

1. **Missing `_Known` variants for some vanilla blocks.** Found during Arcanoscope testing — some vanilla blocks have no `_Known` counterpart, so they can never be scanned/journaled. Audit which vanilla `BlockType`/`Item` IDs under `Server/Item/Items/` lack a `_Known` JSON and add them, following the existing `_Known` convention (`KnownItemSwapper`, `JournalManager.isScannableItem`).

2. **Add a close ("X") button to UI menus.** Custom UI pages (Journal, Arcanoscope, Menagerie Table/Nest, Anchor Info, Runebound Whistle, etc.) need a consistent close affordance, per the UI Style Guide conventions in `CLAUDE.md`.

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

    **Done (2026-11-08):** `CreatureBondAttitudeSystem` previously granted FRIENDLY to *any* player once Tame+, contradicting its own javadoc. Fixed to check the target is specifically the creature's owner — strangers now always get the creature's natural (non-owner) reaction regardless of bond tier, so each temperament's own stranger behavior stays meaningful even at Devoted.

    ~~Bonded-tier proximity recognition cue~~ — already implemented via `CreatureHeartParticleSystem` (subtle hearts at Bonded, streaming hearts at Devoted, within 10 blocks of the owner). Removed as a to-do; this was just a gap in my own awareness of the existing code, not a missing feature. **Note:** proximity cues (and any other particle/sound effects keyed to bond state) should eventually differ *by temperament class*, not be one universal heart effect — see the per-class notes below.

    **Timid (Verdant Sprout) — deferred additions, need new assets/AI work:**
    - Wild: skittish flinch/startle reaction to sudden nearby movement or loud sounds (not just proximity); prefers idling in shaded/dense plant cover; flee sensitivity scales with player speed (sprint vs. walk).
    - Tame: head-turn "recognize" cue when the owner approaches; prefers the owner's dropped food over a stranger's.
    - Bonded: occasional small unprompted hop/step toward the owner during idle, when owner is nearby but not moving.
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
