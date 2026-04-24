package com.homewealth.scheduler;

import com.homewealth.mapper.UserMapper;
import com.homewealth.service.DividendService;
import com.homewealth.service.SnapshotService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class SnapshotScheduler {

    private final SnapshotService snapshotService;
    private final DividendService dividendService;
    private final UserMapper userMapper;

    // 每日 22:00 抓取分红事件
    @Scheduled(cron = "0 0 22 * * *", zone = "Asia/Shanghai")
    public void fetchDividendEvents() {
        log.info("[Scheduler] Fetching dividend events...");
        try {
            dividendService.fetchAndStoreDividendEvents();
        } catch (Exception e) {
            log.error("Failed to fetch dividend events", e);
        }
    }

    // 每日 23:59 生成快照
    @Scheduled(cron = "0 59 23 * * *", zone = "Asia/Shanghai")
    public void generateDailySnapshots() {
        log.info("[Scheduler] Generating daily snapshots...");
        LocalDate today = LocalDate.now();

        List<Long> userIds = userMapper.findAllActiveUserIds();
        for (Long userId : userIds) {
            try {
                snapshotService.generateSnapshot(userId, today);
            } catch (Exception e) {
                log.error("Failed to generate snapshot for userId={}", userId, e);
            }
            try {
                dividendService.generateDividendRecords(userId, today);
            } catch (Exception e) {
                log.error("Failed to generate dividend records for userId={}", userId, e);
            }
        }
    }
}
