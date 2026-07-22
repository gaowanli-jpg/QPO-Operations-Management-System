package com.machining.quote.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("dict_markup_fee")
public class DictMarkupFee {
    @TableId(type = IdType.AUTO)
    private Integer id;

    private String feeName;
    private Double ratePercent;
    private Integer sortNo;
    private Boolean enabled;
}
