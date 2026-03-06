package com.homewealth.mapper;

import com.homewealth.model.ApiKey;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface ApiKeyMapper {
    ApiKey findById(@Param("id") Long id);
    ApiKey findByKeyValue(@Param("keyValue") String keyValue);
    List<ApiKey> findByUserId(@Param("userId") Long userId);
    void insert(ApiKey apiKey);
    void deactivate(@Param("id") Long id, @Param("userId") Long userId);
    void updateLastUsedAt(@Param("id") Long id, @Param("lastUsedAt") LocalDateTime lastUsedAt);
}
