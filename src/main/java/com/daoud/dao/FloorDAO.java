package com.daoud.dao;

import com.daoud.db.DatabaseManager_online;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/**
 * حسابات "الأرضية" — رصيد مستقل لكل مورد بيتحكم فيه الأدمن (زيادة/نقصان)،
 * والمورد ممكن يسحب منه من صفحته العادية. ده منفصل تمامًا عن حساب الشغل
 * (المشتريات supplier_transactions والسحوبات supplier_withdrawals)، وبيستخدم
 * نفس عمود suppliers.floor_amount الموجود أصلاً، مع سجل حركات خاص بيه
 * (supplier_floor_transactions) لتتبع كل زيادة/نقصان لوحده.
 */
public class FloorDAO {

    public record FloorRow(int supplierId, String name, double floorAmount) {}

    public record FloorTransaction(int id, String date, String direction, double amount,
                                   String notes, String recordedByName) {}

    public static List<FloorRow> getAllFloors() {
        List<FloorRow> list = new ArrayList<>();
        String sql = "SELECT id, name, floor_amount FROM suppliers ORDER BY name";
        try (Connection conn = DatabaseManager_online.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                list.add(new FloorRow(rs.getInt("id"), rs.getString("name"), rs.getDouble("floor_amount")));
            }
        } catch (SQLException e) {
            System.err.println("FloorDAO error: " + e.getMessage());
        }
        return list;
    }

    public static double getFloorAmount(int supplierId) {
        String sql = "SELECT floor_amount FROM suppliers WHERE id = ?";
        try (Connection conn = DatabaseManager_online.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, supplierId);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) return rs.getDouble(1);
        } catch (SQLException e) {
            System.err.println("FloorDAO error: " + e.getMessage());
        }
        return 0;
    }

    /**
     * تسجيل زيادة أو نقصان في رصيد الأرضية بتاع مورد.
     * direction: "in" (زيادة/إيداع) أو "out" (نقصان/سحب).
     * مسموح إن الرصيد يبقى سالب لو السحب أكبر من المتاح — من غير أي منع.
     */
    public static void adjust(int supplierId, String direction, double amount, String notes, int recordedBy) throws SQLException {
        double delta = direction.equals("in") ? amount : -amount;
        try (Connection conn = DatabaseManager_online.getConnection()) {
            conn.setAutoCommit(false);
            try {
                try (PreparedStatement upd = conn.prepareStatement(
                        "UPDATE suppliers SET floor_amount = floor_amount + ? WHERE id = ?")) {
                    upd.setDouble(1, delta);
                    upd.setInt(2, supplierId);
                    upd.executeUpdate();
                }
                try (PreparedStatement ins = conn.prepareStatement(
                        "INSERT INTO supplier_floor_transactions (supplier_id, transaction_date, direction, amount, notes, recorded_by) " +
                                "VALUES (?, CURRENT_DATE, ?, ?, ?, ?)")) {
                    ins.setInt(1, supplierId);
                    ins.setString(2, direction);
                    ins.setDouble(3, amount);
                    ins.setString(4, notes);
                    ins.setInt(5, recordedBy);
                    ins.executeUpdate();
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

    public static List<FloorTransaction> getHistory(int supplierId) {
        List<FloorTransaction> list = new ArrayList<>();
        String sql = """
            SELECT ft.id, ft.transaction_date, ft.direction, ft.amount, COALESCE(ft.notes,'') AS notes,
                   COALESCE(u.username, '') AS recorded_by_name
            FROM supplier_floor_transactions ft
            LEFT JOIN users u ON ft.recorded_by = u.id
            WHERE ft.supplier_id = ?
            ORDER BY ft.transaction_date DESC, ft.id DESC
            LIMIT 100
        """;
        try (Connection conn = DatabaseManager_online.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, supplierId);
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                list.add(new FloorTransaction(rs.getInt("id"), rs.getString("transaction_date"),
                        rs.getString("direction"), rs.getDouble("amount"), rs.getString("notes"),
                        rs.getString("recorded_by_name")));
            }
        } catch (SQLException e) {
            System.err.println("FloorDAO error: " + e.getMessage());
        }
        return list;
    }
}
