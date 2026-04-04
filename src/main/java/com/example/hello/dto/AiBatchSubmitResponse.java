package com.example.hello.dto;

import java.util.List;

public class AiBatchSubmitResponse {
    private int total;
    private int successCount;
    private int failCount;
    private List<AiBatchSubmitItemResult> items;

    public int getTotal() {
        return total;
    }

    public void setTotal(int total) {
        this.total = total;
    }

    public int getSuccessCount() {
        return successCount;
    }

    public void setSuccessCount(int successCount) {
        this.successCount = successCount;
    }

    public int getFailCount() {
        return failCount;
    }

    public void setFailCount(int failCount) {
        this.failCount = failCount;
    }

    public List<AiBatchSubmitItemResult> getItems() {
        return items;
    }

    public void setItems(List<AiBatchSubmitItemResult> items) {
        this.items = items;
    }
}
