package com.daoud.db;

import java.sql.*;

public class VaultHelper {

    // جيب الـ vault_id بتاع مخزن معين
    public static int getWarehouseTreasuryId(int warehouseId) {
        String sql = "SELECT id FROM vaults WHERE owner_type = 'warehouse' AND owner_id = ?";
        try (Connection conn = DatabaseManager_online.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, warehouseId);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) return rs.getInt("id");
        } catch (SQLException e) {
            System.err.println("VaultHelper error: " + e.getMessage());
        }
        return -1;
    }

    // جيب الـ vault_id بتاع الأدمن
    public static int getAdminTreasuryId() {
        String sql = "SELECT id FROM vaults WHERE owner_type = 'admin' LIMIT 1";
        try (Connection conn = DatabaseManager_online.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            if (rs.next()) return rs.getInt("id");
        } catch (SQLException e) {
            System.err.println("VaultHelper error: " + e.getMessage());
        }
        return 1; // default
    }

    // تسجيل عملية في الخزنة
    public static void record(int treasuryId, String direction, String paymentType,
                              double amount, String category, String notes, int recordedBy) {
        if (treasuryId == -1 || amount <= 0) return;
        String sql = "INSERT INTO vault_transactions " +
                "(vault_id, transaction_date, direction, payment_type, amount, category, notes, recorded_by) " +
                "VALUES (?, CURRENT_DATE, ?, ?, ?, ?, ?, ?)";
        try (Connection conn = DatabaseManager_online.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, treasuryId);
            stmt.setString(2, direction); // 'in' or 'out'
            stmt.setString(3, paymentType); // 'cash', 'bank', 'wallet', 'check'
            stmt.setDouble(4, amount);
            stmt.setString(5, category);
            stmt.setString(6, notes);
            stmt.setInt(7, recordedBy);
            stmt.executeUpdate();
        } catch (SQLException e) {
            System.err.println("VaultHelper record error: " + e.getMessage());
        }
    }

    // تحويل من نوع الدفع العربي لـ enum
    public static String toPaymentType(String arabic) {
        return switch (arabic) {
            case "بنك", "تحويل بنكي" -> "bank";
            case "محفظة" -> "wallet";
            case "شيك" -> "check";
            default -> "cash";
        };
    }
    public static double getBalance(int vaultId, String paymentType) {
        String sql = "SELECT COALESCE(SUM(CASE WHEN direction='in' THEN amount ELSE -amount END), 0) " +
                "FROM vault_transactions WHERE vault_id = ? AND payment_type = ?";
        try (Connection conn = DatabaseManager_online.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, vaultId);
            stmt.setString(2, paymentType);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) return rs.getDouble(1);
        } catch (SQLException e) {
            System.err.println("Balance check error: " + e.getMessage());
        }
        return 0;
    }
}