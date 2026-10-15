import sys
import unittest
from pathlib import Path
from unittest.mock import patch

sys.path.insert(0,str(Path(__file__).resolve().parents[1]/'scripts'))
from setup_backend import Device,helper_running,parse_devices,perform


class SetupChecks(unittest.TestCase):
    def test_authorization_and_multiple_devices_are_preserved(self):
        devices=parse_devices('List of devices attached\nA unauthorized usb:1-2\nB device model:Thor\nC offline\n')
        self.assertEqual([(d.serial,d.state) for d in devices],[('A','unauthorized'),('B','device'),('C','offline')])

    def test_unready_device_cannot_mutate_anything(self):
        with patch('setup_backend.run_process') as run:
            for state in ('offline','unauthorized'):
                with self.assertRaises(ValueError):perform('adb',Device('thor',state,''),'unused','setup',print)
            run.assert_not_called()

    def test_missing_artifacts_rejected_before_install(self):
        with patch('setup_backend.run_process') as run:
            with self.assertRaises(ValueError):perform('adb',Device('thor','device',''),'missing-folder','setup',print)
            run.assert_not_called()

    def test_shizuku_state_must_match_a_live_shell_service(self):
        ps='PID UID ARGS\n123 2000 org.northland.thor:activation\n'
        self.assertTrue(helper_running(ps,'{"pid":123}'))
        for state in ('{"pid":124}','{}','bad JSON','null'):
            self.assertFalse(helper_running(ps,state))
        self.assertFalse(helper_running(ps.replace('2000','10138'),'{"pid":123}'))
        self.assertFalse(helper_running(ps.replace('2000','0'),'{"pid":123}'))

    def test_usb_route_remains_visible_during_upgrade(self):
        ps='PID UID ARGS\n42 2000 app_process64 /system/bin org.northland.thor.InputBridge controller\n'
        self.assertTrue(helper_running(ps,''))
        self.assertFalse(helper_running(ps.replace('InputBridge','OtherBridge'),''))

    def test_paths_and_serial_are_separate_process_arguments(self):
        with patch('setup_backend.run_process') as run:
            perform('C:/A path/adb.exe',Device('serial:5555','device',''),'unused','activate',print)
            calls=[args.args[0] for args in run.call_args_list]
            self.assertEqual(calls[0][calls[0].index('--adb')+1],'C:/A path/adb.exe')
            self.assertEqual(calls[0][calls[0].index('--serial')+1],'serial:5555')
            self.assertEqual(calls[-1][-2:],['start','--background'])


if __name__=='__main__':unittest.main()
