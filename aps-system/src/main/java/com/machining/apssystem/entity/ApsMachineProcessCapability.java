package com.machining.apssystem.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("aps_machine_process_capability")
public class ApsMachineProcessCapability {
    @TableId(type = IdType.AUTO)
    private Long id;

    private Long machineId;
    private Long processId;
    private String processName;
    private String calcType;
    private String remark;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
