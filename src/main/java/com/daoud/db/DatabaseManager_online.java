package com.daoud.db;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

public class DatabaseManager_online {

    private static final String PASSWORD = "lprPhmnQqNXxs4By";

    private static HikariDataSource dataSource;

    static {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl("jdbc:postgresql://aws-1-eu-central-1.pooler.supabase.com:5432/postgres?sslmode=require");
        config.setUsername("postgres.oamzyslkqnewmtrwnoep");
        config.setPassword(PASSWORD);
        config.setMaximumPoolSize(5);
        config.setMinimumIdle(1);
        config.setConnectionTimeout(10000);
        config.setIdleTimeout(300000);
        config.setMaxLifetime(600000);
        config.setConnectionTestQuery("SELECT 1");
        dataSource = new HikariDataSource(config);
    }

    public static Connection getConnection() throws SQLException {
        return dataSource.getConnection();
    }

    public static void initializeDatabase() {
        try (Connection conn = getConnection()) {
            System.out.println("Connected to Supabase ✓");
        } catch (SQLException e) {
            System.err.println("Connection error: " + e.getMessage());
        }
    }

    public static void createDefaultAdmin() {
        String insert = """
            INSERT INTO users (username, password_hash, role)
            VALUES ('admin', 'admin123', 'admin')
            ON CONFLICT (username) DO NOTHING
        """;
        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute(insert);
        } catch (SQLException e) {
            System.err.println("Error creating admin: " + e.getMessage());
        }
    }
}