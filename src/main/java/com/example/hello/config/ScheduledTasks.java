package com.example.hello.config;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.example.hello.entity.PatternPending;
import com.example.hello.repository.PatternPendingRepository;

@Component
public class ScheduledTasks {

    private final PatternPendingRepository pendingRepository;

    public ScheduledTasks(PatternPendingRepository pendingRepository) {
        this.pendingRepository = pendingRepository;
    }

    /**
     * 每天凌晨2点清理前一天已审核通过的数据，未审核的保留
     */
    @Scheduled(cron = "0 0 2 * * ?")
    public void cleanupApprovedRecords() {
        // 获取昨天结束时间点（今天0点）
        LocalDateTime cutoffTime = LocalDateTime.now().with(LocalTime.MIN);
        
        // 只查询已审核通过且在昨天之前创建的记录
        List<PatternPending> approvedRecords = pendingRepository.findApprovedBeforeTime(cutoffTime);
        
        for (PatternPending record : approvedRecords) {
            try {
                pendingRepository.delete(record);
            } catch (Exception e) {
                System.err.println("清理已审核记录失败, ID: " + record.getId() + ", 错误: " + e.getMessage());
            }
        }
        
        System.out.println("定时清理完成，删除了 " + approvedRecords.size() + " 条已审核通过的记录");
    }
}
