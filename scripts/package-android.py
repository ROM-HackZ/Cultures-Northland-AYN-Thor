"""Create a ready-built setup bundle without game data or development captures."""
import argparse
from pathlib import Path
import zipfile

ROOT=Path(__file__).resolve().parents[1]


def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--out',type=Path,default=ROOT/'artifacts/releases/Northland-Thor-setup.zip')
    args=parser.parse_args()
    names=['Setup.cmd','Setup.command','Setup.sh','README.md',
           'scripts/setup-ui.py','scripts/setup_backend.py','scripts/android-controller.py','scripts/northland_session.py',
           'artifacts/android/northland-panel.apk','artifacts/android/northland-bridge.jar',
           'docs/setup.md','docs/session-recovery.md','docs/control-groups.md','docs/ui-navigation.md',
           'docs/android-controller.md','docs/port-design.md','docs/compatibility.md',
           'third_party/NOTICE.md','third_party/Shizuku-API-LICENSE.txt','third_party/AndroidX-annotation-LICENSE.txt']
    for name in names:
        if not (ROOT/name).is_file():raise FileNotFoundError('Build/setup input missing: '+name)
    args.out.parent.mkdir(parents=True,exist_ok=True)
    temp=args.out.with_suffix('.tmp')
    with zipfile.ZipFile(temp,'w',zipfile.ZIP_DEFLATED) as archive:
        for name in names:
            item=zipfile.ZipInfo('Northland-Thor/'+name)
            item.create_system=3  # Preserve executable launchers when packing on Windows.
            item.external_attr=(0o755 if name.endswith(('.sh','.command')) else 0o644)<<16
            item.compress_type=zipfile.ZIP_DEFLATED
            archive.writestr(item,(ROOT/name).read_bytes())
    temp.replace(args.out)
    print('Created '+str(args.out))


if __name__=='__main__':main()
