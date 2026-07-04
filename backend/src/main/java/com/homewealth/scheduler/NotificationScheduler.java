package com.homewealth.scheduler;

import com.homewealth.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 每日飞书日报定时推送。
 * 09:00（Asia/Shanghai）推送「昨日」完整数据——昨晚 23:59 已生成快照与分红记录。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationScheduler {

    private final NotificationService notificationService;

    @Scheduled(cron = "${feishu.daily-cron:0 0 9 * * *}", zone = "Asia/Shanghai")
    public void pushDaily() {
        log.info("[Scheduler] Pushing daily Feishu digest...");
        try {
            notificationService.pushDailyDigest();
        } catch (Exception e) {
            log.error("Failed to push daily Feishu digest", e);
        }
    }
}
