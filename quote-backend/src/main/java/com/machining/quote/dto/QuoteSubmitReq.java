package com.machining.quote.dto;

import lombok.Data;
import java.util.List;
import java.util.Map;

@Data
public class QuoteSubmitReq {
    private Integer itemId; // 零件明细表的 ID
    private String quoteMode; // NORMAL / ASSEMBLY
    private Double assemblyFee; // 组合件每套组装费

    // 材料填报数据：普通件必须包含 materialCode，后端按材料编码重新读取材料主数据并计算成本
    private Map<String, Object> materialData;

    // 图纸标注的零件净重，单位 kg；未填写时按 0 保存
    private Double partNetWeight;

    // 工序矩阵：前端动态增加的每一列工艺，都作为一个对象放在这个列表里
    private List<ProcessSubmitDTO> processRoute;
    private List<AssemblyChildDTO> assemblyChildren;

    @Data
    public static class ProcessSubmitDTO {
        private Integer sortNo;    // 工艺顺序号，按小到大展示和导出
        private Long processDetailId; // 具体工艺 ID，正式工艺身份
        private Long processCategoryId;
        private String processCode;
        private String categoryName;
        private String detailName;
        private Double hourlyRate;
        private Double unitWorkHours; // 工程师填入的工时
        private String sourceType;    // process / extra
        private Integer feeItemId;    // 非工时费用工艺 ID
        private String feeCode;       // 非工时费用业务编码，用于使用次数统计和删除重建后的身份继承
        private String processName; // 兼容展示字段；非工时费用仍使用名称
        private Double inputVal;    // 兼容字段：旧工时或非工时费用金额
        private String calcType;    // 工时 / 非工时费用
        private Double unitRate;    // 兼容字段；后端保存时不信任前端传入值
        private Double lineCost;    // 兼容字段；后端保存时重新计算
    }

    @Data
    public static class AssemblyChildDTO {
        private Integer childItemId;
        private Double qtyPerParent;
        private Integer sortNo;
    }
}
