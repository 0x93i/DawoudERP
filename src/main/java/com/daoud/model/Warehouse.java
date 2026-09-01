package com.daoud.model;

public class Warehouse {
    private int id;
    private String name;
    private int managerUserId;
    private String managerName;

    public Warehouse(int id, String name, int managerUserId, String managerName) {
        this.id = id;
        this.name = name;
        this.managerUserId = managerUserId;
        this.managerName = managerName;
    }

    public int getId() { return id; }
    public String getName() { return name; }
    public int getManagerUserId() { return managerUserId; }
    public String getManagerName() { return managerName; }

    @Override
    public String toString() {
        return name + (managerName != null ? " — " + managerName : "");
    }
}