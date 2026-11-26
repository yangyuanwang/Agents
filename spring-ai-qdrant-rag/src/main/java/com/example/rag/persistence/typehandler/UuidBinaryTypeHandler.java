package com.example.rag.persistence.typehandler;

import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;
import org.apache.ibatis.type.MappedJdbcTypes;
import org.apache.ibatis.type.MappedTypes;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;

/** Stores RFC 4122 UUID bytes in network order, matching MySQL BINARY(16). */
@MappedTypes(UUID.class)
@MappedJdbcTypes(JdbcType.BINARY)
public final class UuidBinaryTypeHandler extends BaseTypeHandler<UUID> {
    @Override
    public void setNonNullParameter(PreparedStatement ps, int i, UUID value, JdbcType jdbcType) throws SQLException {
        ps.setBytes(i, toBytes(value));
    }

    @Override
    public UUID getNullableResult(ResultSet rs, String columnName) throws SQLException {
        return fromBytes(rs.getBytes(columnName));
    }

    @Override
    public UUID getNullableResult(ResultSet rs, int columnIndex) throws SQLException {
        return fromBytes(rs.getBytes(columnIndex));
    }

    @Override
    public UUID getNullableResult(CallableStatement cs, int columnIndex) throws SQLException {
        return fromBytes(cs.getBytes(columnIndex));
    }

    static byte[] toBytes(UUID uuid) {
        byte[] bytes = new byte[16];
        long msb = uuid.getMostSignificantBits();
        long lsb = uuid.getLeastSignificantBits();
        for (int i = 0; i < 8; i++) {
            bytes[i] = (byte) (msb >>> (56 - 8 * i));
            bytes[8 + i] = (byte) (lsb >>> (56 - 8 * i));
        }
        return bytes;
    }

    static UUID fromBytes(byte[] bytes) throws SQLException {
        if (bytes == null) return null;
        if (bytes.length != 16) throw new SQLException("Expected 16 bytes for UUID, got " + bytes.length);
        long msb = 0;
        long lsb = 0;
        for (int i = 0; i < 8; i++) {
            msb = (msb << 8) | (bytes[i] & 0xffL);
            lsb = (lsb << 8) | (bytes[8 + i] & 0xffL);
        }
        return new UUID(msb, lsb);
    }
}
