package com.example.hello.enums;

public enum CertificationType {
    REAL_NAME("实名认证"),
    ENTERPRISE("企业认证"),
    MASTER("技艺认证");

    private final String name;

    CertificationType(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }
}
