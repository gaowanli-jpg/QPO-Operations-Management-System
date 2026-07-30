package com.machining.quote.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.machining.quote.config.JsonbTypeHandler;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName(value = "sales_order_import_record", autoResultMap = true)
public class SalesOrderImportRecord {
    @TableId(type = IdType.AUTO)
    private Long id;

    private Long batchId;
    private String otherId;
    private String customerOrderNo;
    private String orderBy;
    private String documentType;
    private Integer lineCount;

    @TableField(typeHandler = JsonbTypeHandler.class)
    private Object requestSummaryJson;

    private Integer u9ResCode;
    private Boolean u9Success;
    private String u9Id;
    private String u9Code;
    private String u9ErrorMsg;
    private Long salesOrderId;
    private String salesOrderNo;
    private Integer itemCount;
    @Deprecated
    private Integer rfqId;
    @Deprecated
    private String rfqNo;
    @Deprecated
    private String qpoStatus;
    private Integer matchedItemCount;
    private Integer unmatchedItemCount;
    private String warningMessage;
    private String status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
