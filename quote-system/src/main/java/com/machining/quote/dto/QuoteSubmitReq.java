package com.machining.quote.dto;

import lombok.Data;
import java.util.List;
import java.util.Map;

@Data
public class QuoteSubmitReq {
    private Integer itemId; // 零件明细表的 ID

    // 材料填报数据：包含名称、毛坯重量等
    private Map<String, Object> materialData;

    // 工序矩阵：前端动态增加的每一列工艺，都作为一个对象放在这个列表里
    private List<ProcessSubmitDTO> processRoute;

    @Data
    public static class ProcessSubmitDTO {
        private Integer sortNo;    // 工艺顺序号，按小到大展示和导出
        private String processName; // 工序名称 (如: CNC1)
        private Double inputVal;    // 工程师填入的工时或非工时费用金额
        private String calcType;    // 工时 / 非工时费用
        private Double unitRate;    // 核价时匹配到的单价，便于导出报价单复用
        private Double lineCost;    // 该工序最终计入的费用
    }
}
