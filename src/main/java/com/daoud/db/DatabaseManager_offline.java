package com.daoud.db;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

public class DatabaseManager_offline {

    private static final String DB_URL = "jdbc:sqlite:dawoud_app.db";
    private static Connection connection;

    public static Connection getConnection() throws SQLException {
        if (connection == null || connection.isClosed()) {
            connection = DriverManager.getConnection(DB_URL);
        }
        return connection;
    }

    public static void initializeDatabase() {
        try (Statement stmt = getConnection().createStatement()) {

            stmt.execute("""
                CREATE TABLE IF NOT EXISTS users (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    username TEXT NOT NULL UNIQUE,
                    password_hash TEXT NOT NULL,
                    role TEXT NOT NULL CHECK(role IN ('admin','warehouse_manager'))
                )
            """);

            stmt.execute("""
                CREATE TABLE IF NOT EXISTS warehouses (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    name TEXT NOT NULL,
                    manager_user_id INTEGER,
                    FOREIGN KEY (manager_user_id) REFERENCES users(id)
                )
            """);

            stmt.execute("""
                CREATE TABLE IF NOT EXISTS custodies (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    warehouse_id INTEGER NOT NULL,
                    given_by_user_id INTEGER NOT NULL,
                    amount REAL NOT NULL,
                    given_date TEXT NOT NULL,
                    notes TEXT,
                    FOREIGN KEY (warehouse_id) REFERENCES warehouses(id),
                    FOREIGN KEY (given_by_user_id) REFERENCES users(id)
                )
            """);

            stmt.execute("""
                CREATE TABLE IF NOT EXISTS custody_disbursements (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    custody_id INTEGER NOT NULL,
                    disbursed_by_user_id INTEGER NOT NULL,
                    recipient_type TEXT NOT NULL CHECK(recipient_type IN ('supplier','worker','other')),
                    recipient_id INTEGER,
                    amount REAL NOT NULL,
                    disbursement_date TEXT NOT NULL,
                    notes TEXT,
                    FOREIGN KEY (custody_id) REFERENCES custodies(id),
                    FOREIGN KEY (disbursed_by_user_id) REFERENCES users(id)
                )
            """);

            /*
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS suppliers (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    name TEXT NOT NULL,
                    sector TEXT,
                    floor_amount REAL DEFAULT 0,
                    floor_date TEXT,
                    created_by INTEGER,
                    FOREIGN KEY (created_by) REFERENCES users(id)
                )
            """);
            */
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS suppliers (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    name TEXT NOT NULL,
                    phone TEXT,
                    sector TEXT,
                    floor_amount REAL DEFAULT 0,
                    floor_date TEXT,
                    created_by INTEGER,
                    FOREIGN KEY (created_by) REFERENCES users(id)
                )
            """);

            stmt.execute("""
                CREATE TABLE IF NOT EXISTS supplier_transactions (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    supplier_id INTEGER NOT NULL,
                    transaction_date TEXT NOT NULL,
                    gross_weight REAL NOT NULL,
                    deduction_kg REAL DEFAULT 0,
                    price_per_kg REAL NOT NULL,
                    recorded_by INTEGER,
                    FOREIGN KEY (supplier_id) REFERENCES suppliers(id),
                    FOREIGN KEY (recorded_by) REFERENCES users(id)
                )
            """);

            /*
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS supplier_withdrawals (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    supplier_id INTEGER NOT NULL,
                    amount REAL NOT NULL,
                    withdrawal_date TEXT NOT NULL,
                    recorded_by INTEGER,
                    notes TEXT,
                    FOREIGN KEY (supplier_id) REFERENCES suppliers(id),
                    FOREIGN KEY (recorded_by) REFERENCES users(id)
                )
            """);
             */

            stmt.execute("""
                CREATE TABLE IF NOT EXISTS supplier_withdrawals (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    supplier_id INTEGER NOT NULL,
                    amount REAL NOT NULL,
                    withdrawal_date TEXT NOT NULL,
                    payment_method TEXT DEFAULT 'كاش',
                    recorded_by INTEGER,
                    notes TEXT,
                    FOREIGN KEY (supplier_id) REFERENCES suppliers(id),
                    FOREIGN KEY (recorded_by) REFERENCES users(id)
                )
            """);

            stmt.execute("""
                CREATE TABLE IF NOT EXISTS supplier_settlements (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    supplier_id INTEGER NOT NULL,
                    settlement_date TEXT NOT NULL,
                    balance_before REAL NOT NULL,
                    amount_paid REAL NOT NULL,
                    new_floor REAL NOT NULL,
                    recorded_by INTEGER,
                    FOREIGN KEY (supplier_id) REFERENCES suppliers(id),
                    FOREIGN KEY (recorded_by) REFERENCES users(id)
                )
            """);

            stmt.execute("""
                CREATE TABLE IF NOT EXISTS warehouse_suppliers (
                    warehouse_id INTEGER NOT NULL,
                    supplier_id INTEGER NOT NULL,
                    PRIMARY KEY (warehouse_id, supplier_id),
                    FOREIGN KEY (warehouse_id) REFERENCES warehouses(id),
                    FOREIGN KEY (supplier_id) REFERENCES suppliers(id)
                )
            """);

            stmt.execute("""
                CREATE TABLE IF NOT EXISTS warehouse_stock_entries (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    warehouse_id INTEGER NOT NULL,
                    supplier_id INTEGER NOT NULL,
                    entry_date TEXT NOT NULL,
                    total_weight REAL NOT NULL,
                    recorded_by INTEGER,
                    FOREIGN KEY (warehouse_id) REFERENCES warehouses(id),
                    FOREIGN KEY (supplier_id) REFERENCES suppliers(id),
                    FOREIGN KEY (recorded_by) REFERENCES users(id)
                )
            """);

            stmt.execute("""
                CREATE TABLE IF NOT EXISTS warehouse_stock_exits (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    warehouse_id INTEGER NOT NULL,
                    exit_date TEXT NOT NULL,
                    weight_green REAL DEFAULT 0,
                    weight_colored REAL DEFAULT 0,
                    weight_white REAL DEFAULT 0,
                    weight_waste REAL DEFAULT 0,
                    destination TEXT,
                    recorded_by INTEGER,
                    FOREIGN KEY (warehouse_id) REFERENCES warehouses(id),
                    FOREIGN KEY (recorded_by) REFERENCES users(id)
                )
            """);

           /*
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS workers (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    name TEXT NOT NULL,
                    warehouse_id INTEGER,
                    daily_wage REAL DEFAULT 0,
                    created_by INTEGER,
                    FOREIGN KEY (warehouse_id) REFERENCES warehouses(id),
                    FOREIGN KEY (created_by) REFERENCES users(id)
                )
            """);
            */

            stmt.execute("""
                CREATE TABLE IF NOT EXISTS workers (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    name TEXT NOT NULL,
                    phone TEXT,
                    warehouse_id INTEGER,
                    daily_wage REAL DEFAULT 0,
                    created_by INTEGER,
                    FOREIGN KEY (warehouse_id) REFERENCES warehouses(id),
                    FOREIGN KEY (created_by) REFERENCES users(id)
                )
            """);

            stmt.execute("""
                CREATE TABLE IF NOT EXISTS worker_attendance (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    worker_id INTEGER NOT NULL,
                    work_date TEXT NOT NULL,
                    daily_wage REAL NOT NULL,
                    recorded_by INTEGER,
                    FOREIGN KEY (worker_id) REFERENCES workers(id),
                    FOREIGN KEY (recorded_by) REFERENCES users(id)
                )
            """);

            /*
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS worker_withdrawals (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    worker_id INTEGER NOT NULL,
                    amount REAL NOT NULL,
                    withdrawal_date TEXT NOT NULL,
                    recorded_by INTEGER,
                    notes TEXT,
                    FOREIGN KEY (worker_id) REFERENCES workers(id),
                    FOREIGN KEY (recorded_by) REFERENCES users(id)
                )
            """);
            */

            stmt.execute("""
                CREATE TABLE IF NOT EXISTS worker_withdrawals (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    worker_id INTEGER NOT NULL,
                    amount REAL NOT NULL,
                    withdrawal_date TEXT NOT NULL,
                    payment_method TEXT DEFAULT 'كاش',
                    recorded_by INTEGER,
                    notes TEXT,
                    FOREIGN KEY (worker_id) REFERENCES workers(id),
                    FOREIGN KEY (recorded_by) REFERENCES users(id)
                )
            """);

            stmt.execute("""
                CREATE TABLE IF NOT EXISTS factories (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    name TEXT NOT NULL,
                    created_by INTEGER,
                    FOREIGN KEY (created_by) REFERENCES users(id)
                )
            """);

            stmt.execute("""
                CREATE TABLE IF NOT EXISTS factory_shipments (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    factory_id INTEGER NOT NULL,
                    shipment_date TEXT NOT NULL,
                    supplier_name TEXT,
                    gross_weight REAL NOT NULL,
                    deduction_pct REAL DEFAULT 0,
                    deduction_kg REAL DEFAULT 0,
                    net_weight REAL NOT NULL,
                    price_per_kg REAL NOT NULL,
                    total_amount REAL NOT NULL,
                    recorded_by INTEGER,
                    FOREIGN KEY (factory_id) REFERENCES factories(id),
                    FOREIGN KEY (recorded_by) REFERENCES users(id)
                )
            """);

            stmt.execute("""
                CREATE TABLE IF NOT EXISTS factory_payments (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    factory_id INTEGER NOT NULL,
                    payment_date TEXT NOT NULL,
                    amount REAL NOT NULL,
                    notes TEXT,
                    recorded_by INTEGER,
                    FOREIGN KEY (factory_id) REFERENCES factories(id),
                    FOREIGN KEY (recorded_by) REFERENCES users(id)
                )
            """);

            stmt.execute("""
                CREATE TABLE IF NOT EXISTS shipments (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    shipment_number TEXT NOT NULL,
                    supplier_id INTEGER REFERENCES suppliers(id),
                    factory_id INTEGER REFERENCES factories(id),
                    shipment_date TEXT NOT NULL,
                    gross_weight REAL NOT NULL,
                    deduction_kg REAL DEFAULT 0,
                    net_weight REAL NOT NULL,
                    price_per_kg REAL NOT NULL,
                    total_amount REAL NOT NULL,
                    cost_loading REAL DEFAULT 0,
                    cost_workers REAL DEFAULT 0,
                    cost_fuel REAL DEFAULT 0,
                    cost_transport REAL DEFAULT 0,
                    cost_other REAL DEFAULT 0,
                    status TEXT DEFAULT 'pending' CHECK(status IN ('pending','completed','partial')),
                    recorded_by INTEGER REFERENCES users(id)
                )
            """);

            System.out.println("Database initialized successfully.");

        } catch (SQLException e) {
            System.err.println("Database initialization error: " + e.getMessage());
        }
    }

    public static void createDefaultAdmin() {
        String check = "SELECT COUNT(*) FROM users WHERE username = 'admin'";
        String insert = "INSERT INTO users (username, password_hash, role) VALUES ('admin', 'admin123', 'admin')";
        try (Connection conn = getConnection();
             PreparedStatement checkStmt = conn.prepareStatement(check)) {
            ResultSet rs = checkStmt.executeQuery();
            if (rs.next() && rs.getInt(1) == 0) {
                conn.createStatement().execute(insert);
                System.out.println("Default admin created.");
            }
        } catch (SQLException e) {
            System.err.println("Error creating admin: " + e.getMessage());
        }
    }
}