package com.example.hello.enums;

public enum UserRole {
    SUPER_ADMIN("超级管理员"),
    ADMIN("管理员"),
    USER("录入员"),
    GUEST("游客"),
    REGULAR_USER("普通用户"),
    ENTERPRISE_USER("企商用户"),
    MASTER_ARTISAN("技艺大师");

    private final String name;

    UserRole(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }
}
