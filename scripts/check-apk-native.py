#!/usr/bin/env python3
"""Verify the actual ARM64 APK's ELF LOAD/RELRO boundaries and mmap-friendly ZIP alignment.
Uses Python's standard library, creates no extracted build tree or extra SDK dependency.
"""
import struct
import sys
import zipfile
from pathlib import Path

PAGE = 16384


def check_relro(headers, name):
    # Bionic rounds BOTH ends outward. A partial final page is safe only if it
    # contains no writable LOAD bytes outside RELRO. Current AndroidX path puts
    # its entire relocation LOAD here and its mutable .bss on a separate page.
    # https://android.googlesource.com/platform/bionic/+/refs/heads/main/linker/linker_phdr.cpp
    for kind, flags, offset, address, physical, filesize, memsize, alignment in headers:
        if kind != 0x6474e552 or not memsize:
            continue
        end = address + memsize
        rounded_start = address // PAGE * PAGE
        rounded_end = (end + PAGE - 1) // PAGE * PAGE
        for load in headers:
            if load[0] != 1 or not load[1] & 2:  # PT_LOAD, PF_W
                continue
            start, stop = load[3], load[3] + load[6]
            for a, b in ((rounded_start, address), (end, rounded_end)):
                assert max(start, a) >= min(stop, b), f'RELRO would protect mutable bytes: {name}'


def check(apk):
    names = []
    with zipfile.ZipFile(apk) as archive, open(apk, 'rb') as source:
        for info in archive.infolist():
            if not info.filename.startswith('lib/') or not info.filename.endswith('.so'):
                continue
            assert info.filename.startswith('lib/arm64-v8a/'), f'unexpected shipped ABI: {info.filename}'
            data = archive.read(info)
            assert data[:6] == b'\x7fELF\x02\x01', f'not a little-endian ELF64: {info.filename}'
            phoff = struct.unpack_from('<Q',data,32)[0]
            stride,count = struct.unpack_from('<HH',data,54)
            assert stride >= 56 and count > 0 and phoff+stride*count <= len(data), f'invalid ELF headers: {info.filename}'
            headers = [struct.unpack_from('<IIQQQQQQ', data, phoff + index * stride) for index in range(count)]
            loads = 0
            for kind,flags,offset,address,physical,filesize,memsize,alignment in headers:
                if kind == 1:  # PT_LOAD
                    loads += 1
                    assert alignment >= PAGE and alignment % PAGE == 0, f'4 KB LOAD alignment: {info.filename}'
                    assert (address-offset) % PAGE == 0, f'incompatible LOAD offset: {info.filename}'
            check_relro(headers, info.filename)
            assert loads, f'no loadable segments: {info.filename}'
            assert info.compress_type == zipfile.ZIP_STORED, f'native library would need extraction: {info.filename}'
            source.seek(info.header_offset)
            header=source.read(30)
            filename_len,extra_len=struct.unpack_from('<HH',header,26)
            start=info.header_offset+30+filename_len+extra_len
            assert start % PAGE == 0, f'native ZIP payload not 16 KB aligned: {info.filename}'
            names.append(info.filename)
            print('PASS 16 KB LOAD alignment, safe RELRO and ZIP alignment:',info.filename)
    assert {'lib/arm64-v8a/libobadh_jni.so','lib/arm64-v8a/libjni_latinime.so'} <= set(names), 'required engine/English JNI library missing'
    print(f'Native APK checks passed: {len(names)} libraries, {Path(apk).stat().st_size:,} bytes.')


if __name__=='__main__':
    if len(sys.argv)!=2:
        raise SystemExit('Usage: check-apk-native.py app-release.apk')
    try:
        check(sys.argv[1])
    except (AssertionError,OSError,ValueError,struct.error,zipfile.BadZipFile) as error:
        raise SystemExit(f'FAIL {error}')
