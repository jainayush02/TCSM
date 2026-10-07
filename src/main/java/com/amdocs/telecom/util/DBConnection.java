package com.amdocs.telecom.util;

import java.io.BufferedReader;
import java.io.IOException;
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
 * Database connection manager with connection pooling support.
 * Targets MySQL primarily as required by the case study,
 * with automatic fallback to embedded H2 if MySQL Server is not running locally.
 *
 * Uses a small internal pool so DAO calls can reuse connections.
 */
public class DBConnection {

    private static final Logger LOGGER = Logger.getLogger(DBConnection.class.getName());
    private static final Properties properties = new Properties();
    private static boolean useFallbackH2 = false;

    // Keep a small pool of reusable connections
    private static final int POOL_SIZE = 10;
    private static final java.util.concurrent.BlockingQueue<Connection> connectionPool =
            new java.util.concurrent.LinkedBlockingQueue<>(POOL_SIZE);

    static {
        loadProperties();
        testAndInitializeDatabase();
    }

    private DBConnection() {}

    public static DBConnection getInstance() {
        return InstanceHolder.INSTANCE;
    }

    private static class InstanceHolder {
        private static final DBConnection INSTANCE = new DBConnection();
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

    /**
     * Returns a connection that goes back to the pool when closed.
     */
    public Connection getConnection() throws SQLException {
        // Reuse a connection when one is available
        Connection pooled = connectionPool.poll();
        if (pooled != null) {
            try {
                if (!pooled.isClosed() && pooled.isValid(2)) {
                    return new PooledConnectionWrapper(pooled, connectionPool);
                }
                // Discard stale connections
                pooled.close();
            } catch (SQLException e) {
                // Open a new connection if the pooled one cannot be checked
            }
        }

        // Nothing usable was in the pool
        Connection raw = createRawConnection();
        return new PooledConnectionWrapper(raw, connectionPool);
    }

    /**
     * Creates a raw JDBC connection (physical TCP connection).
     */
    private Connection createRawConnection() throws SQLException {
        if (!useFallbackH2) {
            try {
                Class.forName(properties.getProperty("db.driver", "com.mysql.cj.jdbc.Driver"));
                return DriverManager.getConnection(
                    properties.getProperty("db.url"),
                    System.getenv().getOrDefault("TCSMS_DB_USER", properties.getProperty("db.user", "root")),
                    System.getenv().getOrDefault("TCSMS_DB_PASSWORD", properties.getProperty("db.password", ""))
                );
            } catch (ClassNotFoundException | SQLException e) {
                LOGGER.log(Level.INFO,
                        "MySQL connection unavailable ({0}). Switching to local fallback database.",
                        e.getMessage());
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

    public String getActiveDatabaseName() {
        try (Connection connection = getConnection()) {
            return connection.getMetaData().getDatabaseProductName();
        } catch (SQLException e) {
            return "Unavailable (" + e.getMessage() + ")";
        }
    }

    public static void testAndInitializeDatabase() {
        try (Connection conn = DBConnection.getInstance().getConnection()) {
            LOGGER.log(Level.INFO, "Connected to database successfully: {0}",
                    conn.getMetaData().getDatabaseProductName());
            runScript(conn, "schema.sql");
            ensureAdministratorAccountStatusColumn(conn);
            runScript(conn, "seed.sql");
        } catch (SQLException e) {
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
                            // Ignore duplicate-object errors, but keep unexpected ones visible
                            if (!e.getMessage().toLowerCase().contains("already exists")) {
                                LOGGER.log(Level.FINE, "SQL note on [{0}...]: {1}",
                                        new Object[] {
                                                sql.substring(0, Math.min(30, sql.length())),
                                                e.getMessage()
                                        });
                            }
                        }
                    }
                    sb.setLength(0);
                }
            }
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "Notice while running {0}: {1}",
                    new Object[] {scriptPath, e.getMessage()});
        }
    }

    private static void ensureAdministratorAccountStatusColumn(Connection conn) throws SQLException {
        boolean exists = false;
        try (java.sql.ResultSet columns = conn.getMetaData().getColumns(null, null, "ADMINISTRATORS", "ACCOUNT_STATUS")) {
            while (columns.next()) {
                exists = true;
                break;
            }
        }

        if (!exists) {
            try (Statement statement = conn.createStatement()) {
                statement.executeUpdate("ALTER TABLE administrators ADD COLUMN account_status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE'");
            }
        }
    }

    /**
     * Closes all connections currently held by the pool.
     */
    public void shutdown() {
        Connection conn;
        while ((conn = connectionPool.poll()) != null) {
            try {
                conn.close();
            } catch (SQLException ignored) {}
        }
        LOGGER.info("Connection pool shut down.");
    }

    /**
     * Returns the underlying connection to the pool instead of closing it.
     */
    private static class PooledConnectionWrapper implements Connection {
        private final Connection delegate;
        private final java.util.concurrent.BlockingQueue<Connection> pool;
        private boolean closed = false;

        PooledConnectionWrapper(Connection delegate, java.util.concurrent.BlockingQueue<Connection> pool) {
            this.delegate = delegate;
            this.pool = pool;
        }

        @Override
        public void close() throws SQLException {
            if (!closed) {
                closed = true;
                try {
                    // Reset connection state before returning to pool
                    if (!delegate.isClosed()) {
                        if (!delegate.getAutoCommit()) {
                            delegate.setAutoCommit(true);
                        }
                        // Try to return to pool; if pool is full, close the physical connection
                        if (!pool.offer(delegate)) {
                            delegate.close();
                        }
                    }
                } catch (SQLException e) {
                    // If reset fails, close the physical connection
                    try { delegate.close(); } catch (SQLException ignored) {}
                }
            }
        }

        // Delegate all Connection interface methods to the underlying connection
        @Override public Statement createStatement() throws SQLException { return delegate.createStatement(); }
        @Override public java.sql.PreparedStatement prepareStatement(String sql) throws SQLException { return delegate.prepareStatement(sql); }
        @Override public java.sql.CallableStatement prepareCall(String sql) throws SQLException { return delegate.prepareCall(sql); }
        @Override public String nativeSQL(String sql) throws SQLException { return delegate.nativeSQL(sql); }
        @Override public void setAutoCommit(boolean autoCommit) throws SQLException { delegate.setAutoCommit(autoCommit); }
        @Override public boolean getAutoCommit() throws SQLException { return delegate.getAutoCommit(); }
        @Override public void commit() throws SQLException { delegate.commit(); }
        @Override public void rollback() throws SQLException { delegate.rollback(); }
        @Override public boolean isClosed() throws SQLException { return closed || delegate.isClosed(); }
        @Override public java.sql.DatabaseMetaData getMetaData() throws SQLException { return delegate.getMetaData(); }
        @Override public void setReadOnly(boolean readOnly) throws SQLException { delegate.setReadOnly(readOnly); }
        @Override public boolean isReadOnly() throws SQLException { return delegate.isReadOnly(); }
        @Override public void setCatalog(String catalog) throws SQLException { delegate.setCatalog(catalog); }
        @Override public String getCatalog() throws SQLException { return delegate.getCatalog(); }
        @Override public void setTransactionIsolation(int level) throws SQLException { delegate.setTransactionIsolation(level); }
        @Override public int getTransactionIsolation() throws SQLException { return delegate.getTransactionIsolation(); }
        @Override public java.sql.SQLWarning getWarnings() throws SQLException { return delegate.getWarnings(); }
        @Override public void clearWarnings() throws SQLException { delegate.clearWarnings(); }
        @Override public Statement createStatement(int resultSetType, int resultSetConcurrency) throws SQLException { return delegate.createStatement(resultSetType, resultSetConcurrency); }
        @Override public java.sql.PreparedStatement prepareStatement(String sql, int resultSetType, int resultSetConcurrency) throws SQLException { return delegate.prepareStatement(sql, resultSetType, resultSetConcurrency); }
        @Override public java.sql.CallableStatement prepareCall(String sql, int resultSetType, int resultSetConcurrency) throws SQLException { return delegate.prepareCall(sql, resultSetType, resultSetConcurrency); }
        @Override public java.util.Map<String, Class<?>> getTypeMap() throws SQLException { return delegate.getTypeMap(); }
        @Override public void setTypeMap(java.util.Map<String, Class<?>> map) throws SQLException { delegate.setTypeMap(map); }
        @Override public void setHoldability(int holdability) throws SQLException { delegate.setHoldability(holdability); }
        @Override public int getHoldability() throws SQLException { return delegate.getHoldability(); }
        @Override public java.sql.Savepoint setSavepoint() throws SQLException { return delegate.setSavepoint(); }
        @Override public java.sql.Savepoint setSavepoint(String name) throws SQLException { return delegate.setSavepoint(name); }
        @Override public void rollback(java.sql.Savepoint savepoint) throws SQLException { delegate.rollback(savepoint); }
        @Override public void releaseSavepoint(java.sql.Savepoint savepoint) throws SQLException { delegate.releaseSavepoint(savepoint); }
        @Override public Statement createStatement(int resultSetType, int resultSetConcurrency, int resultSetHoldability) throws SQLException { return delegate.createStatement(resultSetType, resultSetConcurrency, resultSetHoldability); }
        @Override public java.sql.PreparedStatement prepareStatement(String sql, int resultSetType, int resultSetConcurrency, int resultSetHoldability) throws SQLException { return delegate.prepareStatement(sql, resultSetType, resultSetConcurrency, resultSetHoldability); }
        @Override public java.sql.CallableStatement prepareCall(String sql, int resultSetType, int resultSetConcurrency, int resultSetHoldability) throws SQLException { return delegate.prepareCall(sql, resultSetType, resultSetConcurrency, resultSetHoldability); }
        @Override public java.sql.PreparedStatement prepareStatement(String sql, int autoGeneratedKeys) throws SQLException { return delegate.prepareStatement(sql, autoGeneratedKeys); }
        @Override public java.sql.PreparedStatement prepareStatement(String sql, int[] columnIndexes) throws SQLException { return delegate.prepareStatement(sql, columnIndexes); }
        @Override public java.sql.PreparedStatement prepareStatement(String sql, String[] columnNames) throws SQLException { return delegate.prepareStatement(sql, columnNames); }
        @Override public java.sql.Clob createClob() throws SQLException { return delegate.createClob(); }
        @Override public java.sql.Blob createBlob() throws SQLException { return delegate.createBlob(); }
        @Override public java.sql.NClob createNClob() throws SQLException { return delegate.createNClob(); }
        @Override public java.sql.SQLXML createSQLXML() throws SQLException { return delegate.createSQLXML(); }
        @Override public boolean isValid(int timeout) throws SQLException { return delegate.isValid(timeout); }
        @Override public void setClientInfo(String name, String value) throws java.sql.SQLClientInfoException { delegate.setClientInfo(name, value); }
        @Override public void setClientInfo(java.util.Properties props) throws java.sql.SQLClientInfoException { delegate.setClientInfo(props); }
        @Override public String getClientInfo(String name) throws SQLException { return delegate.getClientInfo(name); }
        @Override public java.util.Properties getClientInfo() throws SQLException { return delegate.getClientInfo(); }
        @Override public java.sql.Array createArrayOf(String typeName, Object[] elements) throws SQLException { return delegate.createArrayOf(typeName, elements); }
        @Override public java.sql.Struct createStruct(String typeName, Object[] attributes) throws SQLException { return delegate.createStruct(typeName, attributes); }
        @Override public void setSchema(String schema) throws SQLException { delegate.setSchema(schema); }
        @Override public String getSchema() throws SQLException { return delegate.getSchema(); }
        @Override public void abort(java.util.concurrent.Executor executor) throws SQLException { delegate.abort(executor); }
        @Override public void setNetworkTimeout(java.util.concurrent.Executor executor, int milliseconds) throws SQLException { delegate.setNetworkTimeout(executor, milliseconds); }
        @Override public int getNetworkTimeout() throws SQLException { return delegate.getNetworkTimeout(); }
        @Override public <T> T unwrap(Class<T> iface) throws SQLException { return delegate.unwrap(iface); }
        @Override public boolean isWrapperFor(Class<?> iface) throws SQLException { return delegate.isWrapperFor(iface); }
    }
}
