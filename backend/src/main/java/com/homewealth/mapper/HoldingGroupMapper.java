package com.homewealth.mapper;

import com.homewealth.model.HoldingGroup;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

@Mapper
public interface HoldingGroupMapper {
    HoldingGroup findById(@Param("id") Long id);
    List<HoldingGroup> findByUserId(@Param("userId") Long userId);
    void insert(HoldingGroup group);
    void update(HoldingGroup group);
    void deleteById(@Param("id") Long id, @Param("userId") Long userId);

    // 成员操作（symbol 维度）
    List<Map<String, Object>> findMembersByGroupId(@Param("groupId") Long groupId);
    List<Map<String, Object>> findAllMembersByUserId(@Param("userId") Long userId);
    void insertMember(@Param("groupId") Long groupId, @Param("symbol") String symbol);
    void deleteMembersByGroupId(@Param("groupId") Long groupId);
    int countMemberBySymbol(@Param("symbol") String symbol, @Param("userId") Long userId);
}
