#!/usr/bin/env python3
"""Build a temporary Titanium fixture and run lifecycle tests on an Android 10+ device."""
import argparse
from pathlib import Path
import subprocess
import tempfile
import zipfile

parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('--android-sdk', type=Path, default=Path.home() / 'Library/Android/sdk')
parser.add_argument('--sdk', default='13.4.1.GA')
parser.add_argument('--module-zip', type=Path)
args = parser.parse_args()
repo = Path(__file__).resolve().parents[2]
version = next(line.split(':', 1)[1].strip() for line in (repo / 'android/manifest').read_text().splitlines()
               if line.startswith('version:'))
module_zip = args.module_zip or repo / f'android/dist/ti.paint-android-{version}.zip'
work = Path(tempfile.mkdtemp(prefix='ti-paint-release-'))
print(f'Test artifacts: {work}', flush=True)


def run(*command, log=None, **kwargs):
    if log:
        with (work / log).open('w') as output:
            subprocess.run([str(x) for x in command], stdout=output, stderr=subprocess.STDOUT, check=True, **kwargs)
    else:
        return subprocess.run([str(x) for x in command], check=True, **kwargs)


run('ti', 'create', '-t', 'app', '-p', 'android', '--id', 'ti.paint.releasefixture',
    '-n', 'PaintReleaseFixture', '--workspace-dir', work, '--no-prompt', log='create.log')
app = work / 'PaintReleaseFixture'
config = app / 'tiapp.xml'
config.write_text(config.read_text().replace('<modules>', '<modules>\n<module platform="android">ti.paint</module>'))
with zipfile.ZipFile(module_zip) as archive:
    archive.extractall(app)
(app / 'Resources/app.js').write_text("""
require('ti.paint');
const win = Ti.UI.createWindow({backgroundColor: 'white'});
win.add(Ti.UI.createLabel({text: 'paint-test-ready'}));
win.open();
""")
run('ti', 'build', '-p', 'android', '-T', 'device', '--build-only', '--sdk', args.sdk,
    '--no-prompt', cwd=app, log='app-build.log')
apk = next((app / 'build/android').rglob('*-debug.apk'))
tools = args.android_sdk / 'build-tools/36.0.0'
android_jar = args.android_sdk / 'platforms/android-36/android.jar'
classes = work / 'classes'
classes.mkdir()
run('javac', '-source', '8', '-target', '8', '-classpath', android_jar, '-d', classes,
    repo / 'tests/android/ReleaseInstrumentation.java', log='javac.log')
class_files = list(classes.rglob('*.class'))
run(tools / 'd8', '--min-api', '29', '--lib', android_jar, '--output', work, *class_files)
manifest = work / 'AndroidManifest.xml'
manifest.write_text('''<manifest xmlns:android="http://schemas.android.com/apk/res/android" package="ti.paint.tests">
<uses-sdk android:minSdkVersion="29" android:targetSdkVersion="36"/>
<application android:label="Paint release tests"/>
<instrumentation android:name="ti.paint.tests.ReleaseInstrumentation" android:targetPackage="ti.paint.releasefixture"/>
</manifest>''')
test_apk = work / 'tests.apk'
run(tools / 'aapt2', 'link', '-I', android_jar, '--manifest', manifest, '-o', test_apk)
with zipfile.ZipFile(test_apk, 'a') as archive:
    archive.write(work / 'classes.dex', 'classes.dex')
key = work / 'test.keystore'
run('keytool', '-genkeypair', '-keystore', key, '-storepass', 'android', '-keypass', 'android',
    '-alias', 'test', '-dname', 'CN=Paint lifecycle test', '-keyalg', 'RSA', '-validity', '30', log='keytool.log')
for package in ['ti.paint.tests', 'ti.paint.releasefixture']:
    subprocess.run(['adb', 'uninstall', package], stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
for source, name in [(apk, 'app-signed.apk'), (test_apk, 'tests-signed.apk')]:
    signed = work / name
    run(tools / 'apksigner', 'sign', '--ks', key, '--ks-pass', 'pass:android',
        '--out', signed, source)
    run('adb', 'install', '--no-incremental', '-r', signed)
result = run('adb', 'shell', 'am', 'instrument', '-w', '-r', 'ti.paint.tests/ti.paint.tests.ReleaseInstrumentation',
    capture_output=True, text=True, timeout=90)
print(result.stdout)
(work / 'result.txt').write_text(result.stdout)
if 'PASS:' not in result.stdout or 'INSTRUMENTATION_CODE: -1' not in result.stdout:
    raise SystemExit(1)
