package com.machining.quote.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("system_inbound_callback_config")
public class SystemInboundCallbackConfig {
    @TableId(type = IdType.AUTO)
    private Long id;

    private String callbackCode;
    private String sourceSystemCode;
    private String name;
    private Boolean enabled;
    private Boolean tokenEnabled;
    private String publicBaseUrl;
    private String callbackPath;
    private String tokenHash;
    private String tokenMask;
    private LocalDateTime tokenUpdatedAt;
    private String lastValidateStatus;
    private String lastValidateMessage;
    private LocalDateTime lastValidateAt;
    private String remark;
    private String createdBy;
    private String updatedBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
