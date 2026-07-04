package com.homewealth.service;

public interface NotificationService {

    /** 遍历所有活跃用户，各推送一张「家庭资产日报」卡片到飞书群 */
    void pushDailyDigest();

    /** 为单个用户推送日报（供手动触发 / 测试） */
    void pushDailyDigestForUser(Long userId);
}
