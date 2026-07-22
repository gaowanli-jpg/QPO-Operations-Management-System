package com.machining.quote.dto;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Data
public class SystemUserDTO {
    private Long id;
    private Integer legacyUserId;
    private String username;
    private String displayName;
    private String realName;
    private String phone;
    private String email;
    private Long deptId;
    private Long postId;
    private Boolean enabled;
    private Boolean forcePasswordChange;
    private LocalDateTime lastLoginAt;
    private List<String> roles = new ArrayList<>();
}
