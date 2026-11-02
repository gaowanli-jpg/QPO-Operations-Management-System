package com.machining.quote.dto.mes;

import lombok.Data;

/**
 * 一条待撤销的 MES 需求任务：来自 mes_production_batch_item 的成功下发台账。
 *
 * <p>同一条待产行（allocation）历史上可能重复下发过，会有多条成功记录，
 * 必须逐条删，最后再按 allocation 汇总状态。
 */
@Data
public class MesDemandCancelTarget {
    private Long batchItemId;
    private Integer allocationId;
    private String mesRecordId;
    private String mesPlanNo;
    private String bizId;
    private String saleOrderNo;
    private String saleOrderItemNo;
}
