package com.homewealth.mapper;

import com.homewealth.model.User;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.List;

@Mapper
public interface UserMapper {
    User findById(@Param("id") Long id);
    User findByUsername(@Param("username") String username);
    void insert(User user);
    void updatePassword(@Param("id") Long id, @Param("password") String password);
    void updateEnabled(@Param("id") Long id, @Param("enabled") boolean enabled);
    List<Long> findAllActiveUserIds();
}
