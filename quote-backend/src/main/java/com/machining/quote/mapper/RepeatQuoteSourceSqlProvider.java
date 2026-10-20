package com.machining.quote.mapper;

import java.util.List;
import java.util.Map;

public class RepeatQuoteSourceSqlProvider {
    private static final String AUTO_REUSABLE_FILTER = """
            AND h.status IN ('已核价', '已报价', '部分已提单', '已生成提单', '已提单')
            AND h.tax_included_unit_price IS NOT NULL
            AND h.material_data IS NOT NULL
            AND h.material_data <> '{}'::jsonb
            AND h.process_route IS NOT NULL
            AND h.process_route <> '[]'::jsonb
            """;

    public String selectLatestReleasedRepeatSources(Map<String, Object> params) {
        return selectLatestRepeatSources(params, AUTO_REUSABLE_FILTER);
    }

    public String selectLatestPartVersionRepeatSources(Map<String, Object> params) {
        return selectLatestRepeatSources(params, "");
    }

    private String selectLatestRepeatSources(Map<String, Object> params, String sourceFilter) {
        Object keysObject = params.get("keys");
        if (!(keysObject instanceof List<?> keys) || keys.isEmpty()) {
            return "SELECT * FROM item_quote_version WHERE 1 = 0";
        }

        StringBuilder values = new StringBuilder();
        for (int i = 0; i < keys.size(); i++) {
            if (i > 0) {
                values.append(", ");
            }
            values.append("(#{keys[").append(i).append("].partNo}, #{keys[")
                    .append(i).append("].version})");
        }

        String currentRfqFilter = params.get("currentRfqId") == null ? "" : "\n                      AND h.rfq_id <> #{currentRfqId}";
        return """
                WITH current_keys(part_no, version) AS (
                    VALUES %s
                )
                SELECT h.*
                FROM current_keys k
                JOIN LATERAL (
                    SELECT h.*
                    FROM item_quote_version h
                    WHERE h.part_no = k.part_no
                      AND h.version = k.version
                      %s
                      %s
                    ORDER BY h.id DESC
                    LIMIT 1
                ) h ON true
                ORDER BY h.id DESC
                """.formatted(values, currentRfqFilter, sourceFilter);
    }
}
