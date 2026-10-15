"""Boundaries of the private APK experiment; use synthetic payloads, not game files."""
import importlib.util
from pathlib import Path
import tempfile
from types import SimpleNamespace
import unittest
from unittest.mock import patch
import xml.etree.ElementTree as ET
import zipfile

spec = importlib.util.spec_from_file_location('apk_experiment', Path(__file__).resolve().parents[1] / 'scripts/apk-experiment.py')
experiment = importlib.util.module_from_spec(spec)
spec.loader.exec_module(experiment)


class ExperimentChecks(unittest.TestCase):
    def test_resource_free_splits_get_framework_and_package_configuration(self):
        for config in ('version: 2.12.1\n',
                       'packageInfo:\n  forcedPackageId: 127\n',
                       'packageInfo:\n  renameManifestPackage:\n  forcedPackageId: 127\n',
                       'usesFramework:\n  ids:\n  - 1\npackageInfo:\n  renameManifestPackage: old\n'):
            updated = experiment.configure_apktool(config)
            self.assertEqual(updated.count('renameManifestPackage:'), 1)
            self.assertEqual(updated.count('usesFramework:'), 1)
            self.assertIn('renameManifestPackage: ' + experiment.PACKAGE, updated)
            self.assertEqual(experiment.configure_apktool(updated), updated)
            if 'forcedPackageId: 127' in config:
                self.assertIn('forcedPackageId: 127', updated)

    def test_identity_change_preserves_license_provider_and_permission(self):
        with tempfile.TemporaryDirectory() as folder:
            p = Path(folder) / 'AndroidManifest.xml'
            p.write_text('''<manifest xmlns:android="http://schemas.android.com/apk/res/android" package="com.runesoft.cultures">
              <permission android:name="com.runesoft.cultures.PRIVATE"/>
              <uses-permission android:name="com.android.vending.CHECK_LICENSE"/>
              <application android:label="@string/app_name">
                <provider android:name="com.pairip.licensecheck.LicenseContentProvider"
                  android:authorities="com.runesoft.cultures.com.pairip.licensecheck.LicenseContentProvider" android:exported="false"/>
                <meta-data android:name="untouched" android:value="com.runesoft.cultures"/>
              </application></manifest>''', encoding='utf-8')
            experiment.rename_manifest(p, True)
            root = ET.parse(p).getroot()
            a = experiment.ANDROID
            self.assertEqual(root.get('package'), experiment.PACKAGE)
            self.assertEqual(root.find('uses-permission').get(a + 'name'), 'com.android.vending.CHECK_LICENSE')
            provider = root.find('application/provider')
            self.assertEqual(provider.get(a + 'name'), 'com.pairip.licensecheck.LicenseContentProvider')
            self.assertEqual(provider.get(a + 'exported'), 'false')
            self.assertTrue(provider.get(a + 'authorities').startswith(experiment.PACKAGE + '.'))
            self.assertEqual(root.find('application/meta-data').get(a + 'value'), experiment.ORIGINAL)
            self.assertEqual(root.find('queries/package').get(a + 'name'), 'com.android.vending')

    def test_code_asset_and_library_changes_fail_verification(self):
        with tempfile.TemporaryDirectory() as folder:
            before, after = (Path(folder) / n for n in ('before.zip', 'after.zip'))
            payloads = {'classes.dex': b'license code', 'assets/level': b'level', 'lib/arm64-v8a/game.so': b'engine'}
            def write(path, files):
                with zipfile.ZipFile(path, 'w') as z:
                    for name, data in files.items(): z.writestr(name, data)
            write(before, payloads)
            write(after, payloads)
            self.assertEqual(experiment.preserved_payload(before, after), 3)
            for name in payloads:
                write(after, {**payloads, name: b'changed'})
                with self.assertRaises(ValueError): experiment.preserved_payload(before, after)

    def test_unrecognized_source_cannot_start_build_tools(self):
        with tempfile.TemporaryDirectory() as folder, patch.object(experiment, 'run') as run:
            with self.assertRaises(ValueError): experiment.inspect(Path(folder))
            run.assert_not_called()

    def test_update_rejects_a_different_installed_test_copy_before_install(self):
        import json
        with tempfile.TemporaryDirectory() as folder:
            root = Path(folder)
            previous = root / 'previous'
            previous.mkdir()
            outputs = []
            for name in experiment.INPUTS:
                (root / name).write_bytes(name.encode())
                outputs.append({'name': name, 'sha256': experiment.digest(root / name), 'certificate': 'test-cert'})
            report = {'package': experiment.PACKAGE, 'phase': 'baseline',
                      'license_code_unchanged': True, 'outputs': outputs}
            for where in (root, previous):
                (where / 'build.json').write_text(json.dumps(report), encoding='utf-8')
            def shell_result(args, *unused, **kwargs):
                if args[-3:] == ['pm', 'path', experiment.ORIGINAL]:
                    return '\n'.join('package:/owned/' + name for name in experiment.INPUTS)
                if args[-2] == 'sha256sum':
                    name = Path(args[-1]).name
                    return (experiment.INPUTS[name] if '/owned/' in args[-1] else 'unexpected') + '  file\n'
                if 'list' in args: return 'package:' + experiment.PACKAGE + '\n'
                if args[-3:] == ['pm', 'path', experiment.PACKAGE]: return 'package:/test/base.apk\n'
                raise AssertionError('Unexpected command: ' + repr(args))
            with patch.object(experiment, 'run', side_effect=shell_result) as run:
                args = SimpleNamespace(build_folder=root, adb='adb', serial='thor', previous_build=previous)
                with self.assertRaisesRegex(ValueError, 'Installed test copy differs'):
                    experiment.install_test(args)
                self.assertFalse(any('install-multiple' in call.args[0] for call in run.call_args_list))


if __name__ == '__main__': unittest.main()
