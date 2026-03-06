package com.homewealth.scheduler;

import com.homewealth.mapper.UserMapper;
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
    private final UserMapper userMapper;

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
        }
    }
}
