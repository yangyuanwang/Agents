package com.example.rag.persistence.typehandler;

import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;
import org.apache.ibatis.type.MappedJdbcTypes;
import org.apache.ibatis.type.MappedTypes;
import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/** Persists full SHA-256 digests as exactly 32 raw bytes in MySQL BINARY(32). */
@MappedTypes(byte[].class)
@MappedJdbcTypes(JdbcType.BINARY)
public final class Sha256BinaryTypeHandler extends BaseTypeHandler<byte[]> {
    private static final int LENGTH = 32;
    @Override public void setNonNullParameter(PreparedStatement ps, int i, byte[] value, JdbcType jdbcType) throws SQLException {
        validate(value); ps.setBytes(i, value);
    }
    @Override public byte[] getNullableResult(ResultSet rs, String columnName) throws SQLException { return checked(rs.getBytes(columnName)); }
    @Override public byte[] getNullableResult(ResultSet rs, int columnIndex) throws SQLException { return checked(rs.getBytes(columnIndex)); }
    @Override public byte[] getNullableResult(CallableStatement cs, int columnIndex) throws SQLException { return checked(cs.getBytes(columnIndex)); }
    private static byte[] checked(byte[] value) throws SQLException { if (value != null) validate(value); return value; }
    private static void validate(byte[] value) throws SQLException {
        if (value.length != LENGTH) throw new SQLException("Expected 32-byte SHA-256 digest, got " + value.length);
    }
}
