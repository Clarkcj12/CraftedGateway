package net.sanctuary.servers.craftedgateway.config;

import org.bukkit.configuration.file.FileConfiguration;

public final class ConfigUtils {
    private ConfigUtils() {
    }

    public static String normalizeString(String value, String fallback) {
        if (value == null) {
            return fallback;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? fallback : trimmed;
    }

    public static String normalizeOptional(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    public static String getNormalizedString(FileConfiguration config, String path, String fallback) {
        if (config == null) {
            return fallback;
        }
        return normalizeString(config.getString(path, fallback), fallback);
    }

    public static String getNormalizedStringFromDefaults(
        FileConfiguration config,
        String path,
        String fallback
    ) {
        String defaultValue = getDefaultString(config, path, fallback);
        return getNormalizedString(config, path, defaultValue);
    }

    public static String getNormalizedOptional(FileConfiguration config, String path) {
        if (config == null) {
            return null;
        }
        return normalizeOptional(config.getString(path, null));
    }

    public static String getDefaultString(FileConfiguration config, String path, String fallback) {
        if (config == null || config.getDefaults() == null) {
            return fallback;
        }
        return config.getDefaults().getString(path, fallback);
    }

    public static boolean getDefaultBoolean(FileConfiguration config, String path, boolean fallback) {
        if (config == null || config.getDefaults() == null) {
            return fallback;
        }
        return config.getDefaults().getBoolean(path, fallback);
    }
}
