package com.homewealth.controller;

import com.homewealth.dto.response.ApiResponse;
import com.homewealth.security.SecurityUtils;
import com.homewealth.service.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/notify")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;
    private final SecurityUtils securityUtils;

    /** 手动触发当前用户的飞书日报推送（用于验证，不必等定时任务） */
    @PostMapping("/daily/trigger")
    public ApiResponse<Void> triggerDaily() {
        Long userId = securityUtils.getCurrentUserId();
        notificationService.pushDailyDigestForUser(userId);
        return ApiResponse.success();
    }
}
