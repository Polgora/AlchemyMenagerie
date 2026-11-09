package me.Polgora;

import javax.annotation.Nullable;

/**
 * Behavioral personality class for a Menagerie species. Controls how a creature
 * reacts to strangers and how its bond with an owner is expressed (proximity
 * cue particles and frequency). Classified per species in
 * {@link CreatureTemperamentRegistry}.
 *
 * <p>The per-class design specs live in TODO.md item 13. Role-JSON state machines
 * implement the stranger/owner reactions; this enum only carries the values the
 * Java ticking systems need.
 *
 * <p>Proximity-cue particles are <b>vanilla placeholders</b> from
 * {@code Server/Particles/NPC/Emotions/} (base filenames, no path/extension) so
 * every class reads differently today without custom assets. Swap in custom
 * particle systems here when they exist (listed in TODO.md).
 *
 * <ul>
 *   <li>Timid   — hearts (final design).
 *   <li>Bold    — {@code Alerted} ("!") placeholder for the low rumble/stomp effect.
 *   <li>Curious — {@code Question} / {@code Question_Subtle} placeholder for the sparkle/"!" effect.
 *   <li>Aloof   — no cue at Bonded; a rare subtle heart at Devoted (the one tier where it
 *       "breaks character" — tone to be confirmed, see TODO.md).
 *   <li>Playful — hearts at 2.5x frequency placeholder for the bouncy colorful burst.
 * </ul>
 */
public enum CreatureTemperament {

    //          name       bonded cue         devoted cue   cue interval multiplier (<1 = more often)
    TIMID   ("Timid",   "Hearts_Subtle",   "Hearts",        1.0),
    BOLD    ("Bold",    "Alerted",         "Alerted",       1.0),
    CURIOUS ("Curious", "Question_Subtle", "Question",      1.0),
    ALOOF   ("Aloof",   null,              "Hearts_Subtle", 4.0),
    PLAYFUL ("Playful", "Hearts_Subtle",   "Hearts",        0.4);

    private final String displayName;
    private final String bondedParticle;
    private final String devotedParticle;
    private final double cueIntervalMultiplier;

    CreatureTemperament(String displayName, @Nullable String bondedParticle,
                        @Nullable String devotedParticle, double cueIntervalMultiplier) {
        this.displayName           = displayName;
        this.bondedParticle        = bondedParticle;
        this.devotedParticle       = devotedParticle;
        this.cueIntervalMultiplier = cueIntervalMultiplier;
    }

    public String getDisplayName() { return displayName; }

    /** Particle system played at the Bonded tier proximity cue, or {@code null} for none. */
    @Nullable
    public String getBondedParticle()  { return bondedParticle; }

    /** Particle system played at the Devoted tier proximity cue, or {@code null} for none. */
    @Nullable
    public String getDevotedParticle() { return devotedParticle; }

    /** Scales the base cue interval: &lt;1 fires more often, &gt;1 more rarely. */
    public double getCueIntervalMultiplier() { return cueIntervalMultiplier; }
}
