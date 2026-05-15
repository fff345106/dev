package com.example.hello.enums;

public enum NotificationTargetType {
    PATTERN("纹样"),
    ARTICLE("文章");

    private final String name;

    NotificationTargetType(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }
}
