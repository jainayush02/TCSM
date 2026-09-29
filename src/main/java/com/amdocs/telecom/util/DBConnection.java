package com.amdocs.telecom.util;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Singleton database connection manager.
 * Targets MySQL primarily as required by the case study,
 * with automatic fallback to embedded H2 if MySQL Server is not running locally.
 */
public class DBConnection {

    private static final Logger LOGGER = Logger.getLogger(DBConnection.class.getName());
    private static DBConnection instance;
    private static Properties properties = new Properties();
    private static boolean useFallbackH2 = false;

    static {
        loadProperties();
        testAndInitializeDatabase();
    }

    private DBConnection() {}

    public static synchronized DBConnection getInstance() {
        if (instance == null) {
            instance = new DBConnection();
        }
        return instance;
    }

    private static void loadProperties() {
        try (InputStream input = DBConnection.class.getClassLoader().getResourceAsStream("db.properties")) {
            if (input != null) {
                properties.load(input);
            }
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "db.properties not found, using embedded settings", e);
        }
    }

    public Connection getConnection() throws SQLException {
        if (!useFallbackH2) {
            try {
                Class.forName(properties.getProperty("db.driver", "com.mysql.cj.jdbc.Driver"));
                return DriverManager.getConnection(
                        properties.getProperty("db.url"),
                        properties.getProperty("db.user"),
                        properties.getProperty("db.password")
                );
            } catch (Exception e) {
                LOGGER.info("MySQL connection unavailable (" + e.getMessage() + "). Switching to local fallback database.");
                useFallbackH2 = true;
            }
        }

        try {
            Class.forName(properties.getProperty("h2.driver", "org.h2.Driver"));
            return DriverManager.getConnection(
                    properties.getProperty("h2.url", "jdbc:h2:./data/tcsms_db;DB_CLOSE_DELAY=-1;MODE=MySQL"),
                    properties.getProperty("h2.user", "sa"),
                    properties.getProperty("h2.password", "")
            );
        } catch (ClassNotFoundException e) {
            throw new SQLException("Database driver not found", e);
        }
    }

    public static void testAndInitializeDatabase() {
        try (Connection conn = DBConnection.getInstance().getConnection()) {
            LOGGER.info("Connected to database successfully: " + conn.getMetaData().getDatabaseProductName());
            runScript(conn, "schema.sql");
            runScript(conn, "seed.sql");
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Failed to initialize database tables", e);
        }
    }

    private static void runScript(Connection conn, String scriptPath) {
        try (InputStream is = DBConnection.class.getClassLoader().getResourceAsStream(scriptPath)) {
            if (is == null) return;
            BufferedReader reader = new BufferedReader(new InputStreamReader(is));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.startsWith("--") || line.isEmpty()) continue;
                int commentIdx = line.indexOf("--");
                if (commentIdx != -1) {
                    line = line.substring(0, commentIdx).trim();
                }
                if (line.isEmpty()) continue;
                sb.append(line).append("\n");
                if (line.endsWith(";")) {
                    String sql = sb.toString().trim();
                    if (!sql.isEmpty()) {
                        try (Statement stmt = conn.createStatement()) {
                            stmt.execute(sql);
                        } catch (SQLException e) {
                            // Suppress already-exists notices, log unexpected errors
                            if (!e.getMessage().toLowerCase().contains("already exists")) {
                                LOGGER.log(Level.FINE, "SQL Note on [" + sql.substring(0, Math.min(30, sql.length())) + "...]: " + e.getMessage());
                            }
                        }
                    }
                    sb.setLength(0);
                }
            }
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Notice while running " + scriptPath + ": " + e.getMessage());
        }
    }
}
