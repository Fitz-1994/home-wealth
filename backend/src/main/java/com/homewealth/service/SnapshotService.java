package com.homewealth.service;

import com.homewealth.model.DailyNetAssetSnapshot;

import java.time.LocalDate;

public interface SnapshotService {
    void generateSnapshot(Long userId, LocalDate date);
    void generateSnapshotForAllUsers(LocalDate date);
    void deleteSnapshot(Long userId, LocalDate date);
    DailyNetAssetSnapshot getSnapshot(Long userId, LocalDate date);
}
