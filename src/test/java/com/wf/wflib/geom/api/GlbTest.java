package com.wf.wflib.geom.api;

import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class GlbTest {

    /** 2 VEC3 floats interleaved at stride 16, then 3 unsigned shorts. */
    private static byte[] bin() {
        ByteBuffer b = ByteBuffer.allocate(40).order(ByteOrder.LITTLE_ENDIAN);
        b.putFloat(1).putFloat(2).putFloat(3).putFloat(99).putFloat(4).putFloat(5).putFloat(6).putFloat(99);
        b.putShort((short) 1).putShort((short) 0).putShort((short) 65535).putShort((short) 0);
        return b.array();
    }

    private static String json(String buffer) {
        return "{\"buffers\":[" + buffer + "],"
                + "\"bufferViews\":[{\"buffer\":0,\"byteLength\":32,\"byteStride\":16},"
                + "{\"buffer\":0,\"byteOffset\":32,\"byteLength\":6}],"
                + "\"accessors\":[{\"bufferView\":0,\"componentType\":5126,\"count\":2,\"type\":\"VEC3\"},"
                + "{\"bufferView\":1,\"componentType\":5123,\"count\":3,\"type\":\"SCALAR\"},"
                + "{\"bufferView\":1,\"componentType\":5126,\"count\":1,\"type\":\"SCALAR\",\"sparse\":{}}]}";
    }

    private static byte[] glb(String json, byte[] bin) {
        byte[] j = json.getBytes(StandardCharsets.UTF_8);
        int jl = (j.length + 3) & ~3;
        ByteBuffer b = ByteBuffer.allocate(12 + 8 + jl + 8 + bin.length).order(ByteOrder.LITTLE_ENDIAN);
        b.putInt(0x46546C67).putInt(2).putInt(b.capacity());
        b.putInt(jl).putInt(0x4E4F534A).put(j);
        for (int i = j.length; i < jl; i++) {
            b.put((byte) ' ');
        }
        b.putInt(bin.length).putInt(0x004E4942).put(bin);
        return b.array();
    }

    private static void reads(Glb g) throws IOException {
        assertArrayEquals(new float[]{1, 2, 3, 4, 5, 6}, g.floats(0, 3));
        assertArrayEquals(new int[]{1, 0, 65535}, g.indices(1));
        assertThrows(IOException.class, () -> g.floats(2, 1));
    }

    @Test
    void glbBinChunk() throws IOException {
        reads(Glb.read(glb(json("{\"byteLength\":40}"), bin()), (uri, location) -> {
            throw new IOException("no external buffer");
        }));
    }

    @Test
    void dataUriAndExternal() throws IOException {
        String data = "{\"uri\":\"data:application/octet-stream;base64," + Base64.getEncoder().encodeToString(bin()) + "\"}";
        reads(Glb.read(json(data).getBytes(StandardCharsets.UTF_8), (uri, location) -> null));
        reads(Glb.read(json("{\"uri\":\"m.bin\"}").getBytes(StandardCharsets.UTF_8),
                (uri, location) -> "m.bin".equals(uri) ? bin() : null));
    }

    @Test
    void trsIsTranslateRotateScale() {
        Matrix4f m = Glb.local(com.google.gson.JsonParser.parseString(
                "{\"translation\":[1,2,3],\"rotation\":[0,0.7071068,0,0.7071068],\"scale\":[2,2,2]}").getAsJsonObject());
        Vector3f p = m.transformPosition(new Vector3f(1, 0, 0));
        assertEquals(1, p.x, 1e-5);
        assertEquals(2, p.y, 1e-5);
        assertEquals(1, p.z, 1e-5);
    }
}
