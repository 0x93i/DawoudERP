package com.daoud.dao;

import com.daoud.db.DatabaseManager_online;
import com.daoud.model.Supplier;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class SupplierDAO {

    public static List<Supplier> getAllSuppliers() {
        List<Supplier> list = new ArrayList<>();
        // الترتيب برقم المورد (الأهم) وبعدين بالاسم لأي حد لسه من غير رقم
        String sql = "SELECT * FROM suppliers ORDER BY supplier_no NULLS LAST, name";
        try (Connection conn = DatabaseManager_online.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
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

    /** بيرجع أول رقم متاح للمورد الجديد (أكبر رقم موجود + 1، أو 1 لو مفيش حد لسه). */
    public static int getNextSupplierNo() throws SQLException {
        String sql = "SELECT COALESCE(MAX(supplier_no), 0) + 1 FROM suppliers";
        try (Connection conn = DatabaseManager_online.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            if (rs.next()) return rs.getInt(1);
        }
        return 1;
    }

    public static void addSupplier(String name, String phone, String sector, double floorAmount, int createdBy) {
        String sql = "INSERT INTO suppliers (name, phone, sector, floor_amount, floor_date, created_by, supplier_no) VALUES (?, ?, ?, ?, date('now'), ?, ?)";
        try (Connection conn = DatabaseManager_online.getConnection()) {
            conn.setAutoCommit(false);
            try {
                int nextNo;
                // بنحسب الرقم الجديد جوه نفس الترانزاكشن عشان نقفل السباق بين طلبين إضافة في نفس اللحظة
                try (Statement s = conn.createStatement();
                     ResultSet rs = s.executeQuery("SELECT COALESCE(MAX(supplier_no), 0) + 1 FROM suppliers")) {
                    nextNo = rs.next() ? rs.getInt(1) : 1;
                }
                try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                    stmt.setString(1, name);
                    stmt.setString(2, phone);
                    stmt.setString(3, sector);
                    stmt.setDouble(4, floorAmount);
                    stmt.setInt(5, createdBy);
                    stmt.setInt(6, nextNo);
                    stmt.executeUpdate();
                }
                conn.commit();
            } catch (SQLException e) {
                conn.rollback();
                throw e;
            } finally {
                conn.setAutoCommit(true);
            }
        } catch (SQLException e) {
            System.err.println("Error: " + e.getMessage());
        }
    }

    public static void deleteSupplier(int id) {
        String sql = "DELETE FROM suppliers WHERE id = ?";
        try (Connection conn = DatabaseManager_online.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, id);
            stmt.executeUpdate();
        } catch (SQLException e) {
            System.err.println("Error: " + e.getMessage());
        }
    }

    public static void updateSupplier(int id, String name, String phone, String sector) throws SQLException {
        String sql = "UPDATE suppliers SET name = ?, phone = ?, sector = ? WHERE id = ?";
        try (Connection conn = DatabaseManager_online.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, name);
            stmt.setString(2, phone);
            stmt.setString(3, sector);
            stmt.setInt(4, id);
            stmt.executeUpdate();
        }
    }

    /**
     * حذف مورد بالكامل: بيتشال من أي مخزن مرتبط بيه، وبيتشال كل بيانات حسابه
     * (بضاعة/سحوبات/تسويات/أرضية)، لكن سجلات الشحنات (shipments/shipment_suppliers)
     * بتفضل زي ما هي — لو الفورين كي على suppliers.id متظبط على ON DELETE SET NULL
     * هيبقى اسم المورد يبقى فاضي في الشحنة القديمة من غير ما تتمسح الشحنة نفسها.
     */
    public static void deleteSupplierFully(int supplierId) throws SQLException {
        try (Connection conn = DatabaseManager_online.getConnection()) {
            conn.setAutoCommit(false);
            try {
                String[] cleanupSql = {
                        "DELETE FROM warehouse_suppliers WHERE supplier_id = ?",
                        "DELETE FROM supplier_transactions WHERE supplier_id = ?",
                        "DELETE FROM supplier_withdrawals WHERE supplier_id = ?",
                        "DELETE FROM supplier_floor_transactions WHERE supplier_id = ?",
                        "DELETE FROM supplier_settlements WHERE supplier_id = ?"
                };
                for (String sql : cleanupSql) {
                    try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                        stmt.setInt(1, supplierId);
                        stmt.executeUpdate();
                    } catch (SQLException ignored) {
                        // لو الجدول ده مش موجود عند بعض النسخ، تجاهل واكمل
                    }
                }
                try (PreparedStatement del = conn.prepareStatement("DELETE FROM suppliers WHERE id = ?")) {
                    del.setInt(1, supplierId);
                    del.executeUpdate();
                }
                conn.commit();
            } catch (SQLException e) {
                conn.rollback();
                throw e;
            } finally {
                conn.setAutoCommit(true);
            }
        }
    }

    public static double getSupplierBalance(int supplierId) {
        double floor = 0;
        double totalPurchases = 0;
        double totalWithdrawals = 0;

        try (Connection conn = DatabaseManager_online.getConnection()) {
            PreparedStatement fs = conn.prepareStatement(
                    "SELECT floor_amount FROM suppliers WHERE id = ?");
            fs.setInt(1, supplierId);
            ResultSet frs = fs.executeQuery();
            if (frs.next()) floor = frs.getDouble("floor_amount");

            PreparedStatement ps = conn.prepareStatement(
                    "SELECT COALESCE(SUM((gross_weight - deduction_kg) * price_per_kg), 0) FROM supplier_transactions WHERE supplier_id = ?");
            ps.setInt(1, supplierId);
            ResultSet prs = ps.executeQuery();
            if (prs.next()) totalPurchases = prs.getDouble(1);

            PreparedStatement ws = conn.prepareStatement(
                    "SELECT COALESCE(SUM(amount), 0) FROM supplier_withdrawals WHERE supplier_id = ?");
            ws.setInt(1, supplierId);
            ResultSet wrs = ws.executeQuery();
            if (wrs.next()) totalWithdrawals = wrs.getDouble(1);

        } catch (SQLException e) {
            System.err.println("Error: " + e.getMessage());
        }

        return floor - totalPurchases + totalWithdrawals;
    }

    public static void settleSupplier(int supplierId, double amountPaid, double newFloor, int userId) {
        double balanceBefore = getSupplierBalance(supplierId);
        String insertSettlement = """
            INSERT INTO supplier_settlements 
            (supplier_id, settlement_date, balance_before, amount_paid, new_floor, recorded_by)
            VALUES (?, date('now'), ?, ?, ?, ?)
        """;
        String updateFloor = "UPDATE suppliers SET floor_amount = ?, floor_date = date('now') WHERE id = ?";

        try (Connection conn = DatabaseManager_online.getConnection()) {
            conn.setAutoCommit(false);
            try {
                PreparedStatement s1 = conn.prepareStatement(insertSettlement);
                s1.setInt(1, supplierId);
                s1.setDouble(2, balanceBefore);
                s1.setDouble(3, amountPaid);
                s1.setDouble(4, newFloor);
                s1.setInt(5, userId);
                s1.executeUpdate();

                PreparedStatement s2 = conn.prepareStatement(updateFloor);
                s2.setDouble(1, newFloor);
                s2.setInt(2, supplierId);
                s2.executeUpdate();

                PreparedStatement s3 = conn.prepareStatement("DELETE FROM supplier_transactions WHERE supplier_id = ?");
                s3.setInt(1, supplierId);
                s3.executeUpdate();

                PreparedStatement s4 = conn.prepareStatement("DELETE FROM supplier_withdrawals WHERE supplier_id = ?");
                s4.setInt(1, supplierId);
                s4.executeUpdate();

                conn.commit();
            } catch (SQLException e) {
                conn.rollback();
                throw e;
            } finally {
                conn.setAutoCommit(true);
            }
        } catch (SQLException e) {
            System.err.println("Settlement error: " + e.getMessage());
        }
    }
}