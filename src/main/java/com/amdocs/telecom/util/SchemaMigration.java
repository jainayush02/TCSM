package com.amdocs.telecom.util;

import java.sql.*;
import java.util.*;

public final class SchemaMigration {
    private SchemaMigration() {}

    private static String table(Connection c, String name) throws SQLException {
        try (ResultSet rows = c.getMetaData().getTables(c.getCatalog(), null, "%", new String[]{"TABLE"})) {
            while (rows.next()) if (name.equalsIgnoreCase(rows.getString("TABLE_NAME"))) return rows.getString("TABLE_NAME");
        }
        return name;
    }

    private static boolean column(Connection c, String name, String field) throws SQLException {
        try (ResultSet rows = c.getMetaData().getColumns(c.getCatalog(), null, table(c, name), "%")) {
            while (rows.next()) if (field.equalsIgnoreCase(rows.getString("COLUMN_NAME"))) return true;
        }
        return false;
    }

    private static boolean index(Connection c, String name, String index) throws SQLException {
        try (ResultSet rows = c.getMetaData().getIndexInfo(c.getCatalog(), null, table(c, name), false, false)) {
            while (rows.next()) if (index.equalsIgnoreCase(rows.getString("INDEX_NAME"))) return true;
        }
        return false;
    }

    public static void apply(Connection c) throws SQLException {
        boolean mysql = c.getMetaData().getDatabaseProductName().equalsIgnoreCase("MySQL");
        try (Statement s = c.createStatement()) {
            if (!column(c, "administrators", "account_status"))
                s.executeUpdate("ALTER TABLE administrators ADD COLUMN account_status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE'");
            if (!column(c, "bills", "invoice_type"))
                s.executeUpdate("ALTER TABLE bills ADD COLUMN invoice_type VARCHAR(20) NOT NULL DEFAULT 'MONTHLY'");
            if (!column(c, "bills", "invoice_key"))
                s.executeUpdate("ALTER TABLE bills ADD COLUMN invoice_key VARCHAR(100)");
            migrateInvoiceKeys(c);
            if (index(c, "bills", "uq_bill_subscription_month"))
                s.executeUpdate(mysql ? "DROP INDEX uq_bill_subscription_month ON bills" : "DROP INDEX uq_bill_subscription_month");
            if (!index(c, "bills", "uq_invoice_key")) s.executeUpdate("CREATE UNIQUE INDEX uq_invoice_key ON bills(invoice_key)");
            if (!column(c, "telecom_plans", "allow_type_change"))
                s.executeUpdate("ALTER TABLE telecom_plans ADD COLUMN allow_type_change BOOLEAN NOT NULL DEFAULT FALSE");
            if (!column(c, "telecom_plans", "minimum_change_days"))
                s.executeUpdate("ALTER TABLE telecom_plans ADD COLUMN minimum_change_days INT NOT NULL DEFAULT 0");
            if (!column(c, "customers", "updated_at"))
                s.executeUpdate("ALTER TABLE customers ADD COLUMN updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP");
            Map<String, String[]> money = new LinkedHashMap<>();
            money.put("telecom_plans", new String[]{"monthly_rental"});
            money.put("usage_records", new String[]{"charge"});
            money.put("bills", new String[]{"plan_rental","usage_charges","tax_amount","discount","total_amount"});
            money.put("payments", new String[]{"amount"});
            for (Map.Entry<String,String[]> entry : money.entrySet()) for (String field : entry.getValue()) {
                int type = Types.DECIMAL;
                try (ResultSet rows = c.getMetaData().getColumns(c.getCatalog(), null, table(c, entry.getKey()), "%")) {
                    while(rows.next()) if(field.equalsIgnoreCase(rows.getString("COLUMN_NAME"))) { type=rows.getInt("DATA_TYPE"); break; }
                }
                if(type==Types.DECIMAL || type==Types.NUMERIC) continue;
                s.executeUpdate("ALTER TABLE " + entry.getKey() + (mysql ? " MODIFY COLUMN " : " ALTER COLUMN ") + field +
                        (mysql ? " DECIMAL(18,2) NOT NULL" : " DECIMAL(18,2)"));
            }
            // Only replace the known broken demo hash; preserve passwords changed by users.
            try (PreparedStatement p = c.prepareStatement("UPDATE customers SET password_hash=?, account_status=CASE WHEN account_status='LOCKED' THEN 'ACTIVE' ELSE account_status END WHERE password_hash=? AND username IN ('arjunm','sarahw','omarh')")) {
                p.setString(1, "$2a$10$sIDl.6SBUnH9IG23TmFc/eHItikRx8YqdxxCeEGOvrlyA3mcNPKbO");
                p.setString(2, "$2a$10$wT0H7eFj/y55hX8Q8eLgU.8jWsqR6k1bH4x7f3Y6k9Q1qZ8xPzPWe");
                p.executeUpdate();
            }
        }
    }

    private static void migrateInvoiceKeys(Connection c) throws SQLException {
        Set<String> assignedKeys = new HashSet<>();
        Map<Integer, String> pendingKeys = new LinkedHashMap<>();
        try (Statement s = c.createStatement();
             ResultSet rows = s.executeQuery("SELECT bill_id,subscription_id,billing_month,invoice_type,invoice_key FROM bills ORDER BY bill_id")) {
            while (rows.next()) {
                String key = rows.getString("invoice_key");
                if (key != null) {
                    if (!assignedKeys.add(key.toUpperCase(Locale.ROOT))) {
                        pendingKeys.put(rows.getInt("bill_id"), "LEGACY:" + rows.getInt("bill_id"));
                    }
                } else {
                    String candidate = "MONTHLY".equals(rows.getString("invoice_type"))
                            ? "MONTHLY:" + rows.getInt("subscription_id") + ":" + rows.getString("billing_month")
                            : "LEGACY:" + rows.getInt("bill_id");
                    pendingKeys.put(rows.getInt("bill_id"), candidate);
                }
            }
        }
        try (PreparedStatement p = c.prepareStatement("UPDATE bills SET invoice_key=? WHERE bill_id=?")) {
            for (Map.Entry<Integer, String> entry : pendingKeys.entrySet()) {
                String key = entry.getValue();
                // Keep older duplicate invoices and their payment references intact.
                if (!assignedKeys.add(key.toUpperCase(Locale.ROOT))) {
                    key = "LEGACY:" + entry.getKey();
                    while (!assignedKeys.add(key.toUpperCase(Locale.ROOT))) key += ":OLD";
                }
                p.setString(1, key);
                p.setInt(2, entry.getKey());
                p.executeUpdate();
            }
        }
    }
}
