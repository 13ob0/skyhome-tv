#!/usr/bin/env python3
"""Exercise text menus on a connected TV without changing saved preferences.

UIAutomator may temporarily suppress other accessibility services. Verify physical
Home separately, and do not operate the remote while this check is running.
"""
from pathlib import Path
import argparse,subprocess,time,re,xml.etree.ElementTree as ET
parser=argparse.ArgumentParser()
parser.add_argument('--adb',default='adb')
parser.add_argument('--serial',required=True)
parser.add_argument('--out',type=Path,required=True)
args=parser.parse_args()
out=args.out;out.mkdir(parents=True,exist_ok=True)
base=[args.adb,'-s',args.serial]
def run(*args):return subprocess.check_output(base+list(args),timeout=30)
def key(code,count=1):
    for i in range(count):run('shell','input','keyevent',str(code))
    time.sleep(.25)
def nodes():
    for attempt in range(3):
        try:
            run('shell','uiautomator','dump','/sdcard/skyhome-navigation.xml');break
        except subprocess.CalledProcessError:
            if attempt==2:raise
            time.sleep(.8)
    raw=run('shell','cat','/sdcard/skyhome-navigation.xml')
    return list(ET.fromstring(raw[raw.index(b'<?xml'):]).iter('node'))
def focused():return next((n for n in nodes() if n.get('focused')=='true'),None)
def shot(name):
    raw=run('exec-out','screencap','-p');(out/(name+'.png')).write_bytes(raw[raw.index(b'\x89PNG'):])
def tap(text):
    n=next(n for n in nodes() if n.get('text')==text and n.get('clickable')=='true')
    x1,y1,x2,y2=map(int,re.findall(r'\d+',n.get('bounds')))
    run('shell','input','tap',str((x1+x2)//2),str((y1+y2)//2));time.sleep(.2)
    # TextViews in touch focus mode establish focus on the first touch.
    if focused().get('text')==text:key(23)
results=[]
def passed(message):
    results.append(message);print(message,flush=True)
run('shell','am','start','-a','android.intent.action.MAIN','-c','android.intent.category.HOME','-n','tv.skyhome/.MainActivity');time.sleep(.5)
assert focused() is not None
shot('home');passed('PASS home with remote focus')
key(82)
assert focused().get('text').startswith('背景')
key(23)
assert any(n.get('text')=='背景' for n in nodes())
key(20,2)
backgroundFocus=focused().get('text');shot('background-depth-3')
key(4)
assert focused().get('text').startswith('背景')
key(23)
assert focused().get('text')==backgroundFocus,(focused().get('text'),backgroundFocus)
passed('PASS stacked background menu; Back restores parent and child focus')
key(20,20)
n=focused();assert n.get('text')=='返回上一级',n.attrib
_,y1,_,y2=map(int,re.findall(r'\d+',n.get('bounds')));assert 150<y1<y2<1000,n.attrib
shot('background-bottom');passed('PASS scroll reaches last row inside viewport')
key(21)
assert focused().get('text').startswith('背景')
passed('PASS left returns one menu level')
key(20,6)
assert focused().get('text')=='关于与兼容性',focused().attrib
key(23)
assert focused().get('text')=='设备信息与使用说明'
key(20)
assert focused().get('text')=='许可与照片署名'
key(23)
assert any('MIT License' in n.get('text','') for n in nodes())
shot('licenses-depth-4')
key(20,3)
shot('licenses-scrolled')
key(4)
assert focused().get('text')=='许可与照片署名'
key(4)
assert focused().get('text')=='关于与兼容性'
key(4)
passed('PASS four-level navigation and parent focus restoration')
key(82);key(20,3);key(23)
assert any(n.get('text')=='编辑常用应用' for n in nodes())
shot('edit-grid-depth-3')
key(4)
assert focused().get('text').startswith('编辑常用')
key(4)
passed('PASS nested edit grid and parent focus restoration')
tap('全部应用');grid=focused();first=grid.get('text');shot('apps')
key(22);second=focused().get('text');assert second!=first
key(21);assert focused().get('text')==first
key(19);assert focused().get('text')==first
key(20,30);bottom=focused();assert bottom is not None
shot('apps-bottom');passed('PASS grid D-pad navigation and top/bottom boundaries')
run('shell','input','keyevent','--longpress','23');time.sleep(.5)
assert any(n.get('text')=='常用应用管理' or '常用应用管理' in n.get('text','') for n in nodes())
shot('app-actions')
key(4)
assert focused().get('text')==bottom.get('text')
passed('PASS grid long press and return to selected application')
run('shell','am','start','-a','android.intent.action.MAIN','-c','android.intent.category.HOME','-n','tv.skyhome/.MainActivity');time.sleep(.5)
assert any(n.get('text')=='全部应用' and n.get('clickable')=='true' for n in nodes())
shot('home-final');passed('PASS HOME intent clears all menus')
log=run('logcat','-d','-t','200','AndroidRuntime:E','*:S').decode(errors='replace')
(out/'android-runtime.txt').write_text(log)
assert 'FATAL EXCEPTION' not in log,log
(out/'results.txt').write_text('\n'.join(results)+'\n')
print('Verification complete; results saved to '+str(out/'results.txt'))
