package com.homewealth.service.impl;

import com.homewealth.dto.request.CreateHoldingGroupRequest;
import com.homewealth.dto.request.UpdateGroupMembersRequest;
import com.homewealth.dto.request.UpdateHoldingGroupRequest;
import com.homewealth.dto.response.HoldingGroupVO;
import com.homewealth.exception.BusinessException;
import com.homewealth.exception.ErrorCode;
import com.homewealth.mapper.HoldingGroupMapper;
import com.homewealth.mapper.InvestmentHoldingMapper;
import com.homewealth.model.HoldingGroup;
import com.homewealth.model.InvestmentHolding;
import com.homewealth.service.HoldingGroupService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class HoldingGroupServiceImpl implements HoldingGroupService {

    private final HoldingGroupMapper groupMapper;
    private final InvestmentHoldingMapper holdingMapper;

    @Override
    public List<HoldingGroupVO> listGroups(Long userId) {
        List<HoldingGroup> groups = groupMapper.findByUserId(userId);
        // 加载该用户持仓用于填充 symbolName/market
        Map<String, InvestmentHolding> holdingMap = buildHoldingMap(userId);
        return groups.stream().map(g -> toVO(g, holdingMap)).collect(Collectors.toList());
    }

    @Override
    @Transactional
    public HoldingGroupVO createGroup(Long userId, CreateHoldingGroupRequest request) {
        HoldingGroup group = new HoldingGroup();
        group.setUserId(userId);
        group.setGroupName(request.getGroupName());
        group.setNote(request.getNote());
        groupMapper.insert(group);

        if (request.getSymbols() != null) {
            for (String symbol : request.getSymbols()) {
                validateSymbolNotInGroup(symbol, userId);
                groupMapper.insertMember(group.getId(), symbol);
            }
        }

        Map<String, InvestmentHolding> holdingMap = buildHoldingMap(userId);
        return toVO(group, holdingMap);
    }

    @Override
    @Transactional
    public HoldingGroupVO updateGroup(Long userId, Long groupId, UpdateHoldingGroupRequest request) {
        HoldingGroup group = getGroupOrThrow(groupId, userId);
        group.setGroupName(request.getGroupName());
        group.setNote(request.getNote());
        groupMapper.update(group);

        Map<String, InvestmentHolding> holdingMap = buildHoldingMap(userId);
        return toVO(group, holdingMap);
    }

    @Override
    @Transactional
    public HoldingGroupVO updateMembers(Long userId, Long groupId, UpdateGroupMembersRequest request) {
        HoldingGroup group = getGroupOrThrow(groupId, userId);

        // 获取当前组的成员 symbols
        Set<String> currentSymbols = groupMapper.findMembersByGroupId(groupId).stream()
                .map(m -> (String) m.get("symbol"))
                .collect(Collectors.toSet());

        // 清除当前成员，重新插入
        groupMapper.deleteMembersByGroupId(groupId);

        if (request.getSymbols() != null) {
            for (String symbol : request.getSymbols()) {
                // 只校验非当前组原有成员是否已被其他组占用
                if (!currentSymbols.contains(symbol)) {
                    validateSymbolNotInGroup(symbol, userId);
                }
                groupMapper.insertMember(groupId, symbol);
            }
        }

        Map<String, InvestmentHolding> holdingMap = buildHoldingMap(userId);
        return toVO(group, holdingMap);
    }

    @Override
    @Transactional
    public void deleteGroup(Long userId, Long groupId) {
        getGroupOrThrow(groupId, userId);
        // ON DELETE CASCADE 会自动清理 holding_group_member
        groupMapper.deleteById(groupId, userId);
    }

    private HoldingGroup getGroupOrThrow(Long groupId, Long userId) {
        HoldingGroup group = groupMapper.findById(groupId);
        if (group == null || !group.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.GROUP_NOT_FOUND);
        }
        return group;
    }

    private void validateSymbolNotInGroup(String symbol, Long userId) {
        if (groupMapper.countMemberBySymbol(symbol, userId) > 0) {
            throw new BusinessException(ErrorCode.HOLDING_ALREADY_IN_OTHER_GROUP);
        }
    }

    /** 构建 symbol -> InvestmentHolding 映射（取每个 symbol 的第一条记录用于 name/market） */
    private Map<String, InvestmentHolding> buildHoldingMap(Long userId) {
        Map<String, InvestmentHolding> map = new LinkedHashMap<>();
        for (InvestmentHolding h : holdingMapper.findActiveByUserId(userId)) {
            map.putIfAbsent(h.getSymbol(), h);
        }
        return map;
    }

    private HoldingGroupVO toVO(HoldingGroup group, Map<String, InvestmentHolding> holdingMap) {
        HoldingGroupVO vo = new HoldingGroupVO();
        vo.setId(group.getId());
        vo.setGroupName(group.getGroupName());
        vo.setNote(group.getNote());

        List<Map<String, Object>> memberRows = groupMapper.findMembersByGroupId(group.getId());
        List<HoldingGroupVO.GroupMemberVO> members = new ArrayList<>();
        for (Map<String, Object> row : memberRows) {
            String symbol = (String) row.get("symbol");
            HoldingGroupVO.GroupMemberVO m = new HoldingGroupVO.GroupMemberVO();
            m.setSymbol(symbol);
            InvestmentHolding h = holdingMap.get(symbol);
            if (h != null) {
                m.setSymbolName(h.getSymbolName());
                m.setMarket(h.getMarket());
            }
            members.add(m);
        }
        vo.setMembers(members);
        return vo;
    }
}
