package com.machining.quote.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("dict_process_arrangement_item")
public class DictProcessArrangementItem {
    @TableId(type = IdType.AUTO)
    private Long id;

    private Long parentNodeId;
    private String sourceType;
    private Long processDetailId;
    private Integer feeItemId;
    private Integer sortNo;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
