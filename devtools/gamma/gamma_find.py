"""Winning GAMMA file for a gamedata path: mods by MO2 priority (top first), else the base archives (extracted
under <base>). Usage: gamma_find.py <gamma dir> <base dir> <rel path>..."""
import os, sys
gamma, base = sys.argv[1], sys.argv[2]
order = [l.strip()[1:] for l in open(os.path.join(gamma, 'profiles/G.A.M.M.A/modlist.txt'), encoding='latin-1')
         if l.strip().startswith('+')]
def find(rel):
    rel = rel.replace('\\', '/').lower()
    for mod in order:
        root = os.path.join(gamma, 'mods', mod, 'gamedata')
        p = os.path.join(root, rel)
        if os.path.exists(p):
            return mod, p
        # case-insensitive fallback
        d = root
        ok = True
        for part in rel.split('/'):
            if not os.path.isdir(d) and not os.path.isfile(d):
                ok = False; break
            try:
                names = {n.lower(): n for n in os.listdir(d)}
            except NotADirectoryError:
                ok = False; break
            if part not in names:
                ok = False; break
            d = os.path.join(d, names[part])
        if ok and os.path.isfile(d):
            return mod, d
    p = os.path.join(base, rel)
    return ('base', p) if os.path.exists(p) else (None, None)
for rel in sys.argv[3:]:
    m, p = find(rel)
    print(f'{rel}\t{m}\t{p}')
