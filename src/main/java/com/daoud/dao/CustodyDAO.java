package com.daoud.dao;

import com.daoud.db.DatabaseManager_online;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class CustodyDAO {

    // عم داود بيدي عهدة لمسؤول مخزن
    public static void addCustody(int warehouseId, int givenByUserId, double amount, String notes) {
        String sql = "INSERT INTO custodies (warehouse_id, given_by_user_id, amount, given_date, notes) VALUES (?, ?, ?, date('now'), ?)";
        try (Connection conn = DatabaseManager_online.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, warehouseId);
            stmt.setInt(2, givenByUserId);
            stmt.setDouble(3, amount);
            stmt.setString(4, notes);
            stmt.executeUpdate();
        } catch (SQLException e) {
            System.err.println("Error: " + e.getMessage());
        }
    }

    // إجمالي العهد اللي اتديت لمخزن
    public static double getTotalCustody(int warehouseId) {
        String sql = "SELECT COALESCE(SUM(amount), 0) FROM custodies WHERE warehouse_id = ?";
        try (Connection conn = DatabaseManager_online.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, warehouseId);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) return rs.getDouble(1);
        } catch (SQLException e) {
            System.err.println("Error: " + e.getMessage());
        }
        return 0;
    }

    // إجمالي اللي اتوزع من العهد
    public static double getTotalDisbursed(int warehouseId) {
        String sql = """
            SELECT COALESCE(SUM(cd.amount), 0)
            FROM custody_disbursements cd
            JOIN custodies c ON cd.custody_id = c.id
            WHERE c.warehouse_id = ?
        """;
        try (Connection conn = DatabaseManager_online.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, warehouseId);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) return rs.getDouble(1);
        } catch (SQLException e) {
            System.err.println("Error: " + e.getMessage());
        }
        return 0;
    }

    // المتبقي من العهد
    public static double getRemainingCustody(int warehouseId) {
        return getTotalCustody(warehouseId) - getTotalDisbursed(warehouseId);
    }

    // تسجيل توزيع من العهد
    public static void addDisbursement(int warehouseId, int disbursedByUserId,
                                       String recipientType, int recipientId,
                                       double amount, String notes) {
        // جيب آخر custody للمخزن ده
        String getCustody = "SELECT id FROM custodies WHERE warehouse_id = ? ORDER BY id DESC LIMIT 1";
        try (Connection conn = DatabaseManager_online.getConnection();
             PreparedStatement stmt = conn.prepareStatement(getCustody)) {
            stmt.setInt(1, warehouseId);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                int custodyId = rs.getInt("id");
                String sql = """
                    INSERT INTO custody_disbursements 
                    (custody_id, disbursed_by_user_id, recipient_type, recipient_id, amount, disbursement_date, notes)
                    VALUES (?, ?, ?, ?, ?, date('now'), ?)
                """;
                PreparedStatement s2 = conn.prepareStatement(sql);
                s2.setInt(1, custodyId);
                s2.setInt(2, disbursedByUserId);
                s2.setString(3, recipientType);
                s2.setInt(4, recipientId);
                s2.setDouble(5, amount);
                s2.setString(6, notes);
                s2.executeUpdate();
            }
        } catch (SQLException e) {
            System.err.println("Error: " + e.getMessage());
        }
    }

    // تاريخ العهد للمخزن
    public static List<String> getCustodyHistory(int warehouseId) {
        List<String> list = new ArrayList<>();
        String sql = """
            SELECT c.given_date, c.amount, c.notes
            FROM custodies c
            WHERE c.warehouse_id = ?
            ORDER BY c.given_date DESC LIMIT 30
        """;
        try (Connection conn = DatabaseManager_online.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, warehouseId);
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                list.add(String.format("%s | عهدة: %.1f جنيه | %s",
                        rs.getString("given_date"),
                        rs.getDouble("amount"),
                        rs.getString("notes") != null ? rs.getString("notes") : ""));
            }
        } catch (SQLException e) {
            System.err.println("Error: " + e.getMessage());
        }
        return list;
    }

    // تاريخ التوزيع للمخزن
    public static List<String> getDisbursementHistory(int warehouseId) {
        List<String> list = new ArrayList<>();
        String sql = """
            SELECT cd.disbursement_date, cd.recipient_type, cd.recipient_id, cd.amount, cd.notes
            FROM custody_disbursements cd
            JOIN custodies c ON cd.custody_id = c.id
            WHERE c.warehouse_id = ?
            ORDER BY cd.disbursement_date DESC LIMIT 30
        """;
        try (Connection conn = DatabaseManager_online.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, warehouseId);
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                String type = switch (rs.getString("recipient_type")) {
                    case "supplier" -> "مورد";
                    case "worker" -> "عامل";
                    default -> "أخرى";
                };
                list.add(String.format("%s | %s | %.1f جنيه | %s",
                        rs.getString("disbursement_date"),
                        type,
                        rs.getDouble("amount"),
                        rs.getString("notes") != null ? rs.getString("notes") : ""));
            }
        } catch (SQLException e) {
            System.err.println("Error: " + e.getMessage());
        }
        return list;
    }
}