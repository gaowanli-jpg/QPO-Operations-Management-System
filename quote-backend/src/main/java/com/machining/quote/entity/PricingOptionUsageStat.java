package com.machining.quote.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("pricing_option_usage_stat")
public class PricingOptionUsageStat {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String optionType;
    private String optionKey;
    private Long usageCount;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
