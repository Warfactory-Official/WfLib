package com.wf.wflib.geom.api;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.joml.Quaternionf;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

/**
 * A {@code .gltf} / {@code .glb}: container, JSON, buffers, node transforms, float/index accessor reads. Scene walks
 * and what nodes mean stay with the reader.
 */
public final class Glb {

    public static final int FLOAT = 5126;
    public static final int UNSIGNED_BYTE = 5121;
    public static final int UNSIGNED_SHORT = 5123;
    public static final int UNSIGNED_INT = 5125;
    private static final int GLB_MAGIC = 0x46546C67;
    private static final int CHUNK_JSON = 0x4E4F534A;

    /** External buffer: {@code uri} relative to the model, else {@code extras.resourceLocation} (ns:path). */
    @FunctionalInterface
    public interface Buffers {
        byte[] open(@Nullable String uri, @Nullable String resourceLocation) throws IOException;
    }

    private final JsonObject json;
    private final List<ByteBuffer> buffers;

    private Glb(JsonObject json, List<ByteBuffer> buffers) {
        this.json = json;
        this.buffers = buffers;
    }

    public JsonObject json() {
        return json;
    }

    /** JSON only: all of a {@code .gltf}; a {@code .glb}'s JSON chunk. */
    public static JsonObject json(byte[] file) throws IOException {
        ByteBuffer data = ByteBuffer.wrap(file).order(ByteOrder.LITTLE_ENDIAN);
        if (file.length >= 12 && data.getInt(0) == GLB_MAGIC) {
            if (data.getInt(16) != CHUNK_JSON) {
                throw new IOException("GLB: first chunk is not JSON");
            }
            return JsonParser.parseString(new String(file, 20, data.getInt(12), StandardCharsets.UTF_8)).getAsJsonObject();
        }
        return JsonParser.parseString(new String(file, StandardCharsets.UTF_8)).getAsJsonObject();
    }

    /** JSON + every buffer: GLB BIN chunk, {@code data:} URIs, else {@code buffers}. */
    public static Glb read(byte[] file, Buffers buffers) throws IOException {
        JsonObject gltf = json(file);
        ByteBuffer data = ByteBuffer.wrap(file).order(ByteOrder.LITTLE_ENDIAN);
        ByteBuffer glbBinary = null;
        if (file.length >= 12 && data.getInt(0) == GLB_MAGIC) {
            int binAt = 20 + data.getInt(12);
            if (binAt + 8 <= file.length) {
                int binLength = data.getInt(binAt);
                glbBinary = ByteBuffer.wrap(file, binAt + 8, binLength).slice().order(ByteOrder.LITTLE_ENDIAN);
            }
        }
        List<ByteBuffer> bufferData = new ArrayList<>();
        for (JsonElement element : array(gltf, "buffers")) {
            JsonObject buffer = element.getAsJsonObject();
            JsonObject extras = buffer.has("extras") ? buffer.getAsJsonObject("extras") : new JsonObject();
            String location = extras.has("resourceLocation") ? extras.get("resourceLocation").getAsString() : null;
            if (!buffer.has("uri") && location == null) {
                if (glbBinary == null) {
                    throw new IOException("buffer without uri or resourceLocation outside a GLB");
                }
                bufferData.add(glbBinary);
                continue;
            }
            String uri = buffer.has("uri") ? buffer.get("uri").getAsString() : null;
            byte[] bytes;
            if (uri != null && uri.startsWith("data:")) {
                bytes = Base64.getDecoder().decode(uri.substring(uri.indexOf(',') + 1));
            } else {
                bytes = buffers.open(uri, location);
            }
            bufferData.add(ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN));
        }
        return new Glb(gltf, bufferData);
    }

    /** Empty when absent. */
    public static JsonArray array(JsonObject object, String key) {
        return object.has(key) ? object.getAsJsonArray(key) : new JsonArray();
    }

    /** Node-to-parent: {@code matrix} (column-major) if declared, else T * R * S. */
    public static Matrix4f local(JsonObject node) {
        if (node.has("matrix")) {
            float[] m = new float[16];
            JsonArray values = node.getAsJsonArray("matrix");
            for (int i = 0; i < 16; i++) {
                m[i] = values.get(i).getAsFloat();
            }
            return new Matrix4f().set(m);
        }
        Matrix4f m = new Matrix4f();
        if (node.has("translation")) {
            JsonArray t = node.getAsJsonArray("translation");
            m.translate(t.get(0).getAsFloat(), t.get(1).getAsFloat(), t.get(2).getAsFloat());
        }
        if (node.has("rotation")) {
            JsonArray r = node.getAsJsonArray("rotation");
            m.rotate(new Quaternionf(r.get(0).getAsFloat(), r.get(1).getAsFloat(), r.get(2).getAsFloat(),
                    r.get(3).getAsFloat()));
        }
        if (node.has("scale")) {
            JsonArray s = node.getAsJsonArray("scale");
            m.scale(s.get(0).getAsFloat(), s.get(1).getAsFloat(), s.get(2).getAsFloat());
        }
        return m;
    }

    public JsonObject accessor(int index) {
        return array(json, "accessors").get(index).getAsJsonObject();
    }

    /** FLOAT accessor, {@code width} components per element. */
    public float[] floats(int accessorIndex, int width) throws IOException {
        JsonObject accessor = accessor(accessorIndex);
        if (accessor.get("componentType").getAsInt() != FLOAT) {
            throw new IOException("accessor " + accessorIndex + " is not float (quantized data unsupported)");
        }
        int count = accessor.get("count").getAsInt();
        View view = view(accessor, width * 4);
        float[] out = new float[count * width];
        for (int i = 0; i < count; i++) {
            for (int c = 0; c < width; c++) {
                out[i * width + c] = view.data.getFloat(view.offset + i * view.stride + c * 4);
            }
        }
        return out;
    }

    /** Unsigned byte / short / int accessor. */
    public int[] indices(int accessorIndex) throws IOException {
        JsonObject accessor = accessor(accessorIndex);
        int type = accessor.get("componentType").getAsInt();
        int size = switch (type) {
            case UNSIGNED_BYTE -> 1;
            case UNSIGNED_SHORT -> 2;
            case UNSIGNED_INT -> 4;
            default -> throw new IOException("index accessor " + accessorIndex + " has component type " + type);
        };
        int count = accessor.get("count").getAsInt();
        View view = view(accessor, size);
        int[] out = new int[count];
        for (int i = 0; i < count; i++) {
            int at = view.offset + i * view.stride;
            out[i] = switch (size) {
                case 1 -> view.data.get(at) & 0xFF;
                case 2 -> view.data.getShort(at) & 0xFFFF;
                default -> view.data.getInt(at);
            };
        }
        return out;
    }

    private record View(ByteBuffer data, int offset, int stride) {
    }

    private View view(JsonObject accessor, int elementSize) throws IOException {
        if (accessor.has("sparse") || !accessor.has("bufferView")) {
            throw new IOException("sparse or view-less accessors unsupported");
        }
        JsonObject view = array(json, "bufferViews").get(accessor.get("bufferView").getAsInt()).getAsJsonObject();
        ByteBuffer data = buffers.get(view.get("buffer").getAsInt());
        int offset = (view.has("byteOffset") ? view.get("byteOffset").getAsInt() : 0)
                + (accessor.has("byteOffset") ? accessor.get("byteOffset").getAsInt() : 0);
        int stride = view.has("byteStride") ? view.get("byteStride").getAsInt() : elementSize;
        return new View(data, offset, stride);
    }
}
