package com.machining.quote.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("u9_runtime_config")
public class U9RuntimeConfig {
    @TableId(type = IdType.INPUT)
    private Integer id;
    private String orgCode;
    private LocalDateTime updatedAt;
}
