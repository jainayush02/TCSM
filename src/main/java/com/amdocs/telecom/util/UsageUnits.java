package com.amdocs.telecom.util;

import com.amdocs.telecom.model.UsageType;
import java.util.Locale;

public final class UsageUnits {
    private UsageUnits() {}

    public static String unit(UsageType type) {
        return type == UsageType.VOICE ? "Minutes" : type == UsageType.SMS ? "Count" : "MB";
    }

    public static double normalize(UsageType type, double quantity, String unit) {
        if (type == null || unit == null || !Double.isFinite(quantity) || quantity <= 0)
            throw new IllegalArgumentException("Usage needs a valid type, unit and finite positive quantity.");
        String value = unit.trim().toUpperCase(Locale.ROOT);
        double result = switch (type) {
            case DATA, ROAMING -> switch (value) {
                case "GB" -> quantity * 1024;
                case "MB" -> quantity;
                case "KB" -> quantity / 1024;
                default -> throw new IllegalArgumentException("Data and roaming usage must use KB, MB or GB.");
            };
            case VOICE -> switch (value) {
                case "MINUTES", "MINUTE", "MIN" -> quantity;
                case "SECONDS", "SECOND" -> quantity / 60;
                default -> throw new IllegalArgumentException("Voice usage must use minutes or seconds.");
            };
            case SMS -> {
                if (!(value.equals("COUNT") || value.equals("SMS")) || quantity != Math.rint(quantity))
                    throw new IllegalArgumentException("SMS usage must be a whole count.");
                yield quantity;
            }
        };
        if (!Double.isFinite(result)) throw new IllegalArgumentException("Usage quantity is too large.");
        return result;
    }
}
