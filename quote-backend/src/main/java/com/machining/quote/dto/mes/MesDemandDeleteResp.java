package com.machining.quote.dto.mes;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.math.BigDecimal;

/**
 * MES 需求任务删除响应。
 *
 * <p>MES 的删除不是幂等成功：同一 bizId 重发返回 {@code 1030900002}（上次其实成功了），
 * 换 bizId 删同一条返回 {@code 1030700000}（任务不存在）。这两个码都按"已删除"处理。
 * 状态不允许删除（已提交审核/已执行）返回 {@code 1030700006}。
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class MesDemandDeleteResp {
    private static final String CODE_SUCCESS = "0";
    private static final String CODE_IDEMPOTENT_SUCCESS = "1030900002";
    private static final String CODE_NOT_FOUND = "1030700000";
    private static final String CODE_STATE_REJECTED = "1030700006";
    private static final String CODE_PROCESSING = "1030900003";
    private static final String CODE_AUTH = "1030900001";

    private Object code;
    @JsonAlias("msg")
    private String message;
    private Object data;

    public boolean isSuccess() {
        return CODE_SUCCESS.equals(normalizedCode());
    }

    public boolean isIdempotentSuccess() {
        return CODE_IDEMPOTENT_SUCCESS.equals(normalizedCode());
    }

    public boolean isNotFound() {
        return CODE_NOT_FOUND.equals(normalizedCode());
    }

    public boolean isStateRejected() {
        return CODE_STATE_REJECTED.equals(normalizedCode());
    }

    /** 需求任务已不存在或已删除，三种码都视为删除成功。 */
    public boolean isDeleted() {
        return isSuccess() || isIdempotentSuccess() || isNotFound();
    }

    /** 鉴权异常 / 同 bizId 处理中，可用同一 bizId 重试。 */
    public boolean isRetryable() {
        String code = normalizedCode();
        return CODE_AUTH.equals(code) || CODE_PROCESSING.equals(code);
    }

    public String codeText() {
        return normalizedCode();
    }

    public String messageText() {
        return message == null ? "" : message.trim();
    }

    /**
     * 归一化 MES 返回码。MES 有时写成 {@code 1_030_900_002}，有时写成数字或带小数，
     * 统一去除非数字字符后再比较。
     */
    private String normalizedCode() {
        if (code == null) {
            return "";
        }
        if (code instanceof Number number) {
            return decimalText(number.toString());
        }
        String text = String.valueOf(code).trim();
        if (text.isEmpty()) {
            return "";
        }
        if (text.matches("-?\\d+(\\.\\d+)?")) {
            return decimalText(text);
        }
        return text.replaceAll("[^0-9]", "");
    }

    private String decimalText(String value) {
        try {
            return new BigDecimal(value).stripTrailingZeros().toPlainString();
        } catch (NumberFormatException e) {
            return "";
        }
    }
}
