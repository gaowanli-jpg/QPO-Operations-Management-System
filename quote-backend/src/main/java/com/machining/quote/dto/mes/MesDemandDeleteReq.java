package com.machining.quote.dto.mes;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

/**
 * MES 需求任务删除请求。
 *
 * <p>{@code bizId} 是 MES 的幂等键，每次删除都要唯一（同一 bizId 重发会返回 1030900002）；
 * {@code id} 是 MES 需求任务主键，来自 demand/create 响应里的 {@code data.items[].id}，
 * 不是 {@code planNo}。
 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class MesDemandDeleteReq {
    private String bizId;
    private Long id;
}
