package com.machining.quote.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("dict_material_type")
public class DictMaterialType {
    @TableId(type = IdType.AUTO)
    private Long id;

    private String typeCode;
    private String typeName;
    private String remarks;
    private Boolean enabled;
    private Integer sortNo;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
