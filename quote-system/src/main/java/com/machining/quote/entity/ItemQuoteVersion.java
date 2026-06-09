package com.machining.quote.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.machining.quote.config.JsonbTypeHandler;
import lombok.Data;

@Data
@TableName(value = "item_quote_version", autoResultMap = true) // 必须加 autoResultMap 开启黑魔法
public class ItemQuoteVersion {
    @TableId(type = IdType.AUTO)
    private Integer id;

    private Integer rfqId;
    private String partNo;          // 图号
    private String description;     // 物料描述
    private Integer qty;            // 数量
    private String version;         // 版本 (保留你原有的)
    private String orderType;       // 订单类型 (保留你原有的)

    // 👇 市场部 Excel 导入新增的 5 个业务字段 👇
    private String materialName;      // 材料名称
    private String engineeringMaterialName; // 工程核价选择的材料
    private String surfaceTreatment;  // 表面处理
    private String unit;              // 单位
    private String leadTime;          // 交货周期(L/T)
    private String quoteLeadTime;     // 报价单使用的交货周期，为空时沿用客户询价交期
    private String internalTargetLeadTime; // 提单和工艺表使用的内部目标交期，为空时沿用报价交期
    private String remark;            // 备注
    private Double materialGrossWeight; // 材料毛重，单位 kg
    private Double partNetWeight;       // 零件净重，单位 kg
    private String quoteMode;           // NORMAL / ASSEMBLY
    private Double assemblyFee;         // 组合件每套组装费

    // 指定用 Jackson 把前端传来的复杂矩阵对象，自动变成 JSONB 存进数据库
    @TableField(typeHandler = JsonbTypeHandler.class)
    private Object materialData;

    @TableField(typeHandler = JsonbTypeHandler.class)
    private Object processRoute;

    @TableField(typeHandler = JsonbTypeHandler.class)
    private Object feeData;

    // 利润与最终价格字段
    private Double baseCost;
    private Double finalUnitPrice;
    private Double finalTotalPrice;
    private String status; // 状态：待核价 / 已报价

    // MyBatis-Plus 会自动把驼峰命名 drawingUrl 映射到数据库的 drawing_url
    private String drawingUrl;
}
