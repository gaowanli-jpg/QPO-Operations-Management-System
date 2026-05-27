package com.machining.quote.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;

@Data
@TableName("order_delivery_ticket")
public class OrderDeliveryTicket {
    @TableId(type = IdType.AUTO)
    private Integer id;

    private Integer rfqId;
    private Integer itemId;
    private String orderType;
    private String internalProjectNo;
    private String projectNo;
    private String partNo;
    private Integer qty;
    private String materialName;
    private String surfaceTreatment;
    private String version;
    private LocalDate startDate;
    private LocalDate deliveryDate;
    private String remark;
    private String arrangeType;
}
