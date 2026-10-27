package com.machining.quote.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

@Data
@TableName("production_item_auto_dispatch_config")
public class ProductionItemAutoDispatchConfig {
    @TableId(type = IdType.INPUT)
    private String id;

    private Boolean enabled;
    private String scheduleMode;
    private LocalTime dailyRunTime;
    private Integer batchSize;
    private Integer batchIntervalMs;
    private Integer maxRounds;
    private Long warnDurationMs;
    private Integer failureBreaker;
    private Boolean includeFailed;

    private LocalDateTime lastRunAt;
    private LocalDate lastRunDate;
    private Long lastTaskId;
    private String lastStatus;
    private String lastMessage;
    private Integer lastTotal;
    private Integer lastSuccess;
    private Integer lastFailed;
    private Integer lastSkipped;
    private Integer lastRounds;
    private Long lastDurationMs;

    private String updatedBy;
    private LocalDateTime updatedAt;
}
