package com.machining.quote.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.machining.quote.config.JsonbTypeHandler;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName(value = "system_external_integration_config", autoResultMap = true)
public class SystemExternalIntegrationConfig {
    @TableId(type = IdType.AUTO)
    private Long id;

    private String systemCode;
    private String name;
    private Boolean active;
    private Boolean enabled;
    private String authType;

    @TableField(typeHandler = JsonbTypeHandler.class)
    private Object configJson;

    private String remark;
    private String lastTestStatus;
    private String lastTestMessage;
    private LocalDateTime lastTestAt;
    private String createdBy;
    private String updatedBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
