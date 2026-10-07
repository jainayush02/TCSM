package com.amdocs.telecom.util;

import com.amdocs.telecom.exception.TelecomException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.Savepoint;

public final class Transactions {
    private static final ThreadLocal<Connection> CURRENT = new ThreadLocal<>();

    private Transactions() {}

    @FunctionalInterface
    public interface Work<T> { T run() throws Exception; }

    public static void lockSubscriptions(java.util.Collection<Integer> ids) throws Exception {
        if(CURRENT.get()==null) throw new IllegalStateException("A transaction is required.");
        for(int id:new java.util.TreeSet<>(ids)) {
            try(java.sql.PreparedStatement p=CURRENT.get().prepareStatement("SELECT subscription_id FROM mobile_subscriptions WHERE subscription_id=? FOR UPDATE")) {
                p.setInt(1,id);
                try(java.sql.ResultSet r=p.executeQuery()) { if(!r.next()) throw new TelecomException("Subscription not found."); }
            }
        }
    }

    static Connection currentConnection() {
        Connection connection = CURRENT.get();
        if (connection == null) return null;
        return (Connection) Proxy.newProxyInstance(Connection.class.getClassLoader(), new Class<?>[]{Connection.class},
                (proxy, method, args) -> {
                    String name = method.getName();
                    // The outer transaction owns the connection's lifetime.
                    if (name.equals("close") || name.equals("commit") || name.equals("setAutoCommit")) return null;
                    try { return method.invoke(connection, args); }
                    catch (InvocationTargetException e) { throw e.getCause(); }
                });
    }

    public static <T> T run(Work<T> work) throws TelecomException {
        Connection existing = CURRENT.get();
        if (existing != null) {
            Savepoint savepoint = null;
            try {
                savepoint = existing.setSavepoint();
                T result = work.run();
                existing.releaseSavepoint(savepoint);
                return result;
            } catch (Exception e) {
                if (savepoint != null) try { existing.rollback(savepoint); } catch (Exception rollback) { e.addSuppressed(rollback); }
                throw failure(e);
            }
        }
        try (Connection connection = DBConnection.getInstance().getConnection()) {
            connection.setAutoCommit(false);
            CURRENT.set(connection);
            try {
                T result = work.run();
                connection.commit();
                return result;
            } catch (Exception e) {
                try { connection.rollback(); } catch (Exception rollback) { e.addSuppressed(rollback); }
                throw failure(e);
            } finally { CURRENT.remove(); }
        } catch (TelecomException e) { throw e; }
        catch (Exception e) { throw failure(e); }
    }

    private static TelecomException failure(Exception e) {
        return e instanceof TelecomException ? (TelecomException)e : new TelecomException("Transaction failed: " + e.getMessage(), e);
    }
}
