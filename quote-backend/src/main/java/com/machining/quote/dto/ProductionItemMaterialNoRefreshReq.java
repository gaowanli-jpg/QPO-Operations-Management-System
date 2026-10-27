package com.machining.quote.dto;

import lombok.Data;

import java.util.List;

/**
 * 待产零件「刷新料号」异常修复请求。
 *
 * <p>reason 为必填，用于审计留痕；allocationIds 为待产零件行（分配行），
 * 服务端会按提单行去重后逐个提单执行刷新。</p>
 */
@Data
public class ProductionItemMaterialNoRefreshReq {
    private List<Integer> allocationIds;
    private String reason;
}
