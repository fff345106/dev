package com.example.hello.enums;

public enum AuditStatus {
    PENDING("待审核"),
    APPROVED("已通过"),
    REJECTED("已拒绝");

    private final String name;

    AuditStatus(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }
}
