package com.homewealth.service;

import com.homewealth.model.DailyNetAssetSnapshot;

import java.time.LocalDate;

public interface SnapshotService {
    void generateSnapshot(Long userId, LocalDate date);
    void generateSnapshotForAllUsers(LocalDate date);
    void deleteSnapshot(Long userId, LocalDate date);
    DailyNetAssetSnapshot getSnapshot(Long userId, LocalDate date);

    /**
     * 按历史收盘价重建 [from, to] 区间内的每日快照。
     *
     * <p>仅在该区间内持仓与常规账户余额未发生变动时结果才准确 —— 回补使用
     * <em>当前</em>持仓数量，不回放历史交易。
     *
     * @return 重建结果（覆盖天数、按历史价定价的标的数、沿用最新价的标的等）
     */
    BackfillResult backfillSnapshots(LocalDate from, LocalDate to);

    /** 快照回补结果 */
    record BackfillResult(
            LocalDate from,
            LocalDate to,
            int datesRebuilt,
            int symbolsWithHistory,
            java.util.List<String> symbolsCarriedForward
    ) {}
}
