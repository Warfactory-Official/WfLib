package com.wf.wflib.radiation;

/** Bit twiddles over {@code SectionPos.asLong} (x22|z22|y20), {@code ChunkPos.asLong} and {@code BlockPos.asLong}. */
public final class SectionKeys {

    private SectionKeys() {
    }

    /** {@code (y<<8)|(z<<4)|x} of the block within its section. */
    public static int blockPosToLocal(long serialized) {
        return ((int) (serialized & 0xFL) << 8)
                | (((int) (serialized >>> 12) & 0xF) << 4)
                | ((int) (serialized >>> 38) & 0xF);
    }

    public static int getLocalX(int packed) {
        return packed & 0xF;
    }

    public static int getLocalY(int packed) {
        return (packed >>> 8) & 0xF;
    }

    public static int getLocalZ(int packed) {
        return (packed >>> 4) & 0xF;
    }

    public static long sectionToLong(long ck, int subY) {
        return (ck << 42) | ((ck >>> 12) & 0x0000_03FF_FFF0_0000L) | (((long) subY) & 0xFFFFFL);
    }

    public static long setSectionY(long key, int subY) {
        return (key & ~0xFFFFFL) | (((long) subY) & 0xFFFFFL);
    }

    public static long shiftSectionX(long key, int dx) {
        return (key & ~(0x3FFFFFL << 42)) | (((key >>> 42) + dx & 0x3FFFFFL) << 42);
    }

    public static long shiftSectionZ(long key, int dz) {
        return (key & ~(0x3FFFFFL << 20)) | ((((key >>> 20) & 0x3FFFFFL) + dz & 0x3FFFFFL) << 20);
    }

    public static long sectionToChunkLong(long sck) {
        return (((sck << 22) >> 10) & 0xFFFF_FFFF_0000_0000L) | ((sck >> 42) & 0xFFFF_FFFFL);
    }
}
