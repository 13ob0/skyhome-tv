#!/usr/bin/env python3
"""55F1 source integration check using native input; does not suppress Home service.

Switches HDMI1/2 through SkyHome's menu, restores the original source, ends at Home.
Do not operate the remote during this check. Does not verify external HDMI media.
"""
import argparse
from pathlib import Path
import re
import subprocess
import time

p=argparse.ArgumentParser(description=__doc__)
p.add_argument('--adb',default='adb')
p.add_argument('--serial',required=True)
p.add_argument('--out',required=True,type=Path)
a=p.parse_args()
a.out.mkdir(parents=True,exist_ok=True)
base=[a.adb,'-s',a.serial]
def adb(*parts):
    return subprocess.check_output(base+list(parts),timeout=30)
def query(path):
    raw=adb('shell','content','query','--uri','content://com.skyworth.tv.sdk.provider/system/'+path,
            '--projection','sourceName:sourceIndex:displayName').decode(errors='replace')
    return [(name,int(index)) for name,index in re.findall(r'sourceName=([^,]+), sourceIndex=(-?\d+)',raw)]
def key(*codes):
    adb('shell','input','keyevent',*[str(code) for code in codes])
    time.sleep(.35)
def resumed():
    raw=adb('shell','dumpsys','activity','activities').decode(errors='replace')
    return next((line for line in raw.splitlines() if 'mResumedActivity:' in line),'')
def home():
    adb('shell','am','start','-a','android.intent.action.MAIN','-c','android.intent.category.HOME',
            '-n','tv.skyhome/.MainActivity')
    time.sleep(.5)
def menu():
    home()
    # On the validated 55F1/default inset, tap the source footer to establish its
    # focus. HOME closes a sheet if the tap activated it, leaving that footer focused.
    # Repeated Up/Down no longer anchors focus now that lists wrap.
    adb('shell','input','tap','375','970')
    home()
    key(23)
    time.sleep(.6)
def capture(name):
    raw=adb('exec-out','screencap','-p')
    (a.out/(name+'.png')).write_bytes(raw[raw.index(b'\x89PNG\r\n\x1a\n'):])
def select(target):
    sources=query('source_list')
    menu()
    index=sources.index(target)
    if index:key(*([20]*index))
    key(23)
    for _ in range(15):
        if query('current_source')==[target] and (target[0]!='HDMI' or 'com.skyworth.tv/' in resumed()):return
        time.sleep(.3)
    raise AssertionError(('Source transition not confirmed',target,query('current_source'),resumed()))

initial=query('current_source')
sources=query('source_list')
assert b'tv.skyhome' in adb('shell','settings','get','secure','enabled_accessibility_services'), 'Enable the previously authorized Home service before this check'
assert len(initial)==1 and initial[0] in sources,('Cannot restore initial source',initial,sources)
assert ('HDMI',0) in sources and ('HDMI',1) in sources,sources
try:
    menu();capture('source-menu')
    key(21)  # left returns to the footer; confirm opens sources again
    key(23);capture('source-reopened')
    for port in (1,0):
        select(('HDMI',port));capture('hdmi'+str(port+1))
        key(3)
        for _ in range(10):
            if 'tv.skyhome/.MainActivity' in resumed():break
            time.sleep(.2)
        assert 'tv.skyhome/.MainActivity' in resumed(),resumed()
        print('PASS HDMI'+str(port+1)+' and Home return',flush=True)
    menu()
    key(*([20]*len(sources)))
    key(23)
    time.sleep(2)
    assert 'SourceDialogActivity' in resumed(),resumed()
    capture('system-chooser')
    key(4)
    assert 'tv.skyhome/.MainActivity' in resumed(),resumed()
    capture('source-returned')
    print('PASS system chooser remains open and returns',flush=True)
finally:
    try:
        if query('current_source')!=initial:select(initial[0])
    finally:
        home()
assert b'InputFilterEnabled: false' in adb('shell','dumpsys','input')
print('PASS original source restored; global key filtering disabled',flush=True)
