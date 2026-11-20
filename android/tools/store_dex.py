#!/usr/bin/env python3
"""store_dex.py IN.apk OUT.apk -- re-zip with DEX entries stored (uncompressed) and the old signature removed.
Stored DEX makes consecutive builds binary-diffable (a few hundred KB instead of the whole APK). Re-sign afterwards."""
import sys, zipfile
a = zipfile.ZipFile(sys.argv[1])
with zipfile.ZipFile(sys.argv[2], "w") as o:
    for i in a.infolist():
        n = i.filename
        if n.startswith("META-INF/") and n.upper().endswith((".SF", ".RSA", ".DSA", ".EC", ".MF")): continue
        z = zipfile.ZipInfo(n, (1981, 1, 1, 0, 0, 0))
        z.compress_type = zipfile.ZIP_STORED if n.endswith(".dex") else zipfile.ZIP_DEFLATED
        z.external_attr = i.external_attr
        o.writestr(z, a.read(n), compresslevel=9)
