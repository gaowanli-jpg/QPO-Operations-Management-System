package com.machining.apssystem.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("aps_process")
public class ApsProcess {
    @TableId(type = IdType.AUTO)
    private Long id;

    private String processName;
    private String processType;
    private String calcType;
    private Integer erpSourceId;
    private Integer sortNo;
    private LocalDateTime syncedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
