package com.example.hello.enums;

public enum CollaborationStatus {
    PENDING("待处理"),
    ACCEPTED("已接受"),
    REJECTED("已拒绝"),
    COMPLETED("已完成");

    private final String name;

    CollaborationStatus(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }
}
