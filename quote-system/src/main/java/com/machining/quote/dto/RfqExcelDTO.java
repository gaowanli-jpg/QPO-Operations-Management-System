package com.machining.quote.dto;

import com.alibaba.excel.annotation.ExcelProperty;
import lombok.Data;

@Data
public class RfqExcelDTO {

    // @ExcelProperty 会自动去匹配 Excel 里叫这个名字的表头
    @ExcelProperty("物品编号")
    private String partNo;

    @ExcelProperty("物料描述")
    private String description;

    @ExcelProperty("材料名称")
    private String materialName;

    @ExcelProperty("表面处理")
    private String surfaceTreatment;

    @ExcelProperty("版本")
    private String version;

    @ExcelProperty("数量")
    private Integer qty;

    @ExcelProperty("单位")
    private String unit;

    @ExcelProperty("交货周期(L/T)")
    private String leadTime;

    @ExcelProperty("备注")
    private String remark;
}