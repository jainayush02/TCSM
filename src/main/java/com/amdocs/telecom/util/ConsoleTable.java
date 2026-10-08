package com.amdocs.telecom.util;

import java.util.Arrays;

/** Fixed-width console columns that preserve long values on continuation lines. */
public final class ConsoleTable {
    private final int[] widths;
    private final String separator;

    public ConsoleTable(int... widths) {
        if (widths.length == 0 || Arrays.stream(widths).anyMatch(width -> width < 1)) {
            throw new IllegalArgumentException("Table columns must have positive widths.");
        }
        this.widths = widths.clone();
        this.separator = "  " + "-".repeat(Arrays.stream(widths).sum() + widths.length - 1);
    }

    public void separator() {
        System.out.println(separator);
    }

    public void header(Object... values) {
        row(values);
        separator();
    }

    public void row(Object... values) {
        if (values.length != widths.length) {
            throw new IllegalArgumentException("Value count must match table column count.");
        }
        String[] remaining = new String[values.length];
        for (int i = 0; i < values.length; i++) {
            remaining[i] = values[i] == null ? "-" : values[i].toString().replaceAll("\\s+", " ").strip();
        }
        boolean more;
        do {
            StringBuilder line = new StringBuilder("  ");
            more = false;
            for (int i = 0; i < widths.length; i++) {
                String value = remaining[i];
                int split = Math.min(value.length(), widths[i]);
                if (value.length() > widths[i]) {
                    int space = value.lastIndexOf(' ', widths[i]);
                    if (space > 0) split = space;
                }
                String part = value.substring(0, split);
                remaining[i] = value.substring(split).stripLeading();
                line.append(part).append(" ".repeat(widths[i] - part.length()));
                if (i + 1 < widths.length) line.append(' ');
                more |= !remaining[i].isEmpty();
            }
            System.out.println(line);
        } while (more);
    }
}
