package com.machining.quote.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("dict_pricing_markup_fee")
public class DictPricingMarkupFee {
    @TableId(type = IdType.AUTO)
    private Long id;

    private String pricingScope;
    private String feeCategory;
    private String feeName;
    private BigDecimal ratePercent;
    private Integer sortNo;
    private Boolean enabled;
    private Boolean systemRequired;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
