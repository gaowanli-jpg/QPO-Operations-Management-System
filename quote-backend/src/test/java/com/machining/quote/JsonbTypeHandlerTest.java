package com.machining.quote;

import com.machining.quote.config.JsonbTypeHandler;
import org.junit.jupiter.api.Test;

import java.sql.PreparedStatement;
import java.sql.Types;
import java.time.LocalDate;
import java.util.Map;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class JsonbTypeHandlerTest {
    @Test
    void serializesJavaTimeValuesForJsonbColumns() throws Exception {
        JsonbTypeHandler handler = new JsonbTypeHandler();
        PreparedStatement statement = mock(PreparedStatement.class);

        handler.setNonNullParameter(
                statement,
                7,
                Map.of("salesOrderDate", LocalDate.of(2026, 7, 1)),
                null
        );

        verify(statement).setObject(7, "{\"salesOrderDate\":\"2026-07-01\"}", Types.OTHER);
    }
}
