package com.machining.quote.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("system_data_center_config")
public class SystemDataCenterConfig {
    @TableId(type = IdType.AUTO)
    private Long id;

    private String name;
    private Boolean active;
    private Boolean enabled;
    private String baseUrl;
    private String tokenUrl;
    private String clientId;
    private String clientSecretCipher;
    private String grantType;
    private Integer connectTimeoutMs;
    private Integer readTimeoutMs;
    private Integer tokenExpireSkewSeconds;
    private String lastTestStatus;
    private String lastTestMessage;
    private LocalDateTime lastTestAt;
    private String remark;
    private String createdBy;
    private String updatedBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
