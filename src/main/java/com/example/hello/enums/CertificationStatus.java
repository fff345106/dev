package com.example.hello.enums;

public enum CertificationStatus {
    PENDING("待审核"),
    APPROVED("已通过"),
    REJECTED("已拒绝");

    private final String name;

    CertificationStatus(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }
}
