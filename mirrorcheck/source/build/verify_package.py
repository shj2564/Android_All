"""Check final APK packaging rules, independently of APK signature verification.

Android's targetSdk >= 30 rule requires resources.arsc to be uncompressed
and its file data (not its ZIP header) to start on a 4-byte boundary.
https://developer.android.com/about/versions/11/behavior-changes-11#app-packaging
"""
import json
from pathlib import Path
import struct
import sys
import zipfile


def verify_package(path):
    path = Path(path)
    issues = []
    entries = []
    with zipfile.ZipFile(path) as archive, path.open('rb') as raw:
        names = archive.namelist()
        if len(names) != len(set(names)):
            issues.append('Duplicate ZIP entry names')
        for required in ('AndroidManifest.xml', 'resources.arsc', 'classes.dex'):
            if required not in names:
                issues.append('Missing ' + required)
        bad_crc = archive.testzip()
        if bad_crc:
            issues.append('Invalid CRC: ' + bad_crc)
        for entry in archive.infolist():
            raw.seek(entry.header_offset)
            header = raw.read(30)
            if len(header) != 30 or header[:4] != b'PK\x03\x04':
                issues.append('Invalid local ZIP header: ' + entry.filename)
                continue
            local_method = struct.unpack_from('<H', header, 8)[0]
            name_length, extra_length = struct.unpack_from('<HH', header, 26)
            data_offset = entry.header_offset + 30 + name_length + extra_length
            stored = entry.compress_type == zipfile.ZIP_STORED
            aligned = data_offset % 4 == 0
            entries.append({'name': entry.filename, 'compression': entry.compress_type,
                            'data_offset': data_offset, 'aligned_4': aligned,
                            'size': entry.file_size})
            if local_method != entry.compress_type:
                issues.append('Compression differs between ZIP headers: ' + entry.filename)
            if entry.filename == 'resources.arsc':
                if not stored:
                    issues.append('resources.arsc is compressed (targetSdk >= 30 installation blocker)')
                if not aligned:
                    issues.append('resources.arsc data is not 4-byte aligned (targetSdk >= 30 installation blocker)')
            elif stored and not aligned:
                issues.append('Uncompressed entry is not 4-byte aligned: ' + entry.filename)
        if 'classes.dex' in names:
            dex = archive.read('classes.dex')
            if not dex.startswith(b'dex\n') or dex[7:8] != b'\x00':
                issues.append('Invalid DEX magic')
    result = {'file': path.name, 'passed': not issues,
              'issues': issues, 'entries': entries, 'device_install_test': False}
    if issues:
        raise ValueError(json.dumps(result, indent=2))
    return result


if __name__ == '__main__':
    try:
        print(json.dumps(verify_package(sys.argv[1]), indent=2))
    except ValueError as error:
        print(error)
        sys.exit(1)
