"""Check LT navigation through the actual Thor controller input path.

The toolbar case opens Options with the wrench and closes it with its X button.
It changes no option or save. Start in idle gameplay, with controls released.
"""
import argparse
import json
from pathlib import Path
import re
import subprocess
import time


def main():
    p=argparse.ArgumentParser(description=__doc__)
    p.add_argument('--adb',default='adb');p.add_argument('--serial')
    p.add_argument('--case',required=True,choices=('hold','toolbar','popup','list','drag'))
    p.add_argument('--out',type=Path,default=Path('artifacts/ui-navigation'))
    args=p.parse_args();adb=[args.adb]+(['-s',args.serial] if args.serial else [])
    def shell(*parts):return subprocess.check_output(adb+['shell',*parts],text=True)
    def state():return json.loads(shell('cat','/data/local/tmp/northland-thor/state.json'))
    def wait(check):
        end=time.monotonic()+8
        while time.monotonic()<end:
            s=state()
            if check(s):return s
            time.sleep(.04)
        raise AssertionError('Expected state not reached: '+repr(s))
    dump=shell('dumpsys','input');a=dump.index('FocusedWindows:')
    assert re.search(r"displayId=0, name='[^'\n]* com\.runesoft\.cultures/",dump[a:dump.index('FocusRequests:',a)])
    devices=re.findall(r'add device \d+: (/dev/input/event\d+)\s+name:\s+"([^"]+)"',shell('getevent','-lp'))
    nodes=[node for node,name in devices if name=='Odin Controller'];assert len(nodes)==1
    initial=wait(lambda s:s['armed'] and not s['mouse'] and not s['keys'] and not s['wheel'] and not s['navigation'])
    assert 'navigation' in initial,'Install the UI navigation build first'
    touched=set();report={'initial':initial};args.out.mkdir(parents=True,exist_ok=True)
    def emit(events):
        touched.update((kind,code) for kind,code,value in events)
        lines=[f'sendevent {nodes[0]} {kind} {code} {value}' for kind,code,value in events]
        lines.append(f'sendevent {nodes[0]} 0 0 0')
        subprocess.run(adb+['shell','sh'],input='\n'.join(lines)+'\n',text=True,check=True)
    def capture(phase):
        for name,surface in [('top','4630946441858561667'),('bottom','4630946482288158084')]:
            data=subprocess.check_output(adb+['exec-out','screencap','-p','-d',surface])
            assert data.startswith(b'\x89PNG\r\n\x1a\n')
            (args.out/f'{args.case}-{phase}-{name}.png').write_bytes(data)
    def direction(index):
        before=state()['navigationRequests']
        x,y=[(0,-1),(1,0),(0,1),(-1,0)][index]
        emit([(3,16,x),(3,17,y)]);time.sleep(.06);emit([(3,16,0),(3,17,0)])
        s=wait(lambda s:s['navigationRequests']>before)
        assert s['mouse']==0 and s['groupRequests']==initial['groupRequests'],'Navigation clicked or recalled a group'
        return s
    def click():
        emit([(1,0x130,1)]);time.sleep(.09);emit([(1,0x130,0)])
        wait(lambda s:s['mouse']==0);time.sleep(.2)
    capture('before')
    try:
        if args.case=='drag':
            emit([(1,0x130,1),(3,0,16000),(3,1,16000)])
            wait(lambda s:s['mouse']==1);time.sleep(.35);emit([(3,0,0),(3,1,0)])
            time.sleep(.1);before=state()
            emit([(3,10,32767),(3,16,-1)])
            time.sleep(.5);held=state()
            assert held['mouse']==1 and not held['navigation'],'LT interrupted A dragging'
            assert held['navigationRequests']==before['navigationRequests']
            emit([(3,10,0),(3,16,0)]);time.sleep(.2)
            assert state()['mouse']==1,'LT release ended an A drag'
            report['held']=held;capture('drag')
        else:
            emit([(3,10,32767)])
            held=wait(lambda s:s['navigation'])
            assert not held['mouse'],'LT pressed the mouse'
            if args.case=='hold':
                time.sleep(.8);held=state()
                assert not held['mouse'] and held['navigationRequests']==initial['navigationRequests']
                assert (held['x'],held['y'])==(initial['x'],initial['y'])
                capture('held')
            elif args.case=='toolbar':
                current=direction(3)
                assert current['x']<60,'Left did not reach toolbar'
                for _ in range(12):
                    if 440<=current['y']<=490:break
                    current=direction(0 if current['y']>490 else 2)
                assert 440<=current['y']<=490 and current['x']<60,'Wrench not reached'
                capture('wrench');click();capture('options')
                for _ in range(12):
                    current=direction(0)
                    rect=current['navigationTarget']
                    if rect and current['x']>900 and rect[2]-rect[0]<100:break
                assert rect and current['x']>900 and rect[2]-rect[0]<100,'Popup close button not reached'
                capture('close-focus');click();capture('closed')
                held=current
            else:
                moves=[]
                for index in ([2,2,2] if args.case=='list' else [1,2,3,0]):moves.append(direction(index))
                report['moves']=moves;held=moves[-1];capture('focused')
            report['held']=held
            assert state()['groupRequests']==initial['groupRequests']
    finally:
        emit([(kind,code,0) for kind,code in sorted(touched)])
    report['released']=wait(lambda s:s['armed'] and not s['navigation'] and not s['mouse'] and not s['keys'] and not s['wheel'])
    capture('after')
    report['game_result']='Review native UI captures; helper state alone is not proof of a correct target.'
    (args.out/f'{args.case}.json').write_text(json.dumps(report,indent=2)+'\n',encoding='utf-8')
    print(args.case+': input and release checks passed. Review '+str(args.out))


if __name__=='__main__':main()
