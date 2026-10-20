package com.machining.quote.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("sales_order_change_dispatch_setting")
public class SalesOrderChangeDispatchSetting {
    @TableId
    private Long id;
    private String dispatchMode;
    private Long autoDispatchEpoch;
    private LocalDateTime autoEnabledAt;
    private Long updatedBy;
    private LocalDateTime updatedAt;
}
