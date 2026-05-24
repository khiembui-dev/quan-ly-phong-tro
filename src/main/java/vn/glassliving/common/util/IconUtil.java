package vn.glassliving.common.util;

import java.util.Map;

/**
 * Maps internal codes to icons8 Fluency-style icon URLs (free CDN, no API key required).
 * Used for richer visual treatment of amenities, payment methods, etc.
 *
 * Source: https://icons8.com/icons/fluency
 */
public final class IconUtil {

    private static final String FLUENCY_BASE = "https://img.icons8.com/fluency/96/";
    private static final String COLOR_BASE   = "https://img.icons8.com/color/96/";

    /** Amenity code → icons8 icon name (Fluency style). */
    private static final Map<String, String> AMENITY_ICON = Map.ofEntries(
            Map.entry("WIFI",          "wifi.png"),
            Map.entry("AIR_CON",       "air-conditioner.png"),
            Map.entry("WATER_HEATER",  "water-heater.png"),
            Map.entry("WASHING",       "washing-machine.png"),
            Map.entry("FRIDGE",        "refrigerator.png"),
            Map.entry("KITCHEN",       "kitchen-room.png"),
            Map.entry("PARKING",       "parking.png"),
            Map.entry("SECURITY_24",   "policeman-male.png"),
            Map.entry("CCTV",          "cctv.png"),
            Map.entry("POOL",          "swimming-pool.png"),
            Map.entry("GYM",           "dumbbell.png"),
            Map.entry("BALCONY",       "balcony.png"),
            Map.entry("PET",           "pet-commands-stay.png"),
            Map.entry("ELEVATOR",      "elevator.png")
    );

    private IconUtil() {}

    /** Returns icons8 fluency URL for an amenity code, or null if no mapping exists. */
    public static String amenityIcon(String code) {
        if (code == null) return null;
        String name = AMENITY_ICON.get(code);
        return name != null ? FLUENCY_BASE + name : null;
    }

    /** Generic helper for ad-hoc icons8 lookups. */
    public static String fluency(String name) {
        return FLUENCY_BASE + name;
    }

    public static String color(String name) {
        return COLOR_BASE + name;
    }
}
