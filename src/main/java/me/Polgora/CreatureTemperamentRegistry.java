package me.Polgora;

import com.hypixel.hytale.logger.HytaleLogger;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Maps creature species (NPC role names) to their {@link CreatureTemperament}.
 * Mirrors {@link CreatureDietRegistry}'s species-map pattern.
 *
 * <p>Every registered Menagerie creature must have an entry here. Keep the role
 * JSON's temperament-specific parameters in sync with the class chosen below.
 * {@link #validate(Collection)} logs a warning at startup for any species that
 * was registered elsewhere but forgotten here.
 */
public class CreatureTemperamentRegistry {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    private static final Map<String, CreatureTemperament> SPECIES_TEMPERAMENT;
    static {
        Map<String, CreatureTemperament> m = new HashMap<>();
        m.put("Verdant_Sprout", CreatureTemperament.TIMID);
        m.put("Ember_Wyrmling", CreatureTemperament.BOLD);
        SPECIES_TEMPERAMENT = Collections.unmodifiableMap(m);
    }

    /** Returns the temperament for the given role name, or {@code null} if unregistered. */
    public static CreatureTemperament getTemperament(String roleName) {
        return SPECIES_TEMPERAMENT.get(roleName);
    }

    /** Like {@link #getTemperament} but falls back to {@link CreatureTemperament#TIMID}. */
    public static CreatureTemperament getTemperamentOrDefault(String roleName) {
        CreatureTemperament t = SPECIES_TEMPERAMENT.get(roleName);
        return t != null ? t : CreatureTemperament.TIMID;
    }

    /** UI label, e.g. {@code "Temperament: Bold"} ({@code "Unknown"} if unregistered). */
    public static String getDisplayLabel(String roleName) {
        CreatureTemperament t = SPECIES_TEMPERAMENT.get(roleName);
        return "Temperament: " + (t != null ? t.getDisplayName() : "Unknown");
    }

    /**
     * Startup consistency check: logs a warning for every role in {@code knownRoles}
     * that has no temperament entry (those silently fall back to Timid cues), and for
     * every temperament entry whose species is not a known creature.
     *
     * @return the number of problems found (0 = consistent)
     */
    public static int validate(Collection<String> knownRoles) {
        int problems = 0;
        for (String role : knownRoles) {
            if (!SPECIES_TEMPERAMENT.containsKey(role)) {
                problems++;
                LOGGER.at(java.util.logging.Level.WARNING).log(
                        "Creature '%s' has no temperament in CreatureTemperamentRegistry; "
                        + "falling back to TIMID cues. Add an entry.", role);
            }
        }
        for (String role : SPECIES_TEMPERAMENT.keySet()) {
            if (!knownRoles.contains(role)) {
                problems++;
                LOGGER.at(java.util.logging.Level.WARNING).log(
                        "CreatureTemperamentRegistry has '%s' but it is not registered in "
                        + "CreatureDietRegistry (stale entry or missing diet).", role);
            }
        }
        return problems;
    }
}
