"""Exercise the installed group controls through the Thor's controller event node.

Use an idle gameplay session. Replace and add intentionally change the supplied
group slot; choose a spare slot. Captures establish game behavior separately
from the helper's request counters. Never launches, loads or resets the game.
"""
import argparse
import json
import math
from pathlib import Path
import re
import subprocess
import time


def main():
    p=argparse.ArgumentParser(description=__doc__)
    p.add_argument('--adb',default='adb')
    p.add_argument('--serial')
    p.add_argument('--case',required=True,choices=('wheel','select','replace','add','cancel-replace','change-slot','dpad','diagonal','drag','conflict','both','panel-loss',
                                                'touch-select','touch-set','touch-add','touch-cancel'))
    p.add_argument('--slot',type=int,choices=range(10),default=9)
    p.add_argument('--out',type=Path,default=Path('artifacts/control-groups'))
    args=p.parse_args()
    if args.case=='dpad' and args.slot not in (1,2,3,4): p.error('D-pad slots are 1 to 4')
    adb=[args.adb]+(['-s',args.serial] if args.serial else [])
    def shell(*words):
        return subprocess.check_output(adb+['shell',*words],text=True)
    def state(): return json.loads(shell('cat','/data/local/tmp/northland-thor/state.json'))
    def wait(check):
        end=time.monotonic()+4
        while time.monotonic()<end:
            s=state()
            if check(s):return s
            time.sleep(.04)
        raise AssertionError('State not reached: '+repr(s))
    dump=shell('dumpsys','input');start=dump.index('FocusedWindows:')
    assert re.search(r"displayId=0, name='[^'\n]* com\.runesoft\.cultures/",dump[start:dump.index('FocusRequests:',start)]),'Northland must be focused'
    devices=re.findall(r'add device \d+: (/dev/input/event\d+)\s+name:\s+"([^"]+)"',shell('getevent','-lp'))
    nodes=[node for node,name in devices if name=='Odin Controller']
    assert len(nodes)==1,'Expected one Odin Controller'
    initial=wait(lambda s:s['focused'] and s['armed'] and not s['mouse'] and not s['keys'] and not s['wheel'])
    assert 'groupRequests' in initial,'Install the group-controls build first'
    touched=set();evidence={};panel_stopped=False
    def emit(values):
        touched.update((kind,code) for kind,code,_ in values)
        lines=[f'sendevent {nodes[0]} {kind} {code} {value}' for kind,code,value in values]
        lines.append(f'sendevent {nodes[0]} 0 0 0')
        subprocess.run(adb+['shell','sh'],input='\n'.join(lines)+'\n',text=True,check=True)
    def capture(phase,both=True):
        for name,surface in [('bottom','4630946482288158084')]+([('top','4630946441858561667')] if both else []):
            data=subprocess.check_output(adb+['exec-out','screencap','-p','-d',surface])
            assert data.startswith(b'\x89PNG\r\n\x1a\n')
            (args.out/f'{args.case}-{phase}-{name}.png').write_bytes(data)
    def point(slot):
        angle=((slot+9)%10)*math.pi/5
        return [(3,2,round(math.sin(angle)*32767)),(3,5,round(-math.cos(angle)*32767))]
    args.out.mkdir(parents=True,exist_ok=True)
    capture('before')
    try:
        if args.case.startswith('touch-'):
            def tap(x,y):shell('input','-d','4','tap',str(x),str(y));time.sleep(.12)
            tap(1070,240)
            index=(args.slot+9)%10
            tap(156+(index%5)*232,367+(index//5)*145)
            if args.case in ('touch-set','touch-cancel'):
                tap(600,740);capture('confirmation')
                tap(200 if args.case=='touch-cancel' else 900,860)
            else:tap(200 if args.case=='touch-select' else 1000,740)
            if args.case=='touch-cancel':
                time.sleep(.4);assert state()['groupRequests']==initial['groupRequests'],'Cancel changed the group'
            else:
                evidence['request']=wait(lambda s:s['groupRequests']==initial['groupRequests']+1)
                assert evidence['request']['lastGroup']==args.slot
                assert evidence['request']['lastGroupOperation']==args.case[6:].upper()
        elif args.case in ('dpad','diagonal'):
            vectors={1:(0,-1),2:(1,0),3:(0,1),4:(-1,0)}
            x,y=(1,-1) if args.case=='diagonal' else vectors[args.slot]
            events=[(3,16,x),(3,17,y)]
            emit(events);time.sleep(.5)
            if args.case=='diagonal':assert state()['groupRequests']==initial['groupRequests']
            else:
                evidence['first']=wait(lambda s:s['groupRequests']==initial['groupRequests']+1)
                assert evidence['first']['lastGroup']==args.slot
                emit(events+[(1,{1:0x220,2:0x223,3:0x221,4:0x222}[args.slot],1)])
                time.sleep(.8);assert state()['groupRequests']==initial['groupRequests']+1,'Held/duplicate D-pad repeated'
        elif args.case=='both':
            emit([(1,0x137,1),(3,9,32767),(1,0x130,1)])
            time.sleep(.5)
            evidence['blocked']=state()
            assert not evidence['blocked']['mouse'] and not evidence['blocked']['keys'] and evidence['blocked']['wheelMode']=='NONE'
            assert evidence['blocked']['groupRequests']==initial['groupRequests']
        elif args.case=='drag':
            emit([(3,10,32767),(3,0,12000)])
            wait(lambda s:s['mouse']==1);time.sleep(.3)
            emit([(3,0,0),(1,0x137,1)])
            evidence['drag']=wait(lambda s:s['mouse']==1 and s['wheelMode']=='NONE')
            time.sleep(.8);assert state()['mouse']==1 and state()['groupRequests']==initial['groupRequests']
        else:
            emit([(1,0x137,1)]+point(args.slot))
            evidence['wheel']=wait(lambda s:s['wheelMode']=='GROUPS' and s['choice']==(args.slot+9)%10)
            assert not evidence['wheel']['mouse'] and not evidence['wheel']['keys'],'Wheel leaked game input'
            capture('wheel')
            if args.case in ('replace','cancel-replace','change-slot','panel-loss'):
                emit([(1,0x134,1)])
                evidence['progress']=wait(lambda s:s['progress']>0)
                if args.case=='cancel-replace':emit([(1,0x131,1)])
                elif args.case=='change-slot':emit(point((args.slot+1)%10))
                elif args.case=='panel-loss':
                    panel_stopped=True;shell('am','force-stop','org.northland.thor')
                    evidence['disconnected']=wait(lambda s:not s['armed'] and not s['mouse'] and not s['keys'] and s['wheelMode']=='NONE')
                    shell('am','start','--display','4','-n','org.northland.thor/.SetupActivity','--ez','show-panel','true')
                    panel_stopped=False
                else:
                    evidence['request']=wait(lambda s:s['groupRequests']==initial['groupRequests']+1)
                    assert evidence['request']['lastGroupOperation']=='SET' and evidence['request']['lastGroup']==args.slot
                if args.case!='replace':
                    time.sleep(.85);assert state()['groupRequests']==initial['groupRequests'],'Cancelled hold changed a group'
            elif args.case in ('select','add'):
                emit([(1,0x130 if args.case=='select' else 0x133,1)])
                evidence['request']=wait(lambda s:s['groupRequests']==initial['groupRequests']+1)
                assert evidence['request']['lastGroupOperation']==('SELECT' if args.case=='select' else 'ADD')
                assert not evidence['request']['mouse'],'Confirmation leaked a mouse button'
            elif args.case=='conflict':
                emit([(3,9,32767)])
                wait(lambda s:s['wheelMode']=='NONE')
                emit([(1,0x137,0)]);time.sleep(.5)
                assert state()['wheelMode']=='NONE','Second wheel opened before neutral'
            else:emit([(1,0x131,1)])
        capture('held')
    finally:
        emit([(kind,code,0) for kind,code in sorted(touched)])
        if panel_stopped:shell('am','start','--display','4','-n','org.northland.thor/.SetupActivity','--ez','show-panel','true')
    evidence['released']=wait(lambda s:s['armed'] and not s['mouse'] and not s['keys'] and not s['wheel'])
    time.sleep(.15);capture('after')
    evidence['initial']=initial
    evidence['game_result']='Inspect top-screen captures; request counters alone do not prove membership.'
    (args.out/f'{args.case}.json').write_text(json.dumps(evidence,indent=2)+'\n',encoding='utf-8')
    print(f'{args.case}: input path and release passed. Review captures in {args.out}')


if __name__=='__main__':main()
