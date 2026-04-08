package com.example.hello.dto;

import java.util.List;

public class AiBatchTaskProgressResponse {
    private String taskId;
    private String status;
    private int total;
    private int processed;
    private int successCount;
    private int failCount;
    private int progressPercent;
    private boolean completed;
    private String error;
    private long startedAtEpochMillis;
    private Long finishedAtEpochMillis;
    private List<AiBatchSubmitItemResult> items;

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

    public List<AiBatchSubmitItemResult> getItems() {
        return items;
    }

    public void setItems(List<AiBatchSubmitItemResult> items) {
        this.items = items;
    }
}
