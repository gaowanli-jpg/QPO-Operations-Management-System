package com.machining.quote.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.machining.quote.config.JsonbTypeHandler;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName(value = "system_task", autoResultMap = true)
public class SystemTask {
    @TableId(type = IdType.AUTO)
    private Long id;

    private String taskType;
    private String status;
    private Integer progress;
    private Integer total;

    @TableField(typeHandler = JsonbTypeHandler.class)
    private Object requestJson;

    @TableField(typeHandler = JsonbTypeHandler.class)
    private Object resultJson;

    private String filePath;
    private String errorMessage;
    private String createdBy;
    private LocalDateTime createdAt;
    private LocalDateTime startedAt;
    private LocalDateTime finishedAt;
}
