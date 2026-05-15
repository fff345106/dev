package com.example.hello.enums;

public enum NotificationType {
    SYSTEM("系统通知"),
    LIKE("点赞"),
    FOLLOW("关注"),
    COMMENT("评论"),
    COLLABORATION("协作");

    private final String name;

    NotificationType(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }
}
