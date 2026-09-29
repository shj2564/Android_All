#!/usr/bin/env python3
"""Reproducible Linux/JDK17 build using pinned tools; no Android device is contacted."""
from pathlib import Path
import argparse
import hashlib
import json
import shutil
import subprocess
import urllib.request
import zipfile
import xml.etree.ElementTree as ET
from build.verify_package import verify_package

project = Path(__file__).resolve().parent
parser = argparse.ArgumentParser()
parser.add_argument('--toolchain', type=Path, default=project / 'tools')
args = parser.parse_args()
tools = args.toolchain.resolve()
tools.mkdir(parents=True, exist_ok=True)
spec = json.loads((project / 'build/toolchain.json').read_text())
for name, item in spec.items():
    file = tools / name
    if not file.exists():
        with urllib.request.urlopen(item['url'], timeout=60) as response:
            file.write_bytes(response.read())
    if hashlib.sha256(file.read_bytes()).hexdigest() != item['sha256']:
        raise RuntimeError('Tool hash mismatch: ' + name)
with zipfile.ZipFile(tools / 'aapt2.jar') as archive:
    (tools / 'aapt2').write_bytes(archive.read('aapt2'))
(tools / 'aapt2').chmod(0o755)
java = shutil.which('java')
keytool = shutil.which('keytool')
if not java or not keytool:
    raise RuntimeError('JDK/JRE 17 with java and keytool is required')
jdk = Path(java).resolve().parent.parent
out = project / 'out'
for folder in ['classes', 'test-classes', 'dex', 'signer']:
    path = out / folder
    if path.exists():
        shutil.rmtree(path)
    path.mkdir(parents=True)

def run(command):
    result = subprocess.run(list(map(str, command)), check=True, text=True,
                            stdout=subprocess.PIPE, stderr=subprocess.STDOUT)
    if result.stdout:
        print(result.stdout, end='')
    return result.stdout

compiler = [java, '-jar', tools/'ecj.jar', '-encoding', 'UTF-8', '-source', '8', '-target', '8']
run(compiler + ['-cp', tools/'android.jar', '-d', out/'classes'] + sorted((project/'src').rglob('*.java')))
run(compiler + ['-cp', out/'classes', '-d', out/'test-classes'] + sorted((project/'tests').glob('*.java')))
test_log = run([java, '-cp', str(out/'classes') + ':' + str(out/'test-classes'), 'ProtocolTest'])
(out/'protocol-test.txt').write_text(test_log)
trial_log = run([java, '-cp', str(out/'classes') + ':' + str(out/'test-classes'), 'TrialPolicyTest'])
(out/'trial-policy-test.txt').write_text(trial_log)
run([tools/'aapt2', 'link', '-o', out/'base.apk', '--manifest', project/'AndroidManifest.xml', '-I', tools/'android.jar'])
with zipfile.ZipFile(out/'app-classes.jar', 'w', zipfile.ZIP_DEFLATED) as archive:
    for file in sorted((out/'classes').rglob('*.class')):
        archive.write(file, file.relative_to(out/'classes').as_posix())
run([java, '-cp', tools/'r8.jar', 'com.android.tools.r8.D8', '--release', '--min-api', '21',
     '--lib', tools/'android.jar', '--lib', jdk, '--output', out/'dex', out/'app-classes.jar'])
# Preserve AAPT2's resource compression and local-entry alignment. Repacking
# every entry with ZIP_DEFLATED makes targetSdk >= 30 APKs uninstallable.
shutil.copyfile(out/'base.apk', out/'unsigned.apk')
with zipfile.ZipFile(out/'unsigned.apk', 'a', zipfile.ZIP_DEFLATED) as archive:
    archive.write(out/'dex/classes.dex', 'classes.dex')
verify_package(out/'unsigned.apk')
run(compiler + ['-cp', tools/'apksig.jar', '-d', out/'signer', project/'build/SignAndVerify.java'])
keystore = project/'demo-signing.p12'
if not keystore.exists():
    run([keytool, '-genkeypair', '-alias', 'mirror-demo', '-keyalg', 'RSA', '-keysize', '2048', '-validity', '3650',
         '-keystore', keystore, '-storetype', 'PKCS12', '-storepass', 'mirror-demo-only', '-keypass', 'mirror-demo-only',
         '-dname', 'CN=SONG MirrorCheck Demo, OU=Local demo, O=Independent, C=KR', '-noprompt'])
version_code = int(ET.parse(project/'AndroidManifest.xml').getroot().get(
    '{http://schemas.android.com/apk/res/android}versionCode'))
apk = project.parent/('CMZX-MirrorCheck-v%02d.apk' % version_code)
sign_log = run([java, '-cp', str(out/'signer') + ':' + str(tools/'apksig.jar'), 'SignAndVerify',
               keystore, out/'unsigned.apk', apk])
(out/'signature-verification.txt').write_text(sign_log)
packaging = verify_package(apk)
(out/'packaging-verification.json').write_text(json.dumps(packaging, indent=2))
receipt = {'file': apk.name, 'size': apk.stat().st_size, 'sha256': hashlib.sha256(apk.read_bytes()).hexdigest(),
           'protocol_tests': test_log.strip(), 'trial_policy_tests': trial_log.strip(), 'signature_check': sign_log.strip(),
           'packaging_check': packaging,
           'device_install_or_execution_tested': False}
(out/'build-result.json').write_text(json.dumps(receipt, indent=2))
print(json.dumps(receipt, indent=2))
