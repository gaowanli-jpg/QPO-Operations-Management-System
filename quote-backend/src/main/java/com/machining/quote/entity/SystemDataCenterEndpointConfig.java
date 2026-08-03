package com.machining.quote.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.machining.quote.config.JsonbTypeHandler;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName(value = "system_data_center_endpoint_config", autoResultMap = true)
public class SystemDataCenterEndpointConfig {
    @TableId(type = IdType.AUTO)
    private Long id;

    private String endpointCode;
    private String displayName;
    private String targetSystemCode;
    private String method;
    private String path;
    private Boolean enabled;
    private Integer timeoutMs;
    private String requestAdapter;
    private String responseAdapter;

    @TableField(typeHandler = JsonbTypeHandler.class)
    private Object requestSchemaJson;

    @TableField(typeHandler = JsonbTypeHandler.class)
    private Object responseSchemaJson;

    private String lastTestStatus;
    private String lastTestMessage;
    private LocalDateTime lastTestAt;
    private String remark;
    private String createdBy;
    private String updatedBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
