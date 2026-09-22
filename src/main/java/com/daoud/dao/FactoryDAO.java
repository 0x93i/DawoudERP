package com.daoud.dao;

import com.daoud.db.DatabaseManager_online;
import com.daoud.model.Factory;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class FactoryDAO {

    public static List<Factory> getAllFactories() {
        List<Factory> list = new ArrayList<>();
        String sql = "SELECT * FROM factories ORDER BY name";
        try (Connection conn = DatabaseManager_online.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                list.add(new Factory(rs.getInt("id"), rs.getString("name")));
            }
        } catch (SQLException e) {
            System.err.println("Error: " + e.getMessage());
        }
        return list;
    }

    public static void addFactory(String name, int createdBy) {
        String sql = "INSERT INTO factories (name, created_by) VALUES (?, ?)";
        try (Connection conn = DatabaseManager_online.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, name);
            stmt.setInt(2, createdBy);
            stmt.executeUpdate();
        } catch (SQLException e) {
            System.err.println("Error: " + e.getMessage());
        }
    }

    public static void deleteFactory(int id) {
        String sql = "DELETE FROM factories WHERE id = ?";
        try (Connection conn = DatabaseManager_online.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, id);
            stmt.executeUpdate();
        } catch (SQLException e) {
            System.err.println("Error: " + e.getMessage());
        }
    }

    public static void addShipment(int factoryId, String supplierName, double grossWeight,
                                   double deductionPct, double pricePerKg, int recordedBy) {
        double deductionKg = grossWeight * (deductionPct / 100.0);
        double netWeight = grossWeight - deductionKg;
        double total = netWeight * pricePerKg;

        String sql = """
            INSERT INTO factory_shipments 
            (factory_id, shipment_date, supplier_name, gross_weight, deduction_pct, deduction_kg, net_weight, price_per_kg, total_amount, recorded_by)
            VALUES (?, date('now'), ?, ?, ?, ?, ?, ?, ?, ?)
        """;
        try (Connection conn = DatabaseManager_online.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, factoryId);
            stmt.setString(2, supplierName);
            stmt.setDouble(3, grossWeight);
            stmt.setDouble(4, deductionPct);
            stmt.setDouble(5, deductionKg);
            stmt.setDouble(6, netWeight);
            stmt.setDouble(7, pricePerKg);
            stmt.setDouble(8, total);
            stmt.setInt(9, recordedBy);
            stmt.executeUpdate();
        } catch (SQLException e) {
            System.err.println("Error: " + e.getMessage());
        }
    }

    public static void addPayment(int factoryId, double amount, String notes, int recordedBy) {
        String sql = "INSERT INTO factory_payments (factory_id, payment_date, amount, notes, recorded_by) VALUES (?, date('now'), ?, ?, ?)";
        try (Connection conn = DatabaseManager_online.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, factoryId);
            stmt.setDouble(2, amount);
            stmt.setString(3, notes);
            stmt.setInt(4, recordedBy);
            stmt.executeUpdate();
        } catch (SQLException e) {
            System.err.println("Error: " + e.getMessage());
        }
    }

    /**
     * إجمالي الوزن الصافي (كيلو) اللي المصنع أخده من الشحنات المسجلة في الشهر
     * الحالي بس (بيتحسب لايف من التاريخ، فمع أول يوم في الشهر الجديد بيرجع
     * يبدأ من صفر تلقائي من غير أي تصفير يدوي أو جدول إضافي).
     */
    public static double getCurrentMonthNetWeight(int factoryId) {
        java.time.LocalDate now = java.time.LocalDate.now();
        // بنحوّل shipment_date لنص ونقارنه بأول 7 حروف من تاريخ اليوم (yyyy-MM)
        // — بالظبط زي الشكل اللي التاريخ ظاهر بيه في جدول المعاملات — عشان
        // نضمن إن أي شحنة ظاهرة هناك في الشهر ده تتحسب هنا، مهما كان نوع
        // عمود التاريخ في قاعدة البيانات.
        String yearMonth = String.format("%04d-%02d", now.getYear(), now.getMonthValue());
        double total = 0;
        String sql = "SELECT COALESCE(SUM(net_weight), 0) FROM factory_shipments " +
                "WHERE factory_id = ? AND shipment_date::text LIKE ?";
        try (Connection conn = DatabaseManager_online.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, factoryId);
            stmt.setString(2, yearMonth + "%");
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) total = rs.getDouble(1);
        } catch (SQLException e) {
            System.err.println("Error in getCurrentMonthNetWeight: " + e.getMessage());
            e.printStackTrace();
        }
        return total;
    }

    public static double getFactoryBalance(int factoryId) {
        double totalShipments = 0;
        double totalPayments = 0;
        try (Connection conn = DatabaseManager_online.getConnection()) {
            PreparedStatement s1 = conn.prepareStatement(
                    "SELECT COALESCE(SUM(total_amount), 0) FROM factory_shipments WHERE factory_id = ?");
            s1.setInt(1, factoryId);
            ResultSet r1 = s1.executeQuery();
            if (r1.next()) totalShipments = r1.getDouble(1);

            PreparedStatement s2 = conn.prepareStatement(
                    "SELECT COALESCE(SUM(amount), 0) FROM factory_payments WHERE factory_id = ?");
            s2.setInt(1, factoryId);
            ResultSet r2 = s2.executeQuery();
            if (r2.next()) totalPayments = r2.getDouble(1);
        } catch (SQLException e) {
            System.err.println("Error: " + e.getMessage());
        }
        // موجب = المصنع مدين لعم داود
        return totalShipments - totalPayments;
    }
}