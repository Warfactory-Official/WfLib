#!/usr/bin/env python3
"""Extract files from STALKER (X-Ray) .db archives, for files GAMMA's mods don't ship loose (base-game textures).

Usage: xray-db.py <out dir> <glob> <archive> [<archive> ...]
  glob: fnmatch on the archive path, lower case, e.g. 'textures/pfx/*'
  e.g. xray-db.py /tmp/base 'textures/pfx/*' ~/Games/GAMMA/anomaly/db/textures/textures.db*

Then pass <out dir> as the last gamedata dir to xray-assets.py. Format as xrCore/LocatorAPI.cpp reads it: chunk 1
(LZHUF-compressed when flagged 0x80000000) lists u16 size, u32 real, u32 compressed, u32 crc, name, u32 offset;
data compressed with LZO1X (rtc_decompress) unless real == compressed. Needs liblzo2.
"""
import ctypes
import ctypes.util
import fnmatch
import os
import struct
import sys

COMPRESSED = 0x80000000

# LZHUF (Okumura/Yoshizaki, xrCore/LzHuf.cpp): N 4096, F 60, THRESHOLD 2.
N, F, THRESHOLD = 4096, 60, 2
N_CHAR = 256 - THRESHOLD + F
T = N_CHAR * 2 - 1
R = T - 1
MAX_FREQ = 0x4000
P_LEN = [3] + [4] * 3 + [5] * 8 + [6] * 12 + [7] * 24 + [8] * 16
P_CODE = [0x00, 0x20, 0x30, 0x40, 0x50, 0x58, 0x60, 0x68, 0x70, 0x78, 0x80, 0x88, 0x90, 0x94, 0x98, 0x9C,
          0xA0, 0xA4, 0xA8, 0xAC, 0xB0, 0xB4, 0xB8, 0xBC] + list(range(0xC0, 0xF0, 2)) + list(range(0xF0, 0x100))
D_CODE, D_LEN = [0] * 256, [0] * 256
for _i, (_len, _code) in enumerate(zip(P_LEN, P_CODE)):
    for _b in range(_code, _code + (1 << (8 - _len))):
        D_CODE[_b], D_LEN[_b] = _i, _len


def lzhuf(data):
    size = struct.unpack_from('<I', data)[0]
    pos, getbuf, getlen = 4, 0, 0

    def fill():
        nonlocal pos, getbuf, getlen
        while getlen <= 8:
            b = data[pos] if pos < len(data) else 0
            pos += 1
            getbuf |= b << (8 - getlen)
            getlen += 8

    def bit():
        nonlocal getbuf, getlen
        fill()
        b = (getbuf >> 15) & 1
        getbuf = (getbuf << 1) & 0xFFFF
        getlen -= 1
        return b

    def byte():
        nonlocal getbuf, getlen
        fill()
        b = (getbuf >> 8) & 0xFF
        getbuf = (getbuf << 8) & 0xFFFF
        getlen -= 8
        return b

    freq, son, prnt = [0] * (T + 1), [0] * T, [0] * (T + N_CHAR)
    for i in range(N_CHAR):
        freq[i], son[i], prnt[i + T] = 1, i + T, i
    i, j = 0, N_CHAR
    while j <= R:
        freq[j] = freq[i] + freq[i + 1]
        son[j] = i
        prnt[i] = prnt[i + 1] = j
        i, j = i + 2, j + 1
    freq[T], prnt[R] = 0xFFFF, 0

    def reconst():
        j = 0
        for i in range(T):
            if son[i] >= T:
                freq[j], son[j] = (freq[i] + 1) // 2, son[i]
                j += 1
        i, j = 0, N_CHAR
        while j < T:
            f = freq[j] = freq[i] + freq[i + 1]
            k = j - 1
            while f < freq[k]:
                k -= 1
            k += 1
            freq[k + 1:j + 1] = freq[k:j]
            freq[k] = f
            son[k + 1:j + 1] = son[k:j]
            son[k] = i
            i, j = i + 2, j + 1
        for i in range(T):
            k = son[i]
            prnt[k] = i
            if k < T:
                prnt[k + 1] = i

    def update(c):
        if freq[R] == MAX_FREQ:
            reconst()
        c = prnt[c + T]
        while True:
            freq[c] += 1
            k = freq[c]
            if k > freq[c + 1]:
                l = c + 1
                while k > freq[l + 1]:
                    l += 1
                freq[c], freq[l] = freq[l], k
                i = son[c]
                prnt[i] = l
                if i < T:
                    prnt[i + 1] = l
                j = son[l]
                son[l] = i
                prnt[j] = c
                if j < T:
                    prnt[j + 1] = c
                son[c] = j
                c = l
            c = prnt[c]
            if c == 0:
                break

    text = bytearray(b' ' * (N + F))
    out = bytearray()
    r = N - F
    while len(out) < size:
        c = son[R]
        while c < T:
            c = son[c + bit()]
        c -= T
        update(c)
        if c < 256:
            out.append(c)
            text[r] = c
            r = (r + 1) & (N - 1)
        else:
            i = byte()
            hi, n = D_CODE[i] << 6, D_LEN[i] - 2
            for _ in range(n):
                i = (i << 1) + bit()
            start = (r - (hi | (i & 0x3F)) - 1) & (N - 1)
            for k in range(c - 255 + THRESHOLD):
                b = text[(start + k) & (N - 1)]
                out.append(b)
                text[r] = b
                r = (r + 1) & (N - 1)
    return bytes(out[:size])


LZO = ctypes.CDLL(ctypes.util.find_library('lzo2') or 'liblzo2.so.2')


def lzo1x(data, size):
    out = ctypes.create_string_buffer(size)
    n = ctypes.c_ulong(size)
    if LZO.lzo1x_decompress_safe(data, len(data), out, ctypes.byref(n), None) != 0 or n.value != size:
        raise ValueError('lzo1x failed')
    return out.raw


def entries(path):
    with open(path, 'rb') as f:
        while (head := f.read(8)) and len(head) == 8:
            kind, size = struct.unpack('<II', head)
            if kind & ~COMPRESSED == 1:
                body = f.read(size)
                body = lzhuf(body) if kind & COMPRESSED else body
                at = 0
                while at < len(body):
                    n = struct.unpack_from('<H', body, at)[0]
                    real, packed, _crc = struct.unpack_from('<III', body, at + 2)
                    name = body[at + 14:at + 2 + n - 4].decode('latin-1')
                    offset = struct.unpack_from('<I', body, at + 2 + n - 4)[0]
                    yield name.replace('\\', '/').lower(), real, packed, offset
                    at += 2 + n
                return
            f.seek(size, 1)


def main():
    out, pattern, archives = sys.argv[1], sys.argv[2].lower(), sys.argv[3:]
    for archive in archives:
        with open(archive, 'rb') as f:
            for name, real, packed, offset in entries(archive):
                if not fnmatch.fnmatch(name, pattern) or real == 0:
                    continue
                f.seek(offset)
                data = f.read(packed)
                data = data if real == packed else lzo1x(data, real)
                dst = os.path.join(out, name)
                os.makedirs(os.path.dirname(dst), exist_ok=True)
                with open(dst, 'wb') as w:
                    w.write(data)
                print(f'{os.path.basename(archive)}: {name}')


if __name__ == '__main__':
    main()
