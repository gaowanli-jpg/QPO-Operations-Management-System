package com.machining.quote.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("dict_extra_fee_item")
public class DictExtraFeeItem {
    @TableId(type = IdType.AUTO)
    private Integer id;

    private String feeCode;
    private String feeName;
    private Integer sortNo;
    private Boolean enabled;
    private String remarks;
}
