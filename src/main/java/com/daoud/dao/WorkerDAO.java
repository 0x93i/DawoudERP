package com.daoud.dao;

import com.daoud.db.DatabaseManager_online;
import com.daoud.model.Worker;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class WorkerDAO {

    public static List<Worker> getWorkersByWarehouse(int warehouseId) {
        List<Worker> list = new ArrayList<>();
        String sql;
//        if (warehouseId == -1) {
//            sql = "SELECT * FROM workers WHERE warehouse_id IS NULL OR warehouse_id = 0 ORDER BY name";
//        } else {
//            sql = "SELECT * FROM workers WHERE warehouse_id = ? ORDER BY name";
//        }
        if (warehouseId == -1) {
            sql = "SELECT * FROM workers WHERE warehouse_id IS NULL ORDER BY name";
        } else {
            sql = "SELECT * FROM workers WHERE warehouse_id = ? ORDER BY name";
        }
        try (Connection conn = DatabaseManager_online.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            if (warehouseId != -1) stmt.setInt(1, warehouseId);
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                list.add(new Worker(
                        rs.getInt("id"),
                        rs.getString("name"),
                        rs.getString("phone"),
                        rs.getInt("warehouse_id"),
                        rs.getDouble("daily_wage")));
            }
        } catch (SQLException e) {
            System.err.println("Error: " + e.getMessage());
        }
        return list;
    }

//    public static void addWorker(String name, String phone, int warehouseId, double dailyWage, int createdBy) {
//        String sql = "INSERT INTO workers (name, phone, warehouse_id, daily_wage, created_by) VALUES (?, ?, ?, ?, ?)";
//        try (Connection conn = DatabaseManager_online.getConnection();
//             PreparedStatement stmt = conn.prepareStatement(sql)) {
//            stmt.setString(1, name);
//            stmt.setString(2, phone);
//            stmt.setInt(3, warehouseId);
//            stmt.setDouble(4, dailyWage);
//            stmt.setInt(5, createdBy);
//            stmt.executeUpdate();
//        } catch (SQLException e) {
//            System.err.println("Error: " + e.getMessage());
//        }
//    }
    public static void addWorker(String name, String phone, Integer warehouseId, double dailyWage, int createdBy) {
        String sql = """
            INSERT INTO workers
            (name, phone, warehouse_id, daily_wage, created_by)
            VALUES (?, ?, ?, ?, ?)
            """;

        try (Connection conn = DatabaseManager_online.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, name);
            stmt.setString(2, phone);

            if (warehouseId == null) {
                stmt.setNull(3, Types.INTEGER);
            } else {
                stmt.setInt(3, warehouseId);
            }

            stmt.setDouble(4, dailyWage);
            stmt.setInt(5, createdBy);

            stmt.executeUpdate();

        } catch (SQLException e) {
            System.err.println("Error adding worker: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public static void deleteWorker(int id) {
        String sql = "DELETE FROM workers WHERE id = ?";
        try (Connection conn = DatabaseManager_online.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, id);
            stmt.executeUpdate();
        } catch (SQLException e) {
            System.err.println("Error: " + e.getMessage());
        }
    }

    public static void recordAttendance(int workerId, double dailyWage, int recordedBy) {
//        String sql = "INSERT INTO worker_attendance (worker_id, work_date, daily_wage, recorded_by) VALUES (?, date('now'), ?, ?)";
        String sql = """
    INSERT INTO worker_attendance
    (worker_id, work_date, daily_wage, recorded_by)
    VALUES (?, CURRENT_DATE, ?, ?)
    """;
        try (Connection conn = DatabaseManager_online.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, workerId);
            stmt.setDouble(2, dailyWage);
            stmt.setInt(3, recordedBy);
            stmt.executeUpdate();
        } catch (SQLException e) {
            System.err.println("Error: " + e.getMessage());
        }
    }

    public static void addWithdrawal(int workerId, double amount, String paymentMethod, int recordedBy, String notes) {
//        String sql = "INSERT INTO worker_withdrawals (worker_id, amount, withdrawal_date, payment_method, recorded_by, notes) VALUES (?, ?, date('now'), ?, ?, ?)";
        String sql = """
    INSERT INTO worker_withdrawals
    (worker_id, amount, withdrawal_date, payment_method, recorded_by, notes)
    VALUES (?, ?, CURRENT_DATE, ?, ?, ?)
    """;
        try (Connection conn = DatabaseManager_online.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, workerId);
            stmt.setDouble(2, amount);
            stmt.setString(3, paymentMethod);
            stmt.setInt(4, recordedBy);
            stmt.setString(5, notes);
            stmt.executeUpdate();
        } catch (SQLException e) {
            System.err.println("Error: " + e.getMessage());
        }
    }

    public static double getTotalEarned(int workerId) {
        String sql = "SELECT COALESCE(SUM(daily_wage), 0) FROM worker_attendance WHERE worker_id = ?";
        try (Connection conn = DatabaseManager_online.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, workerId);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) return rs.getDouble(1);
        } catch (SQLException e) {
            System.err.println("Error: " + e.getMessage());
        }
        return 0;
    }

    public static double getTotalWithdrawn(int workerId) {
        String sql = "SELECT COALESCE(SUM(amount), 0) FROM worker_withdrawals WHERE worker_id = ?";
        try (Connection conn = DatabaseManager_online.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, workerId);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) return rs.getDouble(1);
        } catch (SQLException e) {
            System.err.println("Error: " + e.getMessage());
        }
        return 0;
    }

    public static int getWorkDays(int workerId) {
        String sql = "SELECT COUNT(*) FROM worker_attendance WHERE worker_id = ?";
        try (Connection conn = DatabaseManager_online.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, workerId);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) return rs.getInt(1);
        } catch (SQLException e) {
            System.err.println("Error: " + e.getMessage());
        }
        return 0;
    }

    public static void settleWorker(int workerId) {
        try (Connection conn = DatabaseManager_online.getConnection()) {
            conn.setAutoCommit(false);
            try {
                PreparedStatement s1 = conn.prepareStatement("DELETE FROM worker_attendance WHERE worker_id = ?");
                s1.setInt(1, workerId); s1.executeUpdate();
                PreparedStatement s2 = conn.prepareStatement("DELETE FROM worker_withdrawals WHERE worker_id = ?");
                s2.setInt(1, workerId); s2.executeUpdate();
                conn.commit();
            } catch (SQLException e) {
                conn.rollback(); throw e;
            } finally {
                conn.setAutoCommit(true);
            }
        } catch (SQLException e) {
            System.err.println("Error: " + e.getMessage());
        }
    }
}