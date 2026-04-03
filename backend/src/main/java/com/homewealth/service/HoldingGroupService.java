package com.homewealth.service;

import com.homewealth.dto.request.CreateHoldingGroupRequest;
import com.homewealth.dto.request.UpdateGroupMembersRequest;
import com.homewealth.dto.request.UpdateHoldingGroupRequest;
import com.homewealth.dto.response.HoldingGroupVO;

import java.util.List;

public interface HoldingGroupService {
    List<HoldingGroupVO> listGroups(Long userId);
    HoldingGroupVO createGroup(Long userId, CreateHoldingGroupRequest request);
    HoldingGroupVO updateGroup(Long userId, Long groupId, UpdateHoldingGroupRequest request);
    HoldingGroupVO updateMembers(Long userId, Long groupId, UpdateGroupMembersRequest request);
    void deleteGroup(Long userId, Long groupId);
}
