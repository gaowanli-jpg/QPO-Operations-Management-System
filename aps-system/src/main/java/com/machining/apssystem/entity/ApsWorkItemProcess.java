package com.machining.apssystem.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("aps_work_item_process")
public class ApsWorkItemProcess {
    @TableId(type = IdType.AUTO)
    private Long id;

    private Long workItemId;
    private Integer sortNo;
    private String processName;
    private String calcType;
    private String sourceType;
    private BigDecimal inputVal;
    private BigDecimal workHours;
    private BigDecimal unitRate;
    private BigDecimal lineCost;
    private String rawRow;
    private LocalDateTime createdAt;
}
