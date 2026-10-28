"""Resolve GAMMA's artefact sections (base configs + mods in MO2 order + DLTX mod_system patches) and classify
which ones only use effects WFLib's artefacts support. Usage: gamma_artefacts.py <base configs dir> <gamma dir>"""
import os, re, sys, json

base, gamma = sys.argv[1], sys.argv[2]
order = [l.strip()[1:] for l in open(os.path.join(gamma, 'profiles/G.A.M.M.A/modlist.txt'), encoding='latin-1')
         if l.strip().startswith('+')]
files = {}
for root, _, names in os.walk(base):
    for n in names:
        p = os.path.join(root, n); files[os.path.relpath(p, os.path.dirname(base)).lower()] = p
for mod in reversed(order):  # lowest priority first; later writes win
    cfg = os.path.join(gamma, 'mods', mod, 'gamedata', 'configs')
    if not os.path.isdir(cfg):
        continue
    for root, _, names in os.walk(cfg):
        for n in names:
            if n.lower().endswith('.ltx'):
                p = os.path.join(root, n); files['configs/' + os.path.relpath(p, cfg).lower()] = p

sections, parents = {}, {}
def parse(path):
    cur = None
    for raw in open(path, encoding='latin-1', errors='replace'):
        line = raw.split(';')[0].strip()
        if not line or line.startswith('#'):
            continue
        m = re.match(r'^(!{0,2})\[([^\]]+)\]\s*(?::\s*(.*))?$', line)
        if m:
            bang, name, par = m.group(1), m.group(2).strip().lower(), m.group(3)
            if bang == '!!':
                sections.pop(name, None); cur = None; continue
            if bang == '!':
                sections.setdefault(name, {})
            else:
                sections[name] = {}
            if par is not None and par.strip():
                parents[name] = [x.strip().lower() for x in par.split(',')]
            elif bang != '!':
                parents.pop(name, None)
            cur = name; continue
        if cur and '=' in line:
            k, v = line.split('=', 1); sections[cur][k.strip().lower()] = v.strip()
        elif cur and line.startswith('!'):
            sections[cur].pop(line[1:].strip().lower(), None)

for rel in sorted(f for f in files if f.startswith('configs/items/items/') and 'artefact' in f):
    parse(files[rel])
for rel in sorted((f for f in files if os.path.basename(f).startswith('mod_system_')), key=os.path.basename):
    parse(files[rel])

def get(sec, key, seen=()):
    if sec in seen or sec not in sections:
        return None
    if key in sections[sec]:
        return sections[sec][key]
    for p in parents.get(sec, []):
        v = get(p, key, seen + (sec,))
        if v is not None:
            return v
    return None

def num(v):
    try: return float(str(v).split(',')[0])
    except Exception: return 0.0

RESTORE = ['health_restore_speed', 'power_restore_speed', 'radiation_restore_speed', 'bleeding_restore_speed',
           'satiety_restore_speed']
IMM = ['burn_immunity', 'shock_immunity', 'strike_immunity', 'wound_immunity', 'fire_wound_immunity',
       'explosion_immunity', 'chemical_burn_immunity', 'radiation_immunity', 'telepatic_immunity']
OTHER = ['additional_inventory_weight', 'additional_inventory_weight2', 'jump_speed', 'walk_accel']
SUPPORTED = {'health_restore_speed', 'power_restore_speed', 'burn_immunity', 'shock_immunity', 'strike_immunity'}
out = []
for name in sorted(sections):
    if not name.startswith('af_') or 'absorbation' in name or get(name, 'hit_absorbation_sect') is None:
        continue
    if get(name, 'class') not in (None, 'ARTEFACT', 'SCRPTART') and get(name, 'class'):
        pass
    fx = {}
    for k in RESTORE + OTHER:
        v = num(get(name, k))
        if abs(v) > 1e-9: fx[k] = v
    absec = (get(name, 'hit_absorbation_sect') or '').lower()
    for k in IMM:
        v = num(get(absec, k))
        if abs(v) > 1e-9: fx[k] = v
    out.append({'weight': get(name, 'inv_weight'), 'light': get(name, 'trail_light_color'), 'range': get(name, 'trail_light_range'), 'id': name, 'visual': get(name, 'visual'), 'tier': get(name, 'af_rank') or get(name, 'tier'),
                'name': get(name, 'inv_name'), 'cost': num(get(name, 'cost')), 'fx': fx})
json.dump(out, open(sys.argv[3] if len(sys.argv) > 3 else '/dev/stdout', 'w'), indent=1)
