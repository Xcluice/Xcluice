#!/usr/bin/env python3
"""make_patch.py OLD.apk NEW.apk OUT.patch

Builds an Xcluice incremental update patch (bsdiff, deflate-packed) that the app applies to its own installed APK.
Container: "XPT1" | sha256(new apk)[32] | newSize u64 | ctrlC u64 | diffC u64 | extraC u64 | ctrl | diff | extra  (big-endian, zlib streams)
"""
import bsdiff4, bz2, hashlib, struct, sys, zlib

def offtin(b):
    v = int.from_bytes(b[:7] + bytes([b[7] & 0x7F]), "little")
    return -v if b[7] & 0x80 else v

old = open(sys.argv[1], "rb").read()
new = open(sys.argv[2], "rb").read()
raw = bsdiff4.diff(old, new)
assert raw[:8] == b"BSDIFF40"
clen, dlen, nsize = offtin(raw[8:16]), offtin(raw[16:24]), offtin(raw[24:32])
assert nsize == len(new)
ctrl = bz2.decompress(raw[32:32 + clen])
diff = bz2.decompress(raw[32 + clen:32 + clen + dlen])
extra = bz2.decompress(raw[32 + clen + dlen:])
z = lambda b: zlib.compress(b, 9)
c, d, e = z(ctrl), z(diff), z(extra)
out = b"XPT1" + hashlib.sha256(new).digest() + struct.pack(">QQQQ", nsize, len(c), len(d), len(e)) + c + d + e
open(sys.argv[3], "wb").write(out)
print(f"{sys.argv[3]}: {len(out)} bytes ({100*len(out)//len(new)}% of {len(new)})")
