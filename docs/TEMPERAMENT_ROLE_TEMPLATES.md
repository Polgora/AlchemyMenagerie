# Temperament role-JSON templates

Paste-in starting points for each temperament's NPC role JSON (`Server/NPC/Roles/Creature/Critter/{RoleName}.json`).
Everything below is built only from constructs already used by the working Verdant Sprout (Timid) and Ember Wyrmling (Bold)
roles, **but none of the Curious / Aloof / Playful snippets has been run in-game** — treat them as drafts and test with the
first creature of each class. Timid and Bold are implemented in the real files; read those instead.

## Shared mechanics (all classes)

- **Tier signals.** `CreatureTierSlotSystem` / `CreatureLeashTrackingSystem` fill marked-target slots on the NPC:
  `TameOwner` (Tame), `BondedOwner` (Bonded), `BondedOwnerStill` (Bonded, and owner has stood still ~1.5s),
  `OwnerTarget` (Devoted). A slot exists only if a `Target` sensor in the
  role references it by `TargetSlot`. Owner is also `Friendly` from Tame upward (attitude override).
- **Instruction order.** In a state's `Instructions`, the first matching non-`Continue` instruction wins. Branches that only
  add head-motion use `"Continue": true`.
- **Stranger vs owner.** The owner is `Friendly` at Tame+, so `Not Attitude Friendly` filters select strangers only.
- **Proximity cue** (particles) is Java-side: `CreatureTemperament` + `CreatureHeartParticleSystem`. Nothing in the role JSON.
- **Common head-watch for Tame/Bonded** (copy from `Ember_Wyrmling.json` Idle state): a `Continue` branch with
  `Target` sensor on `TameOwner` (Range 8) and another on `BondedOwner` (Range 10), each with `"HeadMotion": {"Type":"Watch"}`.
- **Devoted follow block** is already in the base JSON (`OwnerTarget`, Seek, `StopDistance`, `RelativeSpeed`) — tune per class below.

## Aloof

Set parameters: `"Timid": false`, `"PlayerDefaultAttitude": "Ignore"`.
`Ignore` makes the Idle `Standard_Detection` (Attitudes `Hostile`, `Neutral`) never trigger on players, so the creature never
alerts, flees or approaches — wild, tame and stranger behavior are all indifference. Owner still becomes `Friendly` via the
Java override (no visible reaction needed).

- **Bonded:** nothing extra — the Java leash drift recentres it; no head-watch branch (no enthusiasm).
- **Devoted (open design question — see TODO.md):** to keep it independent, loosen the Devoted follow block:
  `"StopDistance": 6`, `"SlowDownDistance": 6`, `"RelativeSpeed": 0.35`.
- **Cue:** none at Bonded; rare subtle heart at Devoted (`CreatureTemperament.ALOOF`, 4x interval).

## Curious

Set parameters: `"Timid": false`, `"WarnRange"` not needed. Remove/disable the "Threatening actions" flee triggers except
`Combat` and `*Weapon*` (copy Ember's approach: gate the old Or-sensor with `"Enabled": {"Compute":"Timid"}` and add a
reduced version without the `Timid`/`AbsoluteDetectionRange` members).

Add to the Idle state, before the daytime idle behaviors — **approach and inspect unfamiliar players** (Wild/Tame, strangers):

```json
{
  "$Comment": "Curious: drift toward a non-hostile, non-weapon player to investigate, stop at 4 blocks, watch them.",
  "Sensor": {
    "Type": "Player",
    "Range": 15,
    "LockOnTarget": true,
    "Filters": [
      { "Type": "Not", "Filter": { "Type": "Attitude", "Attitudes": [ "Hostile" ] } },
      { "Type": "Not", "Filter": { "Type": "ItemInHand", "Items": [ "*Weapon*" ] } }
    ]
  },
  "BodyMotion": { "Type": "Seek", "SlowDownDistance": 6, "StopDistance": 4, "RelativeSpeed": 0.3, "RelaxedMoveConstraints": false },
  "HeadMotion": { "Type": "Watch" },
  "Actions": [ { "Type": "PlayAnimation", "Slot": "Status", "Animation": "Curious" } ]
}
```

- **Backs off on sudden aggression:** keep the Alerted-state flee only for the `Combat` / `*Weapon*` members.
- **Tame/Bonded:** common head-watch branches (it tracks what the owner is doing).
- **Devoted:** keep the base follow block; add a short detour — a `Continue` branch with a `Random` of
  `WanderInCircle` (Radius 4, `ExecuteFor` [2,4]) vs nothing, gated on `OwnerTarget` within 8, so it occasionally pokes around
  before catching back up. Draft only.
- **Cue:** `Question_Subtle` / `Question` (vanilla placeholder; custom sparkle later).

## Playful

Set parameters: `"Timid": false`. Disable the flee-from-player triggers as for Curious (it only retreats from `Combat` / weapons).

**Wild/stranger: approach, dart away, circle back** — Idle branch, `Random` over a Seek and a short Flee:

```json
{
  "$Comment": "Playful: alternately approach and dart away from a non-hostile player.",
  "Sensor": {
    "Type": "Player",
    "Range": 12,
    "LockOnTarget": true,
    "Filters": [ { "Type": "Not", "Filter": { "Type": "Attitude", "Attitudes": [ "Hostile" ] } } ]
  },
  "Type": "Random",
  "ExecuteFor": [ 2, 4 ],
  "Instructions": [
    { "Weight": 1, "BodyMotion": { "Type": "Seek", "StopDistance": 2, "RelativeSpeed": 0.7 }, "HeadMotion": { "Type": "Watch" } },
    { "Weight": 1, "BodyMotion": { "Type": "Flee", "StopDistance": 6, "SlowDownDistance": 3, "RelativeSpeed": 0.8 }, "HeadMotion": { "Type": "Watch" } }
  ]
}
```

- **Tame/Bonded:** replace the Sprout-style step-toward-owner with a bouncier wander near the owner: gate on `TameOwner` / `BondedOwner`
  within 8 and use `WanderInCircle` with `RelativeSpeed` ~0.6 and `MaxHeadingChange` 120 for `ExecuteFor` [2,4].
- **Devoted:** replace the base follow block's body motion with a `Sequence`: `Timer` [1,2] `Seek` (RelativeSpeed 1.0,
  StopDistance 1) then `Timer` [1,2] `WanderInCircle` (Radius 3, RelativeSpeed 0.8), `Looped: true` — "burst ahead, circle back".
  Needs `Play` / `Hop` animations to look right (see TODO.md).
- **Cue:** hearts at 0.4x interval (`CreatureTemperament.PLAYFUL`) — placeholder for a bouncy colorful burst.

## Timid / Bold

Implemented — see `Verdant_Sprout.json` and `Ember_Wyrmling.json`.
