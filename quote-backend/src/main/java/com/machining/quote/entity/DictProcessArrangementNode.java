package com.machining.quote.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("dict_process_arrangement_node")
public class DictProcessArrangementNode {
    @TableId(type = IdType.AUTO)
    private Long id;

    private Long parentId;
    private String nodeName;
    private Integer sortNo;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
