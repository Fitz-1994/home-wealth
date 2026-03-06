package com.homewealth.mapper;

import com.homewealth.model.AssetAccount;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface AssetAccountMapper {
    AssetAccount findById(@Param("id") Long id);
    List<AssetAccount> findByUserId(@Param("userId") Long userId,
                                    @Param("accountType") String accountType,
                                    @Param("assetCategory") String assetCategory);
    void insert(AssetAccount account);
    void update(AssetAccount account);
    void deactivate(@Param("id") Long id, @Param("userId") Long userId);
}
