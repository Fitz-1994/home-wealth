package com.homewealth.controller;

import com.homewealth.dto.response.ApiResponse;
import com.homewealth.security.SecurityUtils;
import com.homewealth.service.SnapshotService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/snapshots")
@RequiredArgsConstructor
public class SnapshotController {

    private final SnapshotService snapshotService;
    private final SecurityUtils securityUtils;

    @PostMapping("/trigger")
    public ApiResponse<Void> triggerSnapshot() {
        Long userId = securityUtils.getCurrentUserId();
        snapshotService.generateSnapshot(userId, LocalDate.now());
        return ApiResponse.success();
    }

    @DeleteMapping("/{date}")
    public ApiResponse<Void> deleteSnapshot(
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        Long userId = securityUtils.getCurrentUserId();
        snapshotService.deleteSnapshot(userId, date);
        return ApiResponse.success();
    }

    /**
     * 按历史收盘价重建区间内的每日快照（全体用户）。
     * 仅在该区间内持仓未变动时结果才准确 —— 回补不回放历史交易。
     */
    @PostMapping("/backfill")
    public ApiResponse<SnapshotService.BackfillResult> backfill(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ApiResponse.success(snapshotService.backfillSnapshots(from, to));
    }
}
