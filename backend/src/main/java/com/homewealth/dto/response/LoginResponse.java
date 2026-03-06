package com.homewealth.dto.response;

import lombok.Data;

@Data
public class LoginResponse {
    private String token;
    private String tokenType = "Bearer";
    private long expiresIn;
    private UserVO user;

    @Data
    public static class UserVO {
        private Long id;
        private String username;
        private String displayName;
        private String role;
    }
}
