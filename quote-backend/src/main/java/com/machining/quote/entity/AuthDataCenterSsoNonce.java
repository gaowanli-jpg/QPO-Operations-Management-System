package com.machining.quote.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("auth_data_center_sso_nonce")
public class AuthDataCenterSsoNonce {
    @TableId(type = IdType.AUTO)
    private Long id;

    private String nonce;
    private String nonceHash;
    private String username;
    private String appCode;
    private Long requestTimestamp;
    private LocalDateTime usedAt;
    private LocalDateTime expiresAt;
    private String result;
    private String failureReason;
    private LocalDateTime createdAt;
}
