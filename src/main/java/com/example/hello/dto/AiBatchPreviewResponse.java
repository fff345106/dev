package com.example.hello.dto;

import java.util.List;

public class AiBatchPreviewResponse {
    private int total;
    private int validCount;
    private int invalidCount;
    private List<AiBatchPreviewItem> items;

    public int getTotal() {
        return total;
    }

    public void setTotal(int total) {
        this.total = total;
    }

    public int getValidCount() {
        return validCount;
    }

    public void setValidCount(int validCount) {
        this.validCount = validCount;
    }

    public int getInvalidCount() {
        return invalidCount;
    }

    public void setInvalidCount(int invalidCount) {
        this.invalidCount = invalidCount;
    }

    public List<AiBatchPreviewItem> getItems() {
        return items;
    }

    public void setItems(List<AiBatchPreviewItem> items) {
        this.items = items;
    }
}
