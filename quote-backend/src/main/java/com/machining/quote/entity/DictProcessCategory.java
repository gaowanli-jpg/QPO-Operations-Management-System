package com.machining.quote.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("dict_process_category")
public class DictProcessCategory {
    @TableId(type = IdType.AUTO)
    private Long id;

    private String processCode;
    private String categoryName;
    private BigDecimal hourlyRate;
    private String remarks;
    private String sourceType;
    private String mesExternalId;
    private Boolean enabled;
    private Integer sortNo;
    private LocalDateTime mesSyncedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
