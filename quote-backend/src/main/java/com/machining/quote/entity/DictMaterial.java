package com.machining.quote.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("dict_material") // 告诉 MyBatis-Plus 这个类对应数据库里的哪张表
public class DictMaterial {
    @TableId(type = IdType.AUTO) // 告诉它这是主键，并且是自增的
    private Integer id;
    private String materialName;
    private String materialType;
    private Double density;

    @TableField("plate_density")
    private Double plateDensity;
    private Double platePrice;

    @TableField("bar_density")
    private Double barDensity;
    private Double barPrice;

    @TableField("tube_density")
    private Double tubeDensity;
    private Double tubePrice;
    private String remarks;
}
