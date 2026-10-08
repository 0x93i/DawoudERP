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

    /**
     * بتغيّر مسؤول المخزن لمستخدم تاني (بتستبدل أي مسؤول حالي). بتستخدم
     * لما عم داود يضيف مستخدم جديد كـ"مسؤول مخزن" ويحدد مخزن موجود يبقى
     * مسؤول عنه، أو لما يحب يغيّر مسؤول مخزن قائم.
     */
    public static void updateWarehouseManager(int warehouseId, int managerUserId) {
        String sql = "UPDATE warehouses SET manager_user_id = ? WHERE id = ?";
        try (Connection conn = DatabaseManager_online.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, managerUserId);
            stmt.setInt(2, warehouseId);
            stmt.executeUpdate();
        } catch (SQLException e) {
            System.err.println("Error: " + e.getMessage());
        }
    }

    /** بتشيل مسؤول المخزن الحالي (لو هو انتقل لمخزن تاني أو بقى أدمن). */
    public static void clearWarehouseManager(int warehouseId) {
        String sql = "UPDATE warehouses SET manager_user_id = NULL WHERE id = ?";
        try (Connection conn = DatabaseManager_online.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, warehouseId);
            stmt.executeUpdate();
        } catch (SQLException e) {
            System.err.println("Error: " + e.getMessage());
        }
    }

    public static void deleteWarehouse(int id) {
        // حذف الخزنة المرتبطة
        String deleteVault = "DELETE FROM vaults WHERE owner_type = 'warehouse' AND owner_id = ?";
        try (Connection conn = DatabaseManager_online.getConnection();
             PreparedStatement vStmt = conn.prepareStatement(deleteVault)) {
            vStmt.setInt(1, id);
            vStmt.executeUpdate();
        } catch (SQLException e) {
            System.err.println("Error deleting vault: " + e.getMessage());
        }

        // حذف المخزن
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
            SELECT s.id, s.name, s.phone, s.sector, s.floor_amount, s.floor_date, s.supplier_no
            FROM suppliers s
            JOIN warehouse_suppliers ws ON s.id = ws.supplier_id
            WHERE ws.warehouse_id = ?
            ORDER BY s.supplier_no NULLS LAST, s.name
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
                        rs.getString("floor_date"),
                        rs.getInt("supplier_no")
                ));
            }
        } catch (SQLException e) {
            System.err.println("Error: " + e.getMessage());
        }
        return list;
    }

    /**
     * تسجيل نقل بضاعة من مخزن لمخزن تاني (مثلاً مخزن شكري بيودي لمخزن محمد).
     * بيتسجل في جدول warehouse_transfers المستقل عشان النقل الداخلي ده مالوش
     * مورد حقيقي يتسجل بيه زي warehouse_stock_entries العادي.
     */
    public static void recordWarehouseTransfer(int fromWarehouseId, int toWarehouseId,
                                               double green, double colored, double white, double waste,
                                               double pricePerKg, double purchaseTotal,
                                               double costLoading, double costWorkers, double costFuel, double costTransport, double costOther,
                                               int recordedBy) {
        double totalCost = costLoading + costWorkers + costFuel + costTransport + costOther;
        double landedTotal = purchaseTotal + totalCost;
        String sql = "INSERT INTO warehouse_transfers " +
                "(from_warehouse_id, to_warehouse_id, weight_green, weight_colored, weight_white, weight_waste, " +
                "price_per_kg, purchase_total, cost_loading, cost_workers, cost_fuel, cost_transport, cost_other, " +
                "total_cost, landed_total, recorded_by) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        try (Connection conn = DatabaseManager_online.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, fromWarehouseId);
            stmt.setInt(2, toWarehouseId);
            stmt.setDouble(3, green);
            stmt.setDouble(4, colored);
            stmt.setDouble(5, white);
            stmt.setDouble(6, waste);
            stmt.setDouble(7, pricePerKg);
            stmt.setDouble(8, purchaseTotal);
            stmt.setDouble(9, costLoading);
            stmt.setDouble(10, costWorkers);
            stmt.setDouble(11, costFuel);
            stmt.setDouble(12, costTransport);
            stmt.setDouble(13, costOther);
            stmt.setDouble(14, totalCost);
            stmt.setDouble(15, landedTotal);
            stmt.setInt(16, recordedBy);
            stmt.executeUpdate();
        } catch (SQLException e) {
            System.err.println("Error: " + e.getMessage());
        }
    }

    /** إجمالي الوزن الداخل للمخزن ده عن طريق تحويلات من مخازن تانية. */
    public static double getIncomingTransfersWeight(int warehouseId) {
        String sql = "SELECT COALESCE(SUM(weight_green + weight_colored + weight_white + weight_waste), 0) AS total " +
                "FROM warehouse_transfers WHERE to_warehouse_id = ?";
        try (Connection conn = DatabaseManager_online.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, warehouseId);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) return rs.getDouble("total");
        } catch (SQLException e) {
            System.err.println("Error: " + e.getMessage());
        }
        return 0;
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