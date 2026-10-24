#!/usr/bin/env python3
"""Dependency-free Android SDK build. Produces a locally test-signed APK.

Set JAVA_HOME (JDK 17) and ANDROID_HOME (SDK with platform/build-tools 34).
The signing key lives in .local/, excluded from source control. Keep it for updates.
"""
import os
from pathlib import Path
import subprocess
import zipfile
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
SDK = Path(os.environ['ANDROID_HOME'])
JAVA = Path(os.environ['JAVA_HOME']) / 'bin'
TOOLS = SDK / 'build-tools' / '34.0.0'
ANDROID = SDK / 'platforms' / 'android-34' / 'android.jar'
OUT = ROOT / 'app' / 'build' / 'manual'
SOURCE = ROOT / 'app' / 'src' / 'main'
ENV = dict(os.environ, JAVA_HOME=str(JAVA.parent), PATH=str(JAVA) + os.pathsep + os.environ['PATH'])

def run(*args):
    subprocess.run([str(x) for x in args], check=True, env=ENV, cwd=ROOT)

for folder in ['gen', 'classes', 'dex']:
    (OUT / folder).mkdir(parents=True, exist_ok=True)
# AGP reads namespace from build.gradle; direct aapt2 needs an explicit package.
ET.register_namespace('android', 'http://schemas.android.com/apk/res/android')
manifest = ET.parse(SOURCE / 'AndroidManifest.xml')
manifest.getroot().set('package', 'tv.skyhome')
manifest.write(OUT / 'AndroidManifest.xml', encoding='utf-8', xml_declaration=True)
run(TOOLS / 'aapt2', 'compile', '--dir', SOURCE / 'res', '-o', OUT / 'resources.zip')
run(TOOLS / 'aapt2', 'link', '-o', OUT / 'base.apk', '-I', ANDROID,
    '--manifest', OUT / 'AndroidManifest.xml', '--java', OUT / 'gen',
    '-A', SOURCE / 'assets', OUT / 'resources.zip')
sources = sorted((SOURCE / 'java').rglob('*.java')) + sorted((OUT / 'gen').rglob('*.java'))
run(JAVA / 'javac', '-encoding', 'UTF-8', '-source', '8', '-target', '8',
    '-bootclasspath', ANDROID, '-classpath', TOOLS / 'core-lambda-stubs.jar', '-d', OUT / 'classes', *sources)
with zipfile.ZipFile(OUT / 'classes.jar', 'w') as jar:
    for path in sorted((OUT / 'classes').rglob('*.class')):
        jar.write(path, path.relative_to(OUT / 'classes'))
run(TOOLS / 'd8', '--lib', ANDROID, '--min-api', '23', '--output', OUT / 'dex', OUT / 'classes.jar')
with zipfile.ZipFile(OUT / 'base.apk', 'a', compression=zipfile.ZIP_DEFLATED) as apk:
    for path in sorted((OUT / 'dex').glob('*.dex')):
        apk.write(path, path.name)
run(TOOLS / 'zipalign', '-f', '4', OUT / 'base.apk', OUT / 'aligned.apk')
private = ROOT / '.local'
private.mkdir(mode=0o700, exist_ok=True)
key = private / 'test-signing.jks'
if not key.exists():
    run(JAVA / 'keytool', '-genkeypair', '-keystore', key, '-storepass', 'android', '-keypass', 'android',
        '-alias', 'androiddebugkey', '-dname', 'CN=SkyHome Local Test', '-keyalg', 'RSA', '-keysize', '2048', '-validity', '10000')
    key.chmod(0o600)
version = manifest.getroot().get('{http://schemas.android.com/apk/res/android}versionName')
result = ROOT / ('skyhome-v' + version + '.apk')
run(TOOLS / 'apksigner', 'sign', '--ks', key, '--ks-pass', 'pass:android', '--ks-key-alias', 'androiddebugkey',
    '--out', result, OUT / 'aligned.apk')
run(TOOLS / 'apksigner', 'verify', '--verbose', result)
print(result)
