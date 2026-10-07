package com.amdocs.telecom.util;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.logging.ConsoleHandler;
import java.util.logging.FileHandler;
import java.util.logging.Level;
import java.util.logging.LogManager;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;

public final class ApplicationLogging {
    private ApplicationLogging() { }

    public static void configure() {
        LogManager.getLogManager().reset();
        Logger root = Logger.getLogger("");
        root.setLevel(Level.INFO);
        try {
            Files.createDirectories(Path.of("logs"));
            FileHandler handler = new FileHandler("logs/tcsms-%g.log", 1_000_000, 3, true);
            handler.setFormatter(new SimpleFormatter());
            root.addHandler(handler);
        } catch (IOException | SecurityException e) {
            ConsoleHandler fallback = new ConsoleHandler();
            fallback.setLevel(Level.WARNING);
            root.addHandler(fallback);
            root.log(Level.WARNING, "Cannot open the application log; errors will appear on the console.", e);
        }
    }
}
