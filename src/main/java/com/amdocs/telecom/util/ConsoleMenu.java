package com.amdocs.telecom.util;

public final class ConsoleMenu {
    private static final int WIDTH = 60;

    private ConsoleMenu() {}

    public static void show(String title, String subtitle, String... options) {
        print(title, subtitle, false, options);
    }

    public static void showCompact(String title, String subtitle, String... options) {
        print(title, subtitle, true, options);
    }

    private static void print(String title, String subtitle, boolean compact, String... options) {
        String border = "+" + "-".repeat(WIDTH + 2) + "+";
        System.out.println();
        System.out.println(border);
        row(title);
        if (subtitle != null && !subtitle.isBlank()) row(subtitle);
        System.out.println(border);
        for (int i = 0; i < options.length; i++) {
            String option = options[i];
            if (compact && i + 1 < options.length && option.matches("[1-9][0-9]*\\..*")
                    && options[i + 1].matches("[1-9][0-9]*\\..*")
                    && option.length() <= 29 && options[i + 1].length() <= 29) {
                row(String.format("%-29s  %-29s", option, options[++i]));
            } else {
                row(option);
            }
        }
        System.out.println(border);
    }

    private static void row(String text) {
        String remaining = text.replaceAll("[\\r\\n\\t]", " ");
        while (remaining.length() > WIDTH) {
            int split = remaining.lastIndexOf(' ', WIDTH);
            if (split <= 0) split = WIDTH;
            System.out.printf("| %-60s |%n", remaining.substring(0, split));
            remaining = remaining.substring(split).stripLeading();
        }
        System.out.printf("| %-60s |%n", remaining);
    }
}
