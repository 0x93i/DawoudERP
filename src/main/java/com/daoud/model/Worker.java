package com.daoud.model;

public class Worker {
    private int id;
    private String name;
    private String phone;
    private int warehouseId;
    private double dailyWage;

    public Worker(int id, String name, String phone, int warehouseId, double dailyWage) {
        this.id = id;
        this.name = name;
        this.phone = phone;
        this.warehouseId = warehouseId;
        this.dailyWage = dailyWage;
    }

    public int getId() { return id; }
    public String getName() { return name; }
    public String getPhone() { return phone; }
    public int getWarehouseId() { return warehouseId; }
    public double getDailyWage() { return dailyWage; }

    @Override
    public String toString() { return name; }
}