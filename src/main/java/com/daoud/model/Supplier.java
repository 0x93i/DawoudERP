package com.daoud.model;

public class Supplier {
    private int id;
    private String name;
    private String phone;
    private String sector;
    private double floorAmount;
    private String floorDate;

    public Supplier(int id, String name, String phone, String sector, double floorAmount, String floorDate) {
        this.id = id;
        this.name = name;
        this.phone = phone;
        this.sector = sector;
        this.floorAmount = floorAmount;
        this.floorDate = floorDate;
    }

    public int getId() { return id; }
    public String getName() { return name; }
    public String getPhone() { return phone; }
    public String getSector() { return sector; }
    public double getFloorAmount() { return floorAmount; }
    public String getFloorDate() { return floorDate; }

    @Override
    public String toString() { return name; }
}