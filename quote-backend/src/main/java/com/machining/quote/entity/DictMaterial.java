package com.machining.quote.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("dict_material")
public class DictMaterial {
    @TableId(type = IdType.AUTO)
    private Long id;

    private String materialCode;
    private String materialName;
    private Long materialTypeId;
    private String materialCategory;
    private Double density;
    private Double plateDensity;
    private Double platePrice;
    private Double barDensity;
    private Double barPrice;
    private Double tubeDensity;
    private Double tubePrice;
    private String remarks;
    private Boolean enabled;
    private Integer sortNo;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
