"""Private, license-preserving feasibility test using the owner's Northland APKs.

Inspect/install work from Windows; build uses Java 17 and Android SDK 36 on
Linux, macOS or WSL. Never replaces the purchased game or bypasses licensing.
"""
import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import shutil
import subprocess
import tempfile
import time
import urllib.request
import xml.etree.ElementTree as ET
import zipfile

ROOT = Path(__file__).resolve().parents[1]
ORIGINAL = 'com.runesoft.cultures'
PACKAGE = 'org.northland.thor.experimental'
ACTIVITY = 'com.rune_soft.culturesnorthland.MainActivity'
ANDROID = '{http://schemas.android.com/apk/res/android}'
APKTOOL_URL = 'https://github.com/iBotPeaches/Apktool/releases/download/v2.12.1/apktool_2.12.1.jar'
APKTOOL_SHA = '66cf4524a4a45a7f56567d08b2c9b6ec237bcdd78cee69fd4a59c8a0243aeafa'
INPUTS = {
    'base.apk': '15743d8ec38723a0fab64bb846b6d69a98a868cb1477bf0c1a90e79aef483378',
    'split_config.arm64_v8a.apk': '40cd539d5e69e678796b982f685a17dc2d2b12fdd107e350692d0e5c030c7e8e',
    'split_config.en.apk': 'be9395618078367bc8e542b25f561d9503ca188781d2a847e5b11dbb1fd0a3c1',
    'split_config.es.apk': '7c396b08b5eaac83c8e4d53e0e1d5bb697a4f8d7563f8b81183592f2bf10bdeb',
    'split_config.xxhdpi.apk': 'decd6213294b3935d36382ad4bd9bf27dfadd74de926b8d1fe40a1a51e52981d',
    'split_NorthlandAssetPack.apk': '7a5511d9c9e1469e425080891d5f93d99a4f8db4d583cd0088527815d27f9a44',
}


def stream_digest(stream):
    result = hashlib.sha256()
    for block in iter(lambda: stream.read(1024 * 1024), b''):
        result.update(block)
    return result.hexdigest()


def digest(path):
    with Path(path).open('rb') as f:
        return stream_digest(f)


def inspect(source):
    records = []
    for name, expected in INPUTS.items():
        path = source / name
        if not path.is_file() or digest(path) != expected:
            raise ValueError('Missing or unsupported owned APK: ' + name)
        with zipfile.ZipFile(path) as z:
            records.append({'name': name, 'sha256': expected, 'bytes': path.stat().st_size,
                            'dex': [n for n in z.namelist() if re.fullmatch(r'classes\d*\.dex', n)],
                            'libraries': [n for n in z.namelist() if n.startswith('lib/')],
                            'assets': sum(n.startswith('assets/') for n in z.namelist())})
    return records


def run(args, log=None, timeout=300):
    result = subprocess.run([str(a) for a in args], capture_output=True, text=True,
                            encoding='utf-8', errors='replace', timeout=timeout)
    if log:
        with log.open('a', encoding='utf-8') as f:
            f.write('\n$ ' + ' '.join(str(a) for a in args) + '\n' + result.stdout + result.stderr)
    if result.returncode:
        raise RuntimeError('Command failed: ' + str(args[0]) + '\n' + (result.stdout + result.stderr)[-4000:])
    return result.stdout


def apktool():
    path = ROOT / 'artifacts/apk-tools/apktool_2.12.1.jar'
    if not path.is_file() or digest(path) != APKTOOL_SHA:
        path.parent.mkdir(parents=True, exist_ok=True)
        request = urllib.request.Request(APKTOOL_URL, headers={'User-Agent': 'Northland-Thor-local-build'})
        with urllib.request.urlopen(request, timeout=60) as response:
            data = response.read()
        if hashlib.sha256(data).hexdigest() != APKTOOL_SHA:
            raise ValueError('Apktool checksum mismatch')
        temporary = path.with_suffix('.download')
        temporary.write_bytes(data)
        temporary.replace(path)
    return path


def rename_manifest(path, base):
    ET.register_namespace('android', ANDROID[1:-1])
    tree = ET.parse(path)
    root = tree.getroot()
    if root.get('package') != ORIGINAL:
        raise ValueError('Unexpected manifest package')
    root.set('package', PACKAGE)
    for node in root.iter():
        # Namespace the app's own permissions and providers; leave licensing
        # classes, metadata, package constants and response validation intact.
        for key in (ANDROID + 'authorities',):
            if key in node.attrib:
                node.set(key, ';'.join(PACKAGE + a[len(ORIGINAL):] if a.startswith(ORIGINAL + '.') else a
                                       for a in node.get(key).split(';')))
        if node.tag in ('permission', 'uses-permission'):
            name = node.get(ANDROID + 'name', '')
            if name.startswith(ORIGINAL + '.'):
                node.set(ANDROID + 'name', PACKAGE + name[len(ORIGINAL):])
    if base:
        root.find('application').set(ANDROID + 'label', 'Northland Thor Test')
        # A sideloaded package does not inherit Play's installer visibility.
        # Declare the normal bindService dependency; do not spoof its installer.
        queries = root.find('queries')
        if queries is None:
            queries = ET.SubElement(root, 'queries')
        if not any(p.get(ANDROID + 'name') == 'com.android.vending' for p in queries.findall('package')):
            ET.SubElement(queries, 'package', {ANDROID + 'name': 'com.android.vending'})
    tree.write(path, encoding='utf-8', xml_declaration=True)


def preserved_payload(original, rebuilt):
    with zipfile.ZipFile(original) as before, zipfile.ZipFile(rebuilt) as after:
        names = [n for n in before.namelist() if n.startswith(('assets/', 'lib/'))
                 or re.fullmatch(r'classes\d*\.dex', n)]
        for name in names:
            # Full byte hashes, not ZIP CRCs. No asset or licensing-code edits.
            with before.open(name) as a, after.open(name) as b:
                if stream_digest(a) != stream_digest(b):
                    raise ValueError('Protected payload changed: ' + name)
        return len(names)


def configure_apktool(config):
    config, count = re.subn(r'(?m)^([ \t]*renameManifestPackage:)[ \t]*[^\r\n]*$', r'\1 ' + PACKAGE, config)
    if count == 0:
        if re.search(r'(?m)^packageInfo:\s*$', config):
            config = re.sub(r'(?m)^packageInfo:\s*$', 'packageInfo:\n  renameManifestPackage: ' + PACKAGE, config, count=1)
        else:
            config += '\npackageInfo:\n  renameManifestPackage: ' + PACKAGE + '\n'
    elif count != 1:
        raise ValueError('Unsupported Apktool package configuration')
    # Resource-free splits omit this even though their decoded manifest still
    # needs Android's framework attribute definitions when it is recompiled.
    if not re.search(r'(?m)^usesFramework:', config):
        config += '\nusesFramework:\n  ids:\n  - 1\n'
    return config


def build(args):
    inputs = inspect(args.source)
    sdk = args.sdk.expanduser().resolve()
    tools = sdk / 'build-tools/36.0.0'
    for name in ('aapt2', 'zipalign', 'apksigner'):
        if not (tools / name).is_file():
            raise ValueError('Missing Android SDK tool: ' + name)
    jar = apktool()
    job = args.out.resolve() / time.strftime('baseline-%Y%m%d-%H%M%S')
    job.mkdir(parents=True, exist_ok=False)
    log = job / 'build.log'
    # Thousands of decoded resources are very slow on a Windows-mounted WSL
    # directory. Compile in the native temporary filesystem, publish APKs only.
    work = Path(tempfile.mkdtemp(prefix='northland-apk-'))
    log.write_text('Private temporary work directory: ' + str(work) + '\n', encoding='utf-8')
    source = work / 'inputs'
    source.mkdir()
    for name, expected in INPUTS.items():
        shutil.copyfile(args.source / name, source / name)
        if digest(source / name) != expected:
            raise ValueError('Source changed while copying: ' + name)
    key = ROOT / '.local/signing/apk-experiment.keystore'
    if not key.is_file():
        key.parent.mkdir(parents=True, exist_ok=True)
        run(['keytool', '-genkeypair', '-keystore', key, '-storepass', 'android', '-keypass', 'android',
             '-alias', 'experiment', '-dname', 'CN=Northland Thor Private Experiment',
             '-keyalg', 'RSA', '-keysize', '2048', '-validity', '10000', '-noprompt'], log)
    outputs = []
    for record in inputs:
        name = record['name']
        print('Rebuilding ' + name, flush=True)
        decoded = work / (name + '.decoded')
        # Keep DEX byte-for-byte, including the complete license provider.
        run(['java', '-jar', jar, 'd', '--no-src', '-p', work / 'framework',
             '-o', decoded, source / name], log)
        rename_manifest(decoded / 'AndroidManifest.xml', name == 'base.apk')
        # Apktool otherwise restores the original manifest package from YAML.
        yaml = decoded / 'apktool.yml'
        config = configure_apktool(yaml.read_text(encoding='utf-8'))
        yaml.write_text(config, encoding='utf-8')
        unsigned = work / (name + '.unsigned')
        # Apktool's bundled aapt2 accepts its decoded generated resource names;
        # the stock SDK compiler rejects names such as $avd_hide_password__0.
        run(['java', '-jar', jar, 'b', '-p', work / 'framework',
             '-o', unsigned, decoded], log)
        aligned = work / (name + '.aligned')
        run([tools / 'zipalign', '-P', '16', '-f', '4', unsigned, aligned], log)
        signed = work / name
        run([tools / 'apksigner', 'sign', '--ks', key, '--ks-pass', 'pass:android',
             '--out', signed, aligned], log)
        certificate = run([tools / 'apksigner', 'verify', '--print-certs', signed], log)
        dump = run([tools / 'aapt2', 'dump', 'xmltree', signed, '--file', 'AndroidManifest.xml'], log)
        if 'package="' + PACKAGE + '"' not in dump:
            raise ValueError('Rebuilt APK has wrong package')
        count = preserved_payload(source / name, signed)
        final = job / name
        shutil.copyfile(signed, final)
        if digest(final) != digest(signed):
            raise ValueError('Publishing APK changed its bytes: ' + name)
        outputs.append({'name': name, 'sha256': digest(final), 'preserved_entries': count,
                        'certificate': re.search(r'Signer #1 certificate SHA-256 digest: (\w+)', certificate)[1]})
    if len({o['certificate'] for o in outputs}) != 1:
        raise ValueError('Split signing certificates differ')
    report = {'package': PACKAGE, 'phase': 'baseline', 'inputs': inputs, 'outputs': outputs,
              'license_code_unchanged': True, 'controller_adapter': False}
    (job / 'build.json').write_text(json.dumps(report, indent=2) + '\n', encoding='utf-8')
    shutil.rmtree(work)
    print('Built private baseline: ' + str(job))


def install_test(args):
    folder = args.build_folder.resolve()
    report = json.loads((folder / 'build.json').read_text(encoding='utf-8'))
    if report.get('package') != PACKAGE or report.get('phase') != 'baseline' or not report.get('license_code_unchanged'):
        raise ValueError('Only the reviewed baseline experiment can be installed')
    outputs = report['outputs']
    if len(outputs) != len(INPUTS) or {o['name'] for o in outputs} != set(INPUTS):
        raise ValueError('Incomplete split set')
    for record in outputs:
        if digest(folder / record['name']) != record['sha256']:
            raise ValueError('Build changed: ' + record['name'])
    adb = [args.adb, '-s', args.serial]
    log = folder / 'device.log'
    def shell(*parts): return run(adb + ['shell', *parts], log, timeout=40)
    def official():
        paths = shell('pm', 'path', ORIGINAL).splitlines()
        if len(paths) != len(INPUTS): raise ValueError('Expected the official six-APK installation')
        hashes = [shell('sha256sum', p.removeprefix('package:')).split()[0] for p in paths]
        if set(hashes) != set(INPUTS.values()): raise ValueError('Official installation differs from reviewed build')
        return {'paths': paths, 'sha256': hashes}
    before = official()
    installed = 'package:' + PACKAGE in shell('pm', 'list', 'packages', PACKAGE).splitlines()
    if installed:
        if args.previous_build is None:
            raise ValueError('Test copy already installed. Use --previous-build for a verified test-only update.')
        previous = json.loads((args.previous_build / 'build.json').read_text(encoding='utf-8'))
        if previous.get('package') != PACKAGE or previous.get('phase') != 'baseline':
            raise ValueError('Unsupported previous test build')
        old_hashes = {shell('sha256sum', p.removeprefix('package:')).split()[0]
                      for p in shell('pm', 'path', PACKAGE).splitlines()}
        if old_hashes != {o['sha256'] for o in previous['outputs']}:
            raise ValueError('Installed test copy differs from --previous-build; nothing replaced')
        if {o['certificate'] for o in previous['outputs']} != {o['certificate'] for o in outputs}:
            raise ValueError('Test update signing keys differ')
    elif args.previous_build:
        raise ValueError('Previous test build is not installed')
    epoch = shell('date', '+%s').strip() + '.000'
    run(adb + ['install-multiple'] + (['-r'] if installed else []) + [folder / o['name'] for o in outputs], log)
    try:
        launch = shell('am', 'start', '--display', '0', '-W', '-a', 'android.intent.action.MAIN',
                       '-c', 'android.intent.category.LAUNCHER', '-n', PACKAGE + '/' + ACTIVITY)
        print('Test copy launched; observing licensing and startup for 25 seconds.', flush=True)
        time.sleep(25)
        processes = shell('pidof', PACKAGE).strip()
    except RuntimeError as e:
        launch = str(e)
        processes = ''
    logs = shell('logcat', '-d', '-v', 'epoch', '-T', epoch)
    (folder / 'startup-logcat.txt').write_text(logs, encoding='utf-8')
    activity = shell('dumpsys', 'activity', 'activities')
    (folder / 'startup-activities.txt').write_text(activity, encoding='utf-8')
    after = official()
    pids = set(processes.split())
    pids.update(re.findall(r'Start proc (\d+):' + re.escape(PACKAGE) + r'/', logs))
    license_lines = [line for line in logs.splitlines()
                     if re.match(r'\s*\d+\.\d+\s+(?:' + '|'.join(sorted(pids)) + r')\s+', line)
                     and 'LicenseClient' in line] if pids else []
    result = {'package': PACKAGE, 'launch': launch, 'pid': processes,
              'official_installation_unchanged': before == after,
              'license_success_observed': any('License check succeeded.' in line for line in license_lines),
              'license_log': license_lines,
              'manual_menu_and_tutorial_check_required': True}
    (folder / 'device-result.json').write_text(json.dumps(result, indent=2) + '\n', encoding='utf-8')
    print(json.dumps(result, indent=2))
    if before != after: raise RuntimeError('Official installation changed during test')


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    actions = parser.add_subparsers(dest='action', required=True)
    for name in ('inspect', 'build'):
        p = actions.add_parser(name)
        p.add_argument('--source', type=Path, default=ROOT / '.local/android-installed')
        if name == 'build':
            p.add_argument('--sdk', type=Path, default=Path(os.environ.get('ANDROID_HOME', str(Path.home() / 'Android/Sdk'))))
            p.add_argument('--out', type=Path, default=ROOT / 'artifacts/apk-experiment')
    p = actions.add_parser('install-test')
    p.add_argument('--build-folder', type=Path, required=True)
    p.add_argument('--previous-build', type=Path, help='Allow updating only this matching experimental installation')
    p.add_argument('--adb', default='adb')
    p.add_argument('--serial', required=True)
    args = parser.parse_args()
    if args.action == 'inspect': print(json.dumps(inspect(args.source), indent=2))
    elif args.action == 'build': build(args)
    else: install_test(args)


if __name__ == '__main__':
    main()
