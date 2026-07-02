package me.dankofuk.utils;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.potion.PotionEffectType;

/**
 * Cross-version compatibility helpers.
 *
 * <p>The plugin is compiled against a single Spigot API version but is intended to run on
 * anything from 1.8 through the latest 1.21.x. Several enum constants were renamed over the
 * years (for example {@code SLOW} -> {@code SLOWNESS} and {@code JUMP} -> {@code JUMP_BOOST}
 * in the 1.20.5 "component" update). Referencing the old constants directly compiles fine but
 * throws {@link NoSuchFieldError} at runtime on newer servers, so we resolve them dynamically
 * by name instead and cache the result.</p>
 */
public final class Compat {

    private Compat() {
    }

    /** Slowness effect: "SLOWNESS" on modern servers, "SLOW" on legacy ones. */
    public static final PotionEffectType SLOWNESS = potion("SLOWNESS", "SLOW");
    /** Jump boost effect: "JUMP_BOOST" on modern servers, "JUMP" on legacy ones. */
    public static final PotionEffectType JUMP_BOOST = potion("JUMP_BOOST", "JUMP");
    /** Blindness effect (stable name across all supported versions). */
    public static final PotionEffectType BLINDNESS = potion("BLINDNESS");

    /**
     * Resolves the first {@link PotionEffectType} that exists for the given candidate names.
     *
     * @param names candidate names ordered from most-modern to most-legacy
     * @return the resolved type, or {@code null} if none are present on this server
     */
    public static PotionEffectType potion(String... names) {
        for (String name : names) {
            PotionEffectType type = PotionEffectType.getByName(name);
            if (type != null) {
                return type;
            }
        }
        return null;
    }

    /**
     * Resolves the first {@link Material} that exists for the given candidate names. Useful for
     * items whose enum name changed during the 1.13 "flattening".
     *
     * @param names candidate names ordered by preference
     * @return the resolved material, or {@code null} if none are present on this server
     */
    public static Material material(String... names) {
        for (String name : names) {
            Material material = Material.getMaterial(name);
            if (material != null) {
                return material;
            }
        }
        return null;
    }

    /**
     * @return the server's major.minor version parsed from the Bukkit version string
     *         (for example {@code 20} for a 1.20.4 server), or {@code -1} if it cannot be parsed.
     */
    public static int minorVersion() {
        try {
            String version = Bukkit.getBukkitVersion(); // e.g. "1.20.4-R0.1-SNAPSHOT"
            String[] parts = version.split("-")[0].split("\\.");
            if (parts.length >= 2) {
                return Integer.parseInt(parts[1]);
            }
        } catch (Exception ignored) {
        }
        return -1;
    }
}
