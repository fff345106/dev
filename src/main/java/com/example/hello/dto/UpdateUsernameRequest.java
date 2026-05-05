package com.example.hello.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public class UpdateUsernameRequest {
    @NotBlank(message = "用户名不能为空")
    @Size(min = 1, max = 30, message = "用户名长度必须在1-30个字符之间")
    @Pattern(regexp = "^[a-zA-Z0-9_\\u4e00-\\u9fa5]+$",
             message = "用户名只能包含字母、数字、下划线和中文")
    private String username;

    public UpdateUsernameRequest() {}

    public UpdateUsernameRequest(String username) {
        this.username = username;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }
}
