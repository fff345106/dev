package com.example.hello.dto;

import java.util.List;

public class AiBatchPreviewTaskProgressResponse {
    private String taskId;
    private String status;
    private int total;
    private int processed;
    private int validCount;
    private int invalidCount;
    private int progressPercent;
    private boolean completed;
    private String error;
    private long startedAtEpochMillis;
    private Long finishedAtEpochMillis;
    private List<AiBatchPreviewItem> items;

    public String getTaskId() {
        return taskId;
    }

    public void setTaskId(String taskId) {
        this.taskId = taskId;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public int getTotal() {
        return total;
    }

    public void setTotal(int total) {
        this.total = total;
    }

    public int getProcessed() {
        return processed;
    }

    public void setProcessed(int processed) {
        this.processed = processed;
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

    public int getProgressPercent() {
        return progressPercent;
    }

    public void setProgressPercent(int progressPercent) {
        this.progressPercent = progressPercent;
    }

    public boolean isCompleted() {
        return completed;
    }

    public void setCompleted(boolean completed) {
        this.completed = completed;
    }

    public String getError() {
        return error;
    }

    public void setError(String error) {
        this.error = error;
    }

    public long getStartedAtEpochMillis() {
        return startedAtEpochMillis;
    }

    public void setStartedAtEpochMillis(long startedAtEpochMillis) {
        this.startedAtEpochMillis = startedAtEpochMillis;
    }

    public Long getFinishedAtEpochMillis() {
        return finishedAtEpochMillis;
    }

    public void setFinishedAtEpochMillis(Long finishedAtEpochMillis) {
        this.finishedAtEpochMillis = finishedAtEpochMillis;
    }

    public List<AiBatchPreviewItem> getItems() {
        return items;
    }

    public void setItems(List<AiBatchPreviewItem> items) {
        this.items = items;
    }
}
