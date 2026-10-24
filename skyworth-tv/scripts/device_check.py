#!/usr/bin/env python3
"""Read actual device UI and exercise app routes without changing preferences.

Usage: python3 scripts/device_check.py --adb /path/adb --serial IP:5555 --out /tmp/skyhome-qa
"""
import argparse
from pathlib import Path
import re
import subprocess
import time
import xml.etree.ElementTree as ET

p=argparse.ArgumentParser()
p.add_argument('--adb',default='adb')
p.add_argument('--serial',required=True)
p.add_argument('--out',required=True,type=Path)
args=p.parse_args()
args.out.mkdir(parents=True,exist_ok=True)
base=[args.adb,'-s',args.serial]

def adb(*parts):
    return subprocess.check_output(base+list(parts),timeout=30)

def key(code):
    adb('shell','input','keyevent',str(code));time.sleep(.3)

def ui():
    adb('shell','uiautomator','dump','/sdcard/skyhome-check.xml')
    raw=adb('shell','cat','/sdcard/skyhome-check.xml')
    return list(ET.fromstring(raw[raw.index(b'<?xml'):]).iter('node'))

def capture(name):
    raw=adb('exec-out','screencap','-p')
    (args.out/(name+'.png')).write_bytes(raw[raw.index(b'\x89PNG\r\n\x1a\n'):])

def tap(label):
    nodes=ui()
    node=next(n for n in nodes if n.get('text')==label)
    x1,y1,x2,y2=map(int,re.findall(r'\d+',node.get('bounds')))
    adb('shell','input','tap',str((x1+x2)//2),str((y1+y2)//2));time.sleep(.5)
    # Android TextView can consume a first touch only to establish focus.
    if any(n.get('text')==label and n.get('clickable')=='true' and n.get('bounds')==node.get('bounds') for n in ui()):
        key(23)

def start():
    adb('shell','am','start','-n','tv.skyhome/.MainActivity');time.sleep(.5)

def resumed():
    raw=adb('shell','dumpsys','activity','activities').decode(errors='replace')
    return '\n'.join(line.strip() for line in raw.splitlines() if 'ResumedActivity:' in line)

results=[]
adb('shell','am','force-stop','tv.skyhome')
start()
capture('home')
nodes=ui()
assert any(n.get('text')=='全部应用' and n.get('clickable')=='true' for n in nodes)
focused=next((n.get('text') for n in nodes if n.get('focused')=='true'),None)
assert focused, 'No initial remote focus'
results.append('PASS home and initial D-pad focus: '+focused)
key(82) # menu
assert any(n.get('text')=='设置' for n in ui())
key(20) # next settings row
focused=next((n.get('text') for n in ui() if n.get('focused')=='true'),None)
assert focused and '首页布局' in focused, 'D-pad did not move to layout row'
capture('settings')
results.append('PASS menu key and D-pad movement')
key(4)
tap('全部应用')
capture('apps')
nodes=ui()
assert any(n.get('text')=='全部应用' and n.get('clickable')=='false' for n in nodes)
assert any(n.get('focused')=='true' and n.get('clickable')=='true' for n in nodes)
key(22)
assert any(n.get('focused')=='true' and n.get('clickable')=='true' for n in ui())
results.append('PASS all-apps grid and D-pad movement')
key(4)
tap('信号源')
state=resumed()
assert 'com.ccos.cooui.homepage.tvsource.SourceDialogActivity' in state,state
source_labels={n.get('text') for n in ui()}
assert {'HDMI1','HDMI2'}.issubset(source_labels),source_labels
capture('source')
results.append('PASS Skyworth source chooser: '+state)
start()
tap('原桌面')
state=resumed()
assert 'com.tianci.movieplatform/' in state,state
results.append('PASS stock home: '+state)
start()
key(82)
tap('系统设置')
state=resumed()
assert 'com.skyworth.setting/' in state,state
results.append('PASS system settings: '+state)
start()
key(4) # close the retained settings sheet
tap('投屏')
state=resumed()
assert any(pkg in state for pkg in ('com.dangbei.screencast/','com.hpplay.happyplay.aw/','com.skyworth.smartcast/','com.coocaa.skycast.tool/')),state
results.append('PASS cast application entry: '+state)
start()
capture('home-final')
assert 'tv.skyhome/.MainActivity' in resumed(), 'Final screen did not return to SkyHome'
results.append('PASS return to home')
(args.out/'results.txt').write_text('\n'.join(results)+'\n')
print('\n'.join(results))
