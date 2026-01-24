package com.example.hello.dto;

public class StatsResponse {
    private long todaySubmitCount;   // 今日录入数
    private long pendingCount;       // 待审核数
    private long approvedCount;      // 已通过数
    private long totalCount;         // 总录入数

    public StatsResponse(long todaySubmitCount, long pendingCount, long approvedCount, long totalCount) {
        this.todaySubmitCount = todaySubmitCount;
        this.pendingCount = pendingCount;
        this.approvedCount = approvedCount;
        this.totalCount = totalCount;
    }

    public long getTodaySubmitCount() { return todaySubmitCount; }
    public void setTodaySubmitCount(long todaySubmitCount) { this.todaySubmitCount = todaySubmitCount; }
    public long getPendingCount() { return pendingCount; }
    public void setPendingCount(long pendingCount) { this.pendingCount = pendingCount; }
    public long getApprovedCount() { return approvedCount; }
    public void setApprovedCount(long approvedCount) { this.approvedCount = approvedCount; }
    public long getTotalCount() { return totalCount; }
    public void setTotalCount(long totalCount) { this.totalCount = totalCount; }
}
