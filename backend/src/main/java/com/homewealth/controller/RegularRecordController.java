package com.homewealth.controller;

import com.homewealth.dto.request.UpdateRecordRequest;
import com.homewealth.dto.response.ApiResponse;
import com.homewealth.model.RegularAccountRecord;
import com.homewealth.security.SecurityUtils;
import com.homewealth.service.RegularRecordService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/accounts/{accountId}/records")
@RequiredArgsConstructor
public class RegularRecordController {

    private final RegularRecordService recordService;
    private final SecurityUtils securityUtils;

    @GetMapping
    public ApiResponse<List<RegularAccountRecord>> getHistory(@PathVariable Long accountId) {
        Long userId = securityUtils.getCurrentUserId();
        return ApiResponse.success(recordService.getHistory(userId, accountId));
    }

    @GetMapping("/current")
    public ApiResponse<RegularAccountRecord> getCurrent(@PathVariable Long accountId) {
        Long userId = securityUtils.getCurrentUserId();
        return ApiResponse.success(recordService.getCurrentRecord(userId, accountId));
    }

    @PostMapping
    public ApiResponse<RegularAccountRecord> addRecord(@PathVariable Long accountId,
                                                        @Valid @RequestBody UpdateRecordRequest request) {
        Long userId = securityUtils.getCurrentUserId();
        return ApiResponse.success(recordService.addRecord(userId, accountId, request));
    }

    @DeleteMapping("/{recordId}")
    public ApiResponse<Void> deleteRecord(@PathVariable Long accountId, @PathVariable Long recordId) {
        Long userId = securityUtils.getCurrentUserId();
        recordService.deleteRecord(userId, accountId, recordId);
        return ApiResponse.success();
    }
}
