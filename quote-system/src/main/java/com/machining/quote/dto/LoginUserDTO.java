package com.machining.quote.dto;

import lombok.Data;

@Data
public class LoginUserDTO {
    private Integer id;
    private String username;
    private String displayName;
    private String role;
    private Boolean forcePasswordChange;
}
