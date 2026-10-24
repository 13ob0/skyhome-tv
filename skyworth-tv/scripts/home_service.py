#!/usr/bin/env python3
"""Explicitly enable/disable SkyHome's accessibility compatibility service via ADB.

This changes secure device settings. Run enable only with the device owner's
explicit authorization. Other accessibility services are preserved.
"""
import argparse
import subprocess

p=argparse.ArgumentParser(description=__doc__)
p.add_argument('--adb',default='adb')
p.add_argument('--serial',required=True)
mode=p.add_mutually_exclusive_group(required=True)
mode.add_argument('--enable',action='store_true')
mode.add_argument('--disable',action='store_true')
a=p.parse_args()
base=[a.adb,'-s',a.serial,'shell','settings']
def get(key):return subprocess.check_output(base+['get','secure',key],text=True).strip()
def command(*parts):subprocess.run(base+list(parts),check=True)
before=get('enabled_accessibility_services')
services=[] if before in ('','null') else before.split(':')
ours={'tv.skyhome/.HomeKeyService','tv.skyhome/tv.skyhome.HomeKeyService'}
services=[s for s in services if s not in ours]
if a.enable:services.append('tv.skyhome/.HomeKeyService')
if services:
    command('put','secure','enabled_accessibility_services',':'.join(services))
    command('put','secure','accessibility_enabled','1')
else:
    command('delete','secure','enabled_accessibility_services')
    command('put','secure','accessibility_enabled','0')
print('SkyHome compatibility '+('enabled' if a.enable else 'disabled')+'; other services preserved.')
