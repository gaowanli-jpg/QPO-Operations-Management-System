package com.machining.quote.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("u9_sync_seller_option_cache")
public class U9SyncSellerOptionCache {
    @TableId(type = IdType.AUTO)
    private Long id;

    private String u9OrgCode;
    private String u9SellerId;
    private String sellerCode;
    private String sellerName;
    private String departmentCode;
    private String departmentName;
    private Boolean enabled;
    private String operatorType;
    private LocalDateTime lastSyncedAt;
    private String sourceHash;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
