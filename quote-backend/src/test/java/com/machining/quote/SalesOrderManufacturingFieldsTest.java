package com.machining.quote;

import com.machining.quote.dto.SalesOrderQpoExcelParseResult;
import com.machining.quote.dto.SalesOrderQpoImportDefaultsDTO;
import com.machining.quote.entity.OrderDeliveryTicket;
import com.machining.quote.entity.OrderDeliveryTicketAllocation;
import com.machining.quote.service.SalesOrderQpoExcelParser;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SalesOrderManufacturingFieldsTest {
    @Test
    void excelOverridesStayOnItemsAndBlanksInheritOnlyOrderDefaults() throws Exception {
        SalesOrderQpoImportDefaultsDTO defaults = new SalesOrderQpoImportDefaultsDTO();
        defaults.setManufacturingProcessType("订单加工");
        defaults.setReservedField1("订单备用1");
        defaults.setReservedField2("订单备用2");
        defaults.setReservedField3("订单备用3");
        SalesOrderQpoExcelParseResult parsed = new SalesOrderQpoExcelParser().parse(workbook("零件加工"), defaults);
        var order = parsed.getOrders().get(0);
        assertThat(order.getManufacturingProcessType()).isEqualTo("订单加工");
        assertThat(order.getReservedField1()).isEqualTo("订单备用1");
        assertThat(order.getLines().get(0).getManufacturingProcessType()).isEqualTo("零件加工");
        assertThat(order.getLines().get(0).getReservedField1()).isEqualTo("零件备用1");
        assertThat(order.getLines().get(1).getManufacturingProcessType()).isEqualTo("订单加工");
        assertThat(order.getLines().get(1).getReservedField1()).isEqualTo("订单备用1");
        assertThat(order.getLines().get(1).getReservedField2()).isEqualTo("订单备用2");
        assertThat(order.getLines().get(1).getReservedField3()).isEqualTo("订单备用3");

        var withoutDefaults = new SalesOrderQpoExcelParser().parse(workbook("零件加工")).getOrders().get(0);
        assertThat(withoutDefaults.getManufacturingProcessType()).isNull();
        assertThat(withoutDefaults.getReservedField1()).isNull();
        assertThat(withoutDefaults.getLines().get(0).getManufacturingProcessType()).isEqualTo("零件加工");
        assertThat(withoutDefaults.getLines().get(1).getManufacturingProcessType()).isEmpty();
    }

    @Test
    void excelAccepts120CharactersAndRejects121() throws Exception {
        assertThat(new SalesOrderQpoExcelParser().parse(workbook("字".repeat(120)))
                .getOrders().get(0).getLines().get(0).getManufacturingProcessType()).hasSize(120);
        assertThatThrownBy(() -> new SalesOrderQpoExcelParser().parse(workbook("字".repeat(121))))
                .hasMessageContaining("制造加工类型").hasMessageContaining("120");
    }

    @Test
    void migrationOnlyAddsTwelveOptionalColumnsToTheThreeBusinessTables() throws Exception {
        String sql = Files.readString(Path.of("src/main/resources/db/migration/V62__sales_order_manufacturing_fields.sql"),
                StandardCharsets.UTF_8);
        assertThat(sql.split("ALTER TABLE", -1)).hasSize(4);
        assertThat(sql.split("VARCHAR\\(120\\)", -1)).hasSize(13);
        assertThat(sql).contains("ALTER TABLE item_quote_version", "ALTER TABLE sales_order_header", "ALTER TABLE sales_order_item")
                .doesNotContain("NOT NULL", "UPDATE ", "INSERT ", "order_delivery_ticket", "allocation");
        for (Class<?> type : List.of(OrderDeliveryTicket.class, OrderDeliveryTicketAllocation.class)) {
            assertThat(Arrays.stream(type.getDeclaredFields()).map(java.lang.reflect.Field::getName))
                    .doesNotContain("manufacturingProcessType", "reservedField1", "reservedField2", "reservedField3");
        }
    }

    private ByteArrayInputStream workbook(String manufacturingType) throws Exception {
        try (var workbook = new XSSFWorkbook(); var output = new ByteArrayOutputStream()) {
            var sheet = workbook.createSheet();
            String[] headers = {"图号", "版本", "订单数量", "制造加工类型", "备用字段1", "备用字段2", "备用字段3"};
            Row header = sheet.createRow(0);
            for (int index = 0; index < headers.length; index++) {
                header.createCell(index).setCellValue(headers[index]);
            }
            Row first = sheet.createRow(1);
            first.createCell(0).setCellValue("PN-001");
            first.createCell(1).setCellValue("A");
            first.createCell(2).setCellValue(10);
            first.createCell(3).setCellValue(manufacturingType);
            first.createCell(4).setCellValue("零件备用1");
            first.createCell(5).setCellValue("零件备用2");
            first.createCell(6).setCellValue("零件备用3");
            Row second = sheet.createRow(2);
            second.createCell(0).setCellValue("PN-002");
            second.createCell(1).setCellValue("A");
            second.createCell(2).setCellValue(5);
            workbook.write(output);
            return new ByteArrayInputStream(output.toByteArray());
        }
    }
}
