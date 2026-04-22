package AutoNego;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/**
 * Reads config.properties from the current working directory.
 * Falls back to hard-coded defaults if the file is missing or a key is absent.
 *
 * Usage:
 *   int dealerCount = ConfigLoader.getInt("dealer.count", 3);
 *   double fee      = ConfigLoader.getDouble("broker.fixedFee", 500.0);
 */
public final class ConfigLoader {

    private static final String CONFIG_FILE = "config.properties";
    private static final Properties PROPS = new Properties();

    static {
        File file = new File(CONFIG_FILE);
        if (file.exists()) {
            try (InputStream in = new FileInputStream(file)) {
                PROPS.load(in);
                System.out.println("[ConfigLoader] Loaded " + file.getAbsolutePath());
            } catch (IOException e) {
                System.err.println("[ConfigLoader] Could not read " + CONFIG_FILE + ": " + e.getMessage());
            }
        } else {
            System.out.println("[ConfigLoader] " + CONFIG_FILE + " not found — using built-in defaults.");
        }
    }

    private ConfigLoader() {
    }

    public static int getInt(String key, int defaultValue) {
        String val = PROPS.getProperty(key);
        if (val == null) return defaultValue;
        try {
            return Integer.parseInt(val.trim());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    public static double getDouble(String key, double defaultValue) {
        String val = PROPS.getProperty(key);
        if (val == null) return defaultValue;
        try {
            return Double.parseDouble(val.trim());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    public static String getString(String key, String defaultValue) {
        return PROPS.getProperty(key, defaultValue);
    }
}
