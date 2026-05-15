package com.example.hello.enums;

public enum ArticleStatus {
    DRAFT("草稿"),
    PUBLISHED("已发布"),
    ARCHIVED("已归档");

    private final String name;

    ArticleStatus(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }
}
