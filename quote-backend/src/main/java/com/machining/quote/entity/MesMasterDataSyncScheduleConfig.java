package com.machining.quote.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("mes_master_data_sync_schedule_config")
public class MesMasterDataSyncScheduleConfig {
    @TableId(type = IdType.INPUT)
    private String syncType;

    private Boolean enabled;
    private Long startupDelayMs;
    private Long fixedDelayMs;
    private LocalDateTime lastScheduledRunAt;
    private Long lastScheduledSyncRunId;
    private String lastScheduledStatus;
    private String lastScheduledMessage;
    private LocalDateTime updatedAt;
}
