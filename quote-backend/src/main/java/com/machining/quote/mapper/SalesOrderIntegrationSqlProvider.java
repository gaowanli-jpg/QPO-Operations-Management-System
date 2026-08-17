package com.machining.quote.mapper;

public class SalesOrderIntegrationSqlProvider {
    public String selectOrderPage() {
        return """
                <script>
                WITH order_changed AS (
                  SELECT
                    so.id,
                    GREATEST(so.updated_at, COALESCE(MAX(soi.updated_at), so.updated_at)) AS changed_at
                  FROM sales_order_header so
                  LEFT JOIN sales_order_item soi ON soi.sales_order_id = so.id
                  WHERE 1 = 1
                    <if test='req.saleOrderNo != null and req.saleOrderNo != ""'>
                    AND so.sales_order_no = #{req.saleOrderNo}
                    </if>
                    <if test='req.status != null and req.status != ""'>
                    AND so.status = #{req.status}
                    </if>
                    <if test='req.createdFrom != null'>
                    AND so.created_at >= #{req.createdFrom}
                    </if>
                    <if test='req.createdTo != null'>
                    AND so.created_at &lt;= #{req.createdTo}
                    </if>
                    <if test='(req.materialNo != null and req.materialNo != "") or (req.partNo != null and req.partNo != "") or (req.version != null and req.version != "")'>
                    AND EXISTS (
                      SELECT 1
                      FROM sales_order_item f
                      WHERE f.sales_order_id = so.id
                        <if test='req.materialNo != null and req.materialNo != ""'>
                        AND f.material_no = #{req.materialNo}
                        </if>
                        <if test='req.partNo != null and req.partNo != ""'>
                        AND f.part_no = #{req.partNo}
                        </if>
                        <if test='req.version != null and req.version != ""'>
                        AND f.version = #{req.version}
                        </if>
                    )
                    </if>
                  GROUP BY so.id, so.updated_at
                )
                SELECT
                  so.id AS sales_order_id,
                  so.sales_order_no AS sale_order_no,
                  so.internal_project_no,
                  so.order_type,
                  so.status,
                  so.created_at,
                  oc.changed_at
                FROM order_changed oc
                JOIN sales_order_header so ON so.id = oc.id
                WHERE 1 = 1
                  <if test='req.updatedFrom != null'>
                  AND oc.changed_at >= #{req.updatedFrom}
                  </if>
                  <if test='req.updatedTo != null'>
                  AND oc.changed_at &lt;= #{req.updatedTo}
                  </if>
                  <if test='cursorChangedAt != null and cursorSalesOrderId != null'>
                  AND (oc.changed_at > #{cursorChangedAt} OR (oc.changed_at = #{cursorChangedAt} AND so.id > #{cursorSalesOrderId}))
                  </if>
                ORDER BY oc.changed_at ASC, so.id ASC
                LIMIT #{limit}
                </script>
                """;
    }

    public String selectItemsByOrderIds() {
        return """
                <script>
                SELECT
                  soi.sales_order_id,
                  soi.line_no,
                  soi.description,
                  soi.material_no,
                  soi.surface_treatment,
                  soi.remark,
                  GREATEST(COALESCE(soi.released_qty, 0), 0) AS released_qty
                FROM sales_order_item soi
                WHERE soi.sales_order_id IN
                  <foreach collection='orderIds' item='orderId' open='(' separator=',' close=')'>
                  #{orderId}
                  </foreach>
                  <if test='req.materialNo != null and req.materialNo != ""'>
                  AND soi.material_no = #{req.materialNo}
                  </if>
                  <if test='req.partNo != null and req.partNo != ""'>
                  AND soi.part_no = #{req.partNo}
                  </if>
                  <if test='req.version != null and req.version != ""'>
                  AND soi.version = #{req.version}
                  </if>
                ORDER BY soi.sales_order_id ASC, soi.line_no ASC, soi.id ASC
                </script>
                """;
    }
}
