package com.example.rag.persistence;

import com.example.rag.persistence.typehandler.UuidBinaryTypeHandler;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

class UuidBinaryTypeHandlerTest {
    @Test
    void encodesRfc4122BytesInNetworkOrder() throws Exception {
        UUID uuid = UUID.fromString("00112233-4455-6677-8899-aabbccddeeff");
        Method encode = UuidBinaryTypeHandler.class.getDeclaredMethod("toBytes", UUID.class);
        encode.setAccessible(true);
        byte[] bytes = (byte[]) encode.invoke(null, uuid);
        assertArrayEquals(new byte[]{0,0x11,0x22,0x33,0x44,0x55,0x66,0x77,(byte)0x88,(byte)0x99,(byte)0xaa,(byte)0xbb,(byte)0xcc,(byte)0xdd,(byte)0xee,(byte)0xff}, bytes);
        Method decode = UuidBinaryTypeHandler.class.getDeclaredMethod("fromBytes", byte[].class);
        decode.setAccessible(true);
        assertEquals(uuid, decode.invoke(null, (Object) bytes));
    }
}
