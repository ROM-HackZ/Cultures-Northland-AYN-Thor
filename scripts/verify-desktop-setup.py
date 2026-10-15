"""Drive the real Tk setup window against a connected Thor and capture its result."""
import argparse
import importlib.util
from pathlib import Path
import sys
import time
import tkinter as tk


def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--adb',required=True)
    parser.add_argument('--action',choices=('refresh','activate','setup'),default='refresh')
    parser.add_argument('--serial',required=True)
    parser.add_argument('--out',type=Path,default=Path('artifacts/desktop-setup'))
    parser.add_argument('--setup-script',type=Path,default=Path(__file__).with_name('setup-ui.py'),
                        help='Verify a setup window from an extracted release bundle')
    args=parser.parse_args();args.out.mkdir(parents=True,exist_ok=True)
    sys.path.insert(0,str(args.setup_script.resolve().parent))
    spec=importlib.util.spec_from_file_location('setup_ui',args.setup_script)
    module=importlib.util.module_from_spec(spec);spec.loader.exec_module(module)
    root=tk.Tk();app=module.SetupWindow(root,adb=args.adb)
    deadline=time.monotonic()+120;phase='find';failure=[]
    def finish(error=None):
        if error:failure.append(error)
        (args.out/'result.txt').write_text(app.status.get()+'\n'+app.log.get('1.0','end'),encoding='utf-8')
        if sys.platform=='win32':
            import ctypes
            from ctypes import wintypes
            from PIL import ImageGrab
            root.update()
            ancestor=ctypes.windll.user32.GetAncestor
            ancestor.argtypes=[wintypes.HWND,wintypes.UINT];ancestor.restype=wintypes.HWND
            window=ancestor(root.winfo_id(),2)
            ImageGrab.grab(window=window).save(args.out/'setup-window.png')
        root.destroy()
    def advance():
        nonlocal phase
        if time.monotonic()>deadline:finish('Setup window timed out');return
        if not app.busy:
            if phase=='find':
                matches=[i for i,d in enumerate(app.devices) if d.serial==args.serial]
                if len(matches)!=1:finish('Thor was not listed');return
                app.device_box.current(matches[0])
                if args.action=='refresh':finish();return
                phase='action';app.buttons[args.action].invoke()
            else:
                if not app.status.get().startswith('Done.'):finish('Setup action failed: '+app.status.get())
                else:finish()
                return
        root.after(100,advance)
    root.after(200,app.refresh_button.invoke);root.after(400,advance);root.mainloop()
    if failure:raise RuntimeError(failure[0])
    print('Desktop setup UI '+args.action+' passed. Evidence: '+str(args.out))


if __name__=='__main__':main()
