package com.example.hello.dto;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;

public class AiBatchConfirmRequest {
    @Valid
    @NotEmpty(message = "确认项不能为空")
    private List<AiBatchConfirmItem> items;

    public List<AiBatchConfirmItem> getItems() {
        return items;
    }

    public void setItems(List<AiBatchConfirmItem> items) {
        this.items = items;
    }
}
