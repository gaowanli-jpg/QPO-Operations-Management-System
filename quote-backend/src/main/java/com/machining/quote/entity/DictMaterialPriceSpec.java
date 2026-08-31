package com.machining.quote.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("dict_material_price_spec")
public class DictMaterialPriceSpec {
    @TableId(type = IdType.AUTO)
    private Long id;

    private Long materialId;
    private String specCode;
    private String specName;
    private Double unifiedPrice;
    private Double platePrice;
    private Double barPrice;
    private Double tubePrice;
    private Integer sortNo;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
