"""Open the original game without stacking a second SDL activity."""
import re

GAME_PACKAGE = "com.runesoft.cultures"
GAME_COMPONENT = GAME_PACKAGE + "/com.rune_soft.culturesnorthland.MainActivity"


def open_game_args():
    # MAIN/LAUNCHER matches normal launches. REORDER and SINGLE_TOP also reuse
    # an existing task whose root intent came from an older component-only probe.
    return ("am", "start", "--display", "0", "-a", "android.intent.action.MAIN",
            "-c", "android.intent.category.LAUNCHER", "--activity-reorder-to-front",
            "--activity-single-top", "-n", GAME_COMPONENT)


def game_identity(shell):
    """Record process and ActivityRecord identities, not just the task number."""
    processes = shell("ps", "-A", "-o", "PID,NAME")
    pids = [fields[0] for line in processes.splitlines()
            if len(fields := line.split()) == 2 and fields[1] == GAME_PACKAGE]
    dump = shell("dumpsys", "activity", "activities")
    top = re.search(r"^Display #0 .*?(?=^Display #\d|\Z)", dump, re.M | re.S)
    activities = re.findall(r"\* Hist\s+#\d+: ActivityRecord\{([a-f0-9]+) u0 "
                            + re.escape(GAME_COMPONENT) + r"\} t(\d+)\}", top[0] if top else "")
    if len(pids) != 1 or len(activities) != 1:
        raise RuntimeError("Expected one live Northland activity on display 0; "
                           f"found PIDs {pids}, activities {activities}")
    return {"pid": pids[0], "activity": activities[0][0], "task": int(activities[0][1])}
