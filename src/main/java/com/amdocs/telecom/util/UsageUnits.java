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
        double result;
        if (type == UsageType.DATA || type == UsageType.ROAMING) {
            if (value.equals("GB")) result = quantity * 1024;
            else if (value.equals("MB")) result = quantity;
            else if (value.equals("KB")) result = quantity / 1024;
            else throw new IllegalArgumentException("Data and roaming usage must use KB, MB or GB.");
        } else if (type == UsageType.VOICE) {
            if (value.equals("MINUTES") || value.equals("MINUTE") || value.equals("MIN")) result = quantity;
            else if (value.equals("SECONDS") || value.equals("SECOND")) result = quantity / 60;
            else throw new IllegalArgumentException("Voice usage must use minutes or seconds.");
        } else {
            if (!(value.equals("COUNT") || value.equals("SMS")) || quantity != Math.rint(quantity))
                throw new IllegalArgumentException("SMS usage must be a whole count.");
            result = quantity;
        }
        if (!Double.isFinite(result)) throw new IllegalArgumentException("Usage quantity is too large.");
        return result;
    }
}
