package com.daoud.model;

public class Factory {
    private int id;
    private String name;

    public Factory(int id, String name) {
        this.id = id;
        this.name = name;
    }

    public int getId() { return id; }
    public String getName() { return name; }

    @Override
    public String toString() { return name; }
}