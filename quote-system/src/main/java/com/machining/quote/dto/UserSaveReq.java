package com.machining.quote.dto;

import lombok.Data;

@Data
public class UserSaveReq {
    private String username;
    private String password;
    private String displayName;
    private String role;
    private Boolean enabled;
}
