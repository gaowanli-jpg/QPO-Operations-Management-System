package com.machining.quote.config;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;

public class JsonbTypeHandler extends BaseTypeHandler<Object> {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper()
            .findAndRegisterModules()
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    @Override
    public void setNonNullParameter(PreparedStatement ps, int i, Object parameter, JdbcType jdbcType) throws SQLException {
        ps.setObject(i, toJson(parameter), Types.OTHER);
    }

    @Override
    public Object getNullableResult(ResultSet rs, String columnName) throws SQLException {
        return fromJson(rs.getString(columnName));
    }

    @Override
    public Object getNullableResult(ResultSet rs, int columnIndex) throws SQLException {
        return fromJson(rs.getString(columnIndex));
    }

    @Override
    public Object getNullableResult(CallableStatement cs, int columnIndex) throws SQLException {
        return fromJson(cs.getString(columnIndex));
    }

    private String toJson(Object value) throws SQLException {
        if (value instanceof String text) {
            return text;
        }
        try {
            return OBJECT_MAPPER.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new SQLException("JSONB 序列化失败", e);
        }
    }

    private Object fromJson(String text) throws SQLException {
        if (text == null || text.isBlank()) {
            return null;
        }
        try {
            return OBJECT_MAPPER.readValue(text, Object.class);
        } catch (JsonProcessingException e) {
            throw new SQLException("JSONB 反序列化失败", e);
        }
    }
}
