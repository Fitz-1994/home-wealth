package com.homewealth.controller;

import com.homewealth.dto.request.CreateHoldingGroupRequest;
import com.homewealth.dto.request.UpdateGroupMembersRequest;
import com.homewealth.dto.request.UpdateHoldingGroupRequest;
import com.homewealth.dto.response.ApiResponse;
import com.homewealth.dto.response.HoldingGroupVO;
import com.homewealth.security.SecurityUtils;
import com.homewealth.service.HoldingGroupService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/holding-groups")
@RequiredArgsConstructor
public class HoldingGroupController {

    private final HoldingGroupService holdingGroupService;
    private final SecurityUtils securityUtils;

    @GetMapping
    public ApiResponse<List<HoldingGroupVO>> listGroups() {
        Long userId = securityUtils.getCurrentUserId();
        return ApiResponse.success(holdingGroupService.listGroups(userId));
    }

    @PostMapping
    public ApiResponse<HoldingGroupVO> createGroup(@Valid @RequestBody CreateHoldingGroupRequest request) {
        Long userId = securityUtils.getCurrentUserId();
        return ApiResponse.success(holdingGroupService.createGroup(userId, request));
    }

    @PutMapping("/{id}")
    public ApiResponse<HoldingGroupVO> updateGroup(@PathVariable Long id,
                                                    @Valid @RequestBody UpdateHoldingGroupRequest request) {
        Long userId = securityUtils.getCurrentUserId();
        return ApiResponse.success(holdingGroupService.updateGroup(userId, id, request));
    }

    @PutMapping("/{id}/members")
    public ApiResponse<HoldingGroupVO> updateMembers(@PathVariable Long id,
                                                      @Valid @RequestBody UpdateGroupMembersRequest request) {
        Long userId = securityUtils.getCurrentUserId();
        return ApiResponse.success(holdingGroupService.updateMembers(userId, id, request));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> deleteGroup(@PathVariable Long id) {
        Long userId = securityUtils.getCurrentUserId();
        holdingGroupService.deleteGroup(userId, id);
        return ApiResponse.success();
    }
}
