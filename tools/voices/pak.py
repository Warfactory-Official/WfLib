"""Minimal reader for unencrypted, uncompressed Unreal .pak v10/v11 (path-hash + full-directory index).

Enough to list and extract assets (e.g. Trepang2 voice lines) without external tools.
Unsupported (raises): encrypted index, compressed entries.
"""
import struct

MAGIC = 0x5A6F12E1


class Pak:
    def __init__(self, path):
        self.f = open(path, 'rb')
        self.entries = {}  # full path -> (offset, size)
        self._read()

    @staticmethod
    def _fstr(b, p):
        n, = struct.unpack_from('<i', b, p)
        p += 4
        if n == 0:
            return '', p
        if n < 0:
            n = -n
            return b[p:p + 2 * n - 2].decode('utf-16le'), p + 2 * n
        return b[p:p + n - 1].decode('utf-8', 'replace'), p + n

    def _read(self):
        f = self.f
        for foot in (221, 189, 205, 61):  # v11, v10, v9-ish, v8-
            f.seek(-foot, 2)
            b = f.read(foot)
            j = b.find(struct.pack('<I', MAGIC))
            if j >= 0:
                break
        else:
            raise ValueError('not a pak')
        enc = b[:j][:1]
        ver, off, size = struct.unpack_from('<IQQ', b, j + 4)
        if ver < 10:
            raise ValueError('pak version %d unsupported' % ver)
        f.seek(off)
        idx = f.read(size)
        mount, p = self._fstr(idx, 0)
        count, seed = struct.unpack_from('<IQ', idx, p)
        p += 12
        has_ph, = struct.unpack_from('<I', idx, p)
        p += 4
        if has_ph:
            p += 8 + 8 + 20
        has_fdi, = struct.unpack_from('<I', idx, p)
        p += 4
        fdi_off, fdi_size = struct.unpack_from('<qq', idx, p)
        p += 16 + 20
        enc_size, = struct.unpack_from('<i', idx, p)
        p += 4
        self.encoded = idx[p:p + enc_size]
        if not has_fdi:
            raise ValueError('no full directory index')
        f.seek(fdi_off)
        fdi = f.read(fdi_size)
        p = 0
        ndirs, = struct.unpack_from('<I', fdi, p)
        p += 4
        for _ in range(ndirs):
            d, p = self._fstr(fdi, p)
            nf, = struct.unpack_from('<I', fdi, p)
            p += 4
            for _ in range(nf):
                name, p = self._fstr(fdi, p)
                eo, = struct.unpack_from('<i', fdi, p)
                p += 4
                full = (mount + d + name).lstrip('/')
                ent = self._decode(eo)
                if ent:
                    self.entries[full] = ent

    def _decode(self, o):
        e = self.encoded
        bits, = struct.unpack_from('<I', e, o)
        o += 4
        if (bits >> 23) & 0x3f:
            return None  # compressed: skipped by the listing
        if bits & (1 << 22):
            return None  # encrypted
        vals = []
        for flag in (31, 30):  # offset, uncompressed size: 32-bit when the flag is set
            if bits & (1 << flag):
                v, = struct.unpack_from('<I', e, o)
                o += 4
            else:
                v, = struct.unpack_from('<Q', e, o)
                o += 8
            vals.append(v)
        return vals[0], vals[1]

    HEADER = 53  # in-file FPakEntry for an uncompressed entry, version 11

    def read(self, name):
        off, size = self.entries[name]
        self.f.seek(off + self.HEADER)
        return self.f.read(size)
