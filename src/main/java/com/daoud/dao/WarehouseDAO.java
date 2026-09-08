package com.daoud.dao;

import com.daoud.db.DatabaseManager_online;
import com.daoud.model.Supplier;
import com.daoud.model.Warehouse;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class WarehouseDAO {

    public static List<Warehouse> getAllWarehouses() {
        List<Warehouse> list = new ArrayList<>();
        String sql = """
            SELECT w.id, w.name, w.manager_user_id, u.username as manager_name
            FROM warehouses w
            LEFT JOIN users u ON w.manager_user_id = u.id
            ORDER BY w.name
        """;
        try (Connection conn = DatabaseManager_online.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                list.add(new Warehouse(
                        rs.getInt("id"),
                        rs.getString("name"),
                        rs.getInt("manager_user_id"),
                        rs.getString("manager_name")
                ));
            }
        } catch (SQLException e) {
            System.err.println("Error: " + e.getMessage());
        }
        return list;
    }

    public static void addWarehouse(String name, int managerUserId) {
        String sql = "INSERT INTO warehouses (name, manager_user_id) VALUES (?, ?)";
        try (Connection conn = DatabaseManager_online.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, name);
            stmt.setInt(2, managerUserId);
            stmt.executeUpdate();
        } catch (SQLException e) {
            System.err.println("Error: " + e.getMessage());
        }
    }

    public static void deleteWarehouse(int id) {
        String sql = "DELETE FROM warehouses WHERE id = ?";
        try (Connection conn = DatabaseManager_online.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, id);
            stmt.executeUpdate();
        } catch (SQLException e) {
            System.err.println("Error: " + e.getMessage());
        }
    }

    public static List<String> getAllManagers() {
        List<String> list = new ArrayList<>();
        String sql = "SELECT id || '|' || username FROM users ORDER BY username";
        try (Connection conn = DatabaseManager_online.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) list.add(rs.getString(1));
        } catch (SQLException e) {
            System.err.println("Error: " + e.getMessage());
        }
        return list;
    }

//    public static void assignSupplierToWarehouse(int warehouseId, int supplierId) {
//        String sql = "INSERT OR IGNORE INTO warehouse_suppliers (warehouse_id, supplier_id) VALUES (?, ?)";
//        try (Connection conn = DatabaseManager_online.getConnection();
//             PreparedStatement stmt = conn.prepareStatement(sql)) {
//            stmt.setInt(1, warehouseId);
//            stmt.setInt(2, supplierId);
//            stmt.executeUpdate();
//        } catch (SQLException e) {
//            System.err.println("Error: " + e.getMessage());
//        }
//    }

    public static void assignSupplierToWarehouse(int warehouseId, int supplierId) {
        String sql = """
        INSERT INTO warehouse_suppliers (warehouse_id, supplier_id)
        VALUES (?, ?)
        ON CONFLICT (warehouse_id, supplier_id) DO NOTHING
        """;

        try (Connection conn = DatabaseManager_online.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setInt(1, warehouseId);
            stmt.setInt(2, supplierId);

            int rows = stmt.executeUpdate();

            System.out.println(
                    "Supplier assignment result: " + rows + " row(s) inserted."
            );

        } catch (SQLException e) {
            System.err.println(
                    "Error assigning supplier to warehouse: " + e.getMessage()
            );
            e.printStackTrace();
        }
    }

    public static void removeSupplierFromWarehouse(int warehouseId, int supplierId) {
        String sql = "DELETE FROM warehouse_suppliers WHERE warehouse_id = ? AND supplier_id = ?";
        try (Connection conn = DatabaseManager_online.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, warehouseId);
            stmt.setInt(2, supplierId);
            stmt.executeUpdate();
        } catch (SQLException e) {
            System.err.println("Error: " + e.getMessage());
        }
    }

    public static List<String> getSuppliersOfWarehouse(int warehouseId) {
        List<String> list = new ArrayList<>();
        String sql = """
            SELECT s.id || '|' || s.name FROM suppliers s
            JOIN warehouse_suppliers ws ON s.id = ws.supplier_id
            WHERE ws.warehouse_id = ?
        """;
        try (Connection conn = DatabaseManager_online.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, warehouseId);
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) list.add(rs.getString(1));
        } catch (SQLException e) {
            System.err.println("Error: " + e.getMessage());
        }
        return list;
    }

    public static Warehouse getWarehouseByManager(int managerUserId) {
        String sql = """
            SELECT w.id, w.name, w.manager_user_id, u.username as manager_name
            FROM warehouses w
            LEFT JOIN users u ON w.manager_user_id = u.id
            WHERE w.manager_user_id = ?
            LIMIT 1
        """;
        try (Connection conn = DatabaseManager_online.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, managerUserId);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                return new Warehouse(
                        rs.getInt("id"),
                        rs.getString("name"),
                        rs.getInt("manager_user_id"),
                        rs.getString("manager_name")
                );
            }
        } catch (SQLException e) {
            System.err.println("Error: " + e.getMessage());
        }
        return null;
    }

    public static List<Supplier> getSuppliersByWarehouse(int warehouseId) {
        List<Supplier> list = new ArrayList<>();
        String sql = """
            SELECT s.id, s.name, s.phone, s.sector, s.floor_amount, s.floor_date
            FROM suppliers s
            JOIN warehouse_suppliers ws ON s.id = ws.supplier_id
            WHERE ws.warehouse_id = ?
            ORDER BY s.name
        """;
        try (Connection conn = DatabaseManager_online.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, warehouseId);
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                list.add(new Supplier(
                        rs.getInt("id"),
                        rs.getString("name"),
                        rs.getString("phone"),
                        rs.getString("sector"),
                        rs.getDouble("floor_amount"),
                        rs.getString("floor_date")
                ));
            }
        } catch (SQLException e) {
            System.err.println("Error: " + e.getMessage());
        }
        return list;
    }

    public static int getWarehouseBySupplier(int supplierId) {
        String sql = "SELECT warehouse_id FROM warehouse_suppliers WHERE supplier_id = ? LIMIT 1";
        try (Connection conn = DatabaseManager_online.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, supplierId);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) return rs.getInt("warehouse_id");
        } catch (SQLException e) {
            System.err.println("Error: " + e.getMessage());
        }
        return -1;
    }
}