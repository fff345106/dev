package com.example.hello.enums;

public enum UserRole {
    SUPER_ADMIN("超级管理员"),
    ADMIN("管理员"),
    USER("录入员");

    private final String name;

    UserRole(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }
}
