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
parser.add_argument('--home-focus-only',action='store_true',help='Only check favorites-to-footer focus navigation')
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
            result=run('shell','uiautomator','dump','/sdcard/skyhome-navigation.xml')
            if b'dumped to' not in result:
                raise RuntimeError('UIAutomator did not produce a fresh tree')
            raw=run('shell','cat','/sdcard/skyhome-navigation.xml')
            return list(ET.fromstring(raw[raw.index(b'<?xml'):]).iter('node'))
        except (subprocess.CalledProcessError,RuntimeError):
            if attempt==2:raise
            time.sleep(.8)
def focused():return next((n for n in nodes() if n.get('focused')=='true'),None)
def shot(name):
    raw=run('exec-out','screencap','-p');(out/(name+'.png')).write_bytes(raw[raw.index(b'\x89PNG'):])
def tap(text):
    n=next(n for n in nodes() if n.get('text')==text and n.get('clickable')=='true')
    x1,y1,x2,y2=map(int,re.findall(r'\d+',n.get('bounds')))
    run('shell','input','tap',str((x1+x2)//2),str((y1+y2)//2));time.sleep(.2)
    # TextViews in touch focus mode establish focus on the first touch.
    if focused().get('text')==text:key(23)
anchor=None
def check_center(title):
    tree=nodes()
    current=next(n for n in tree if n.get('content-desc')=='菜单：'+title)
    bounds=list(map(int,re.findall(r'\d+',current.get('bounds'))))
    assert bounds[0]==anchor[0], (title,bounds,anchor)
    assert abs((bounds[1]+bounds[3])/2-screen_height/2)<=1, (title,bounds,screen_height)
    assert not any(n.get('text')==title and n.get('clickable')=='false' for n in tree), 'Redundant submenu title'
screen_height=int(re.findall(r'\d+x(\d+)',run('shell','wm','size').decode())[-1])
results=[]
def passed(message):
    results.append(message);print(message,flush=True)
run('shell','am','start','-a','android.intent.action.MAIN','-c','android.intent.category.HOME','-n','tv.skyhome/.MainActivity');time.sleep(.5)
assert focused() is not None
anchor=list(map(int,re.findall(r'\d+',next(n for n in nodes() if n.get('text')=='常 用 应 用').get('bounds'))))
shot('home');passed('PASS home with remote focus')
# The last favorite must enter the footer at All Apps, not its geometric neighbor.
key(19,12)
home_tree=nodes()
favorite_scroll=next(n for n in home_tree if n.get('class')=='android.widget.ScrollView')
visible_favorites=[n.get('text') for n in favorite_scroll.iter('node') if n.get('focusable')=='true']
assert visible_favorites and focused().get('text')==visible_favorites[0]
path=[visible_favorites[0]]
favorite_nodes={id(n) for n in favorite_scroll.iter('node')}
footer_labels={'全部应用','信号源','投屏','原桌面','设置'}
footer={(n.get('text'),n.get('bounds')) for n in home_tree if id(n) not in favorite_nodes and n.get('text') in footer_labels and n.get('clickable')=='true'}
for step in range(12):
    key(20);candidate=focused();label=candidate.get('text')
    if (label,candidate.get('bounds')) in footer:
        assert label=='全部应用', 'Last favorite moved to '+label
        break
    path.append(label)
else:
    raise AssertionError('Could not leave favorites list')
assert path[:len(visible_favorites)]==visible_favorites, (path,visible_favorites)
shot('favorites-to-all-apps')
key(22);assert focused().get('text')=='信号源'
key(21);assert focused().get('text')=='全部应用'
passed('PASS favorites order and last-favorite Down -> All Apps; footer Left/Right unchanged')
if args.home_focus_only:
    (out/'results.txt').write_text('\n'.join(results)+'\n')
    print('Home focus verification complete.');raise SystemExit(0)
key(82)
check_center('设置')
assert focused().get('text').startswith('背景')
key(23)
check_center('背景')
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
check_center('编辑常用应用')
shot('edit-grid-depth-3')
key(4)
assert focused().get('text').startswith('编辑常用')
key(4)
passed('PASS nested edit grid and parent focus restoration')
tap('全部应用');check_center('全部应用');grid=focused();first=grid.get('text');shot('apps');passed('PASS title-free submenus centered vertically with the home left margin')
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
