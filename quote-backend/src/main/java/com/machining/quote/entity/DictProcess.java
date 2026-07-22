package com.machining.quote.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("dict_process")
public class DictProcess {
    @TableId(type = IdType.AUTO)
    private Integer id;

    private String processName;
    private String calcType;
    private Integer sortNo;

    // 强制绑定，且 Java 变量名坚决不用下划线！
    @TableField("price_1_5")
    private Double price1To5;

    @TableField("price_5_10")
    private Double price5To10;

    @TableField("price_10_50")
    private Double price10To50;

    @TableField("price_50_100")
    private Double price50To100;

    @TableField("price_100_500")
    private Double price100To500;

    @TableField("price_500_1000")
    private Double price500To1000;

    @TableField("price_1000_3000")
    private Double price1000To3000;

    @TableField("price_3000_plus")
    private Double price3000Plus;
}
