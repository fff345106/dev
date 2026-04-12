package com.example.hello.enums;

public enum ImageSourceType {
    TEMP_UPLOAD,
    EXTERNAL,
    LIBRARY;

    public static ImageSourceType fromValue(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        for (ImageSourceType type : values()) {
            if (type.name().equalsIgnoreCase(value.trim())) {
                return type;
            }
        }
        throw new IllegalArgumentException("无效的图片来源类型: " + value);
    }
}
