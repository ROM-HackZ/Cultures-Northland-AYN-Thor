"""Activity dumps must establish a single surviving game, not just a task ID."""
import sys
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "scripts"))
from northland_session import GAME_COMPONENT, game_identity


class SessionIdentityChecks(unittest.TestCase):
    def read(self, records, processes="PID NAME\n123 com.runesoft.cultures\n"):
        def shell(*args):
            if args[0] == "ps":
                return processes
            return records
        return game_identity(shell)

    def record(self, token="abcdef", display=0):
        return (f"Display #{display} (activities from top to bottom):\n"
                f" * Hist #0: ActivityRecord{{{token} u0 {GAME_COMPONENT}}} t138}}\n")

    def test_record_is_not_counted_again_in_resumed_summary(self):
        record = self.record() + f"Resumed: ActivityRecord{{abcdef u0 {GAME_COMPONENT}}} t138}}\n"
        self.assertEqual(self.read(record), {"pid": "123", "activity": "abcdef", "task": 138})

    def test_other_display_is_excluded(self):
        self.assertEqual(self.read(self.record() + self.record("deadbeef", 4))["activity"], "abcdef")
        with self.assertRaises(RuntimeError):
            self.read(self.record(display=4))

    def test_duplicate_game_activity_is_rejected(self):
        duplicate = f" * Hist #1: ActivityRecord{{deadbeef u0 {GAME_COMPONENT}}} t138}}\n"
        with self.assertRaises(RuntimeError):
            self.read(self.record() + duplicate)

    def test_dead_or_ambiguous_process_is_rejected(self):
        for processes in ["PID NAME\n", "123 com.runesoft.cultures\n456 com.runesoft.cultures\n"]:
            with self.assertRaises(RuntimeError):
                self.read(self.record(), processes)

    def test_recreation_inside_same_process_and_task_is_detected(self):
        self.assertNotEqual(self.read(self.record()), self.read(self.record("deadbeef")))


if __name__ == "__main__":
    unittest.main()
