package com.homewealth.mapper;

import com.homewealth.model.RegularAccountRecord;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface RegularAccountRecordMapper {
    RegularAccountRecord findCurrentByAccountId(@Param("accountId") Long accountId);
    List<RegularAccountRecord> findByAccountId(@Param("accountId") Long accountId);
    void insert(RegularAccountRecord record);
    void markAllNotCurrent(@Param("accountId") Long accountId);
    void deleteById(@Param("id") Long id, @Param("accountId") Long accountId);
    // 查询某用户当前所有账户的有效记录（用于汇总计算）
    List<RegularAccountRecord> findAllCurrentByUserId(@Param("userId") Long userId);
}
