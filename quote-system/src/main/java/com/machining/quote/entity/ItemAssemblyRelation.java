package com.machining.quote.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("item_assembly_relation")
public class ItemAssemblyRelation {
    @TableId(type = IdType.AUTO)
    private Integer id;

    private Integer rfqId;
    private Integer parentItemId;
    private Integer childItemId;
    private Double childQtyPerParent;
    private Integer sortNo;
}
