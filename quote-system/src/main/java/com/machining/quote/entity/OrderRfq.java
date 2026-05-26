package com.machining.quote.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.machining.quote.config.JsonbTypeHandler;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName(value = "order_rfq", autoResultMap = true)
public class OrderRfq {
    @TableId(type = IdType.AUTO)
    private Integer id;

    private String rfqNo;          // 询价单号 (我们会自动生成)
    private String customerName;   // 客户名称
    private String projectName;    // 历史兼容字段，新单不再使用项目名称

    private String status;         // 单据状态 (与控制器里的 status 对应)

    @TableField(typeHandler = JsonbTypeHandler.class)
    private Object engineerConfig;  // 工程部订单全局默认设定

    private LocalDateTime createdAt;
}
