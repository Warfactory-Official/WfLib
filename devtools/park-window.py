#!/usr/bin/env python3
"""Park a dev game's window on a Hyprland workspace, out of sight, without following it.

The game is found by its working directory (the dev run dir), never by title: a player's own Minecraft window
has the same one. Usage: park-window.py <run dir> <workspace>. Exits when no such process is left.
"""
import json
import os
import subprocess
import sys
import time

run_dir = os.path.realpath(sys.argv[1])
workspace = int(sys.argv[2])


def game_pids():
    pids = set()
    for pid in os.listdir('/proc'):
        if not pid.isdigit():
            continue
        try:
            if os.path.realpath(f'/proc/{pid}/cwd') == run_dir and 'java' in os.readlink(f'/proc/{pid}/exe'):
                pids.add(int(pid))
        except OSError:
            pass
    return pids


seen = False
deadline = time.time() + 600
while time.time() < deadline:
    pids = game_pids()
    if seen and not pids:
        break
    seen |= bool(pids)
    if pids:
        clients = json.loads(subprocess.run(['hyprctl', 'clients', '-j'], capture_output=True, text=True).stdout)
        for c in clients:
            if c.get('pid') in pids and c.get('workspace', {}).get('id') != workspace:
                # This Hyprland takes Lua dispatches.
                subprocess.run(['hyprctl', 'dispatch', 'hl.dsp.window.move({ workspace = %d, follow = false, '
                                'window = "address:%s" })' % (workspace, c['address'])], capture_output=True)
    time.sleep(0.2)
