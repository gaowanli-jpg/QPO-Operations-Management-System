package com.machining.quote.dto;

import lombok.Data;

import java.util.List;

@Data
public class UserSaveReq {
    private String username;
    private String password;
    private String displayName;
    private String role;
    private List<String> roleCodes;
    private String realName;
    private String phone;
    private String email;
    private Long deptId;
    private Long postId;
    private Boolean enabled;
}
