"""Shared, shell-free operations for the desktop setup window and its checks."""
from dataclasses import dataclass
from pathlib import Path
import os
import json
import shutil
import subprocess
import sys

ROOT=Path(__file__).resolve().parents[1]


@dataclass(frozen=True)
class Device:
    serial: str
    state: str
    description: str


def default_adb():
    found=shutil.which("adb")
    if found:return found
    executable="adb.exe" if os.name=="nt" else "adb"
    sdk=os.environ.get("ANDROID_HOME") or os.environ.get("ANDROID_SDK_ROOT")
    candidates=([Path(sdk)/"platform-tools"/executable] if sdk else [])
    candidates += [ROOT/"platform-tools"/executable,Path.home()/"AppData/Local/Android/Sdk/platform-tools"/executable,
                   Path.home()/"Library/Android/sdk/platform-tools"/executable,Path.home()/"Android/Sdk/platform-tools"/executable]
    return str(next((path for path in candidates if path.is_file()),""))


def parse_devices(output):
    devices=[]
    for line in output.splitlines():
        fields=line.split()
        if len(fields)<2 or fields[0]=='List' or line.startswith('*'):continue
        devices.append(Device(fields[0],fields[1],' '.join(fields[2:])))
    return devices


def helper_running(processes,state_text):
    records=[line.split(None,2) for line in processes.splitlines()]
    owned={p[0]:p[2] for p in records if len(p)==3 and p[1]=='2000'}
    if any(command.startswith('app_process64 /system/bin org.northland.thor.InputBridge controller')
           for command in owned.values()):return True
    try:
        pid=str(json.loads(state_text)['pid'])
        return owned.get(pid)=='org.northland.thor:activation'
    except (ValueError,KeyError,TypeError):return False


def helper_active(adb):
    processes=subprocess.run(adb+['shell','ps','-A','-o','PID,UID,ARGS'],capture_output=True,text=True,check=True).stdout
    state=subprocess.run(adb+['shell','cat','/data/local/tmp/northland-thor/state.json'],capture_output=True,text=True).stdout
    return helper_running(processes,state)


def run_process(args,emit,timeout=180):
    # No shell, including for file paths containing spaces or shell metacharacters.
    result=subprocess.run(args,text=True,capture_output=True,timeout=timeout,
                          creationflags=subprocess.CREATE_NO_WINDOW if os.name=='nt' else 0)
    if result.stdout:emit(result.stdout.rstrip())
    if result.stderr:emit(result.stderr.rstrip())
    if result.returncode:
        raise RuntimeError("Command failed. Check the details above.")
    return result.stdout


def find_devices(adb,emit=lambda text:None):
    if not adb:raise ValueError("Choose adb from Android Platform Tools first.")
    return parse_devices(run_process([adb,"devices","-l"],emit,timeout=30))


def perform(adb,device,artifacts,action,emit):
    if device.state=='unauthorized':raise ValueError("Unlock the Thor and allow USB debugging, then refresh devices.")
    if device.state!='device':raise ValueError("The Thor is offline. Reconnect it and refresh devices.")
    prefix=[sys.executable,str(ROOT/"scripts/android-controller.py"),"--adb",adb,"--serial",device.serial]
    if action=='setup':
        folder=Path(artifacts).expanduser().resolve()
        for filename in ('northland-panel.apk','northland-bridge.jar'):
            if not (folder/filename).is_file():raise ValueError("Build folder is missing "+filename)
        commands=[prefix+["--artifacts",str(folder),"setup"]]
    elif action=='activate':commands=[prefix+["panel"],prefix+["start","--background"]]
    elif action in ('stop','game'):commands=[prefix+[action]]
    else:raise ValueError("Unknown setup action")
    for command in commands:run_process(command,emit)
