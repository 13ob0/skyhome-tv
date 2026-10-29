#!/usr/bin/env python3
"""Verify vertical wrapping without changing favorites, background, or other preferences.

UIAutomator can temporarily suppress accessibility services; check Home separately.
"""
import argparse,re,subprocess,time,xml.etree.ElementTree as ET
from pathlib import Path
p=argparse.ArgumentParser(description=__doc__)
p.add_argument('--adb',default='adb');p.add_argument('--serial',required=True);p.add_argument('--out',required=True,type=Path)
a=p.parse_args();a.out.mkdir(parents=True,exist_ok=True)
base=[a.adb,'-s',a.serial]
def run(*args):return subprocess.check_output(base+list(args),timeout=30)
def key(*codes):
    run('shell','input','keyevent',*[str(c) for c in codes]);time.sleep(.2)
def nodes():
    for attempt in range(3):
        result=run('shell','uiautomator','dump','/sdcard/skyhome-cycle.xml')
        if b'dumped to' in result:
            raw=run('shell','cat','/sdcard/skyhome-cycle.xml')
            return list(ET.fromstring(raw[raw.index(b'<?xml'):]).iter('node'))
        time.sleep(.5)
    raise AssertionError('UIAutomator did not return a fresh tree')
def focused():
    node=next((n for n in nodes() if n.get('focused')=='true'),None)
    assert node is not None,'No focused node'
    return node
screen_height=int(re.findall(r'\d+x(\d+)',run('shell','wm','size').decode())[-1])
def visible(node):
    x1,y1,x2,y2=map(int,re.findall(r'\d+',node.get('bounds')))
    assert x2>x1 and 0<=y1<y2<=screen_height, node.attrib
    return x1
def expect(text):
    node=focused();assert node.get('text')==text,(node.get('text'),text);visible(node)
def home():
    run('shell','am','start','-a','android.intent.action.MAIN','-c','android.intent.category.HOME','-n','tv.skyhome/.MainActivity');time.sleep(.3)
def footer(label):
    home()
    node=next(n for n in nodes() if n.get('text')==label and n.get('clickable')=='true')
    x1,y1,x2,y2=map(int,re.findall(r'\d+',node.get('bounds')))
    run('shell','input','tap',str((x1+x2)//2),str((y1+y2)//2))
    home()  # close the sheet if tapping an already-focused item activated it
    expect(label)
def shot(name):
    run('shell','screencap','-p','/sdcard/skyhome-cycle.png')
    run('pull','/sdcard/skyhome-cycle.png',str(a.out/(name+'.png')))
def grid_cycle(label):
    counts=[];all_items=set()
    for column in range(4):
        first=focused();first_text=first.get('text');left=visible(first);visited=[]
        first_bounds=list(map(int,re.findall(r'\d+',first.get('bounds'))))
        # Existing blank cells omit the real cell's margin, slightly changing the
        # partial row's weight distribution. Allow that drift, not a full column.
        column_tolerance=max(2,(first_bounds[2]-first_bounds[0])//8)
        for _ in range(129):
            node=focused();text=node.get('text');assert abs(visible(node)-left)<=column_tolerance,'Vertical movement changed column'
            assert text not in visited,'Repeated an item before completing the cycle'
            visited.append(text);key(20)
            if focused().get('text')==first_text:break
        else:raise AssertionError('Grid never wrapped')
        key(19);expect(visited[-1]);key(20);expect(first_text)
        assert not all_items.intersection(visited),'Columns overlap'
        all_items.update(visited);counts.append(len(visited))
        if column==0:shot(label+'-wrapped')
        key(22)
        if focused().get('text')==first_text:break
    assert counts==sorted(counts,reverse=True) and max(counts)-min(counts)<=1,counts
    print('PASS '+label+' same-column cycles; column lengths='+str(counts),flush=True)

try:
    footer('全部应用');key(20);first=focused().get('text');visible(focused())
    key(19);expect('全部应用');key(20);expect(first)
    favorites=[]
    for _ in range(11):
        text=focused().get('text')
        if text=='全部应用':break
        assert text not in favorites;favorites.append(text);key(20)
    else:raise AssertionError('Home never reached the footer')
    key(19);expect(favorites[-1]);key(20);expect('全部应用')
    key(22);expect('信号源');key(20);expect(first);key(19);expect('全部应用')
    print('PASS home favorites/footer cycle and footer horizontal movement',flush=True)
    key(82);assert focused().get('text').startswith('背景')
    first=focused().get('text');key(19);expect('关于与兼容性');key(20);expect(first)
    key(23);first=focused().get('text');key(19);expect('返回上一级');shot('text-last-visible')
    key(20);expect(first);key(19);expect('返回上一级');key(21)
    # Left returned to Settings; the Background row remains selected.
    assert focused().get('text').startswith('背景')
    print('PASS text list wrap, cross-screen reveal and parent focus restoration',flush=True)
    home();footer('全部应用');key(23);grid_cycle('apps')
    selected=focused().get('text');run('shell','input','keyevent','--longpress','23');time.sleep(.3)
    expect('打开应用');key(4);expect(selected);key(4)
    key(82);key(20,20,20,23);grid_cycle('edit-apps');key(4,4)
    key(82);key(20,20,20,20,20,20,23);key(20,23)
    assert focused().get('class')=='android.widget.ScrollView'
    key(20,20,20);assert focused().get('class')=='android.widget.ScrollView'
    key(19,19,19);assert focused().get('class')=='android.widget.ScrollView'
    print('PASS long press/return and reading-page scroll focus',flush=True)
finally:
    home()
print('Verification complete; no saved preferences changed',flush=True)
