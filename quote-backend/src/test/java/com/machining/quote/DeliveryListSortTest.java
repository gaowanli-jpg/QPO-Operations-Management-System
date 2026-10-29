package com.machining.quote;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 已提零件 / 待产零件 / 锁库零件三个列表的排序与单页上限约束。
 *
 * <p>排序字段必须走服务端白名单 + Mapper 里的 choose 分支，禁止把前端参数拼进 SQL；
 * 分页上限与待产零件对齐到 1000 行，但客户下拉等辅助分页仍保持 100。</p>
 */
class DeliveryListSortTest {
    private static final Path DTO_DIR = Path.of("src", "main", "java", "com", "machining", "quote", "dto");
    private static final Path SERVICE_DIR = Path.of("src", "main", "java", "com", "machining", "quote", "service");
    private static final Path MAPPER_DIR = Path.of("src", "main", "java", "com", "machining", "quote", "mapper");

    @Test
    void listQueryDtosCarryWhitelistedSortParams() throws IOException {
        for (String dto : new String[]{"DeliveredItemQueryReq", "LockedItemQueryReq", "ProductionItemQueryReq"}) {
            assertThat(read(DTO_DIR.resolve(dto + ".java")))
                    .as("%s 需要接收前端表头排序参数", dto)
                    .contains("private String sortBy;", "private String sortOrder;");
        }
    }

    @Test
    void deliveredItemMapperSortsOnlyByWhitelistedColumns() throws IOException {
        String source = read(MAPPER_DIR.resolve("OrderDeliveryTicketMapper.java"));

        assertThat(source)
                .contains(
                        "<when test=\"sortBy == null\">",
                        "ORDER BY ticket.id DESC",
                        "<when test=\"sortBy == 'deliveryDate'\">delivery_date</when>",
                        "<when test=\"sortBy == 'discardedAt'\">discarded_at</when>",
                        "<otherwise>created_at</otherwise>",
                        "NULLS LAST",
                        "@Param(\"sortBy\") String sortBy",
                        "@Param(\"sortAsc\") boolean sortAsc"
                );
        assertThat(source)
                .as("前端排序参数不得拼进 SQL")
                .doesNotContain("${sortBy}", "${sortOrder}", "${sortAsc}");
    }

    @Test
    void lockedAndProductionItemMapperSortsByOrderDateDeliveryDateAndCreatedAt() throws IOException {
        String source = read(MAPPER_DIR.resolve("OrderDeliveryTicketAllocationMapper.java"));

        assertThat(source)
                .contains(
                        "<when test=\"sortBy == null\">",
                        "ORDER BY alloc.id DESC",
                        "<when test=\"sortBy == 'salesOrderDate'\">sales_order_date</when>",
                        "<when test=\"sortBy == 'deliveryDate'\">delivery_date</when>",
                        "<when test=\"sortBy == 'discardedAt'\">discarded_at</when>",
                        "<otherwise>created_at</otherwise>",
                        "NULLS LAST",
                        "@Param(\"sortBy\") String sortBy",
                        "@Param(\"sortAsc\") boolean sortAsc"
                );
        assertThat(source)
                .as("前端排序参数不得拼进 SQL")
                .doesNotContain("${sortBy}", "${sortOrder}", "${sortAsc}");
    }

    @Test
    void listServicesValidateSortFieldsAndRaisePageSizeTo1000() throws IOException {
        String delivered = read(SERVICE_DIR.resolve("DeliveredItemService.java"));
        String locked = read(SERVICE_DIR.resolve("LockedItemService.java"));
        String production = read(SERVICE_DIR.resolve("ProductionItemService.java"));

        assertThat(delivered)
                .contains(
                        "private static final Set<String> LIST_SORT_FIELDS = Set.of(\"deliveryDate\", \"createdAt\", \"discardedAt\");",
                        "normalizeSortBy(effectiveReq.getSortBy()), isAscending(effectiveReq.getSortOrder())",
                        "private int normalizeListPageSize(Integer pageSize)",
                        "Math.min(pageSize, 1000)"
                );
        assertThat(locked)
                .contains(
                        "private static final Set<String> LIST_SORT_FIELDS = Set.of(\"deliveryDate\", \"createdAt\", \"discardedAt\");",
                        "normalizeSortBy(effectiveReq.getSortBy()), isAscending(effectiveReq.getSortOrder())",
                        "private int normalizeListPageSize(Integer pageSize)",
                        "Math.min(pageSize, 1000)"
                );
        assertThat(production)
                .contains(
                        "Set.of(\"salesOrderDate\", \"deliveryDate\", \"createdAt\", \"discardedAt\")",
                        "normalizeSortBy(effectiveReq.getSortBy()), isAscending(effectiveReq.getSortOrder())"
                );
        assertThat(production)
                .as("待产零件的客户下拉分页仍按 100 限制，只有列表放到 1000")
                .contains("return Math.min(pageSize, 100);");
    }

    private String read(Path path) throws IOException {
        return Files.readString(path, StandardCharsets.UTF_8);
    }
}
