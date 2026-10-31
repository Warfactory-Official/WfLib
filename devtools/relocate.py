#!/usr/bin/env python3
"""Applies docs/relocations.tsv to a repo's Java/resource sources. Subcommands: apply, verify, census, transplant."""
import argparse
import os
import re
import shutil
import subprocess
import sys
import tempfile
from collections import defaultdict

DEFAULT_TSV = os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', 'docs', 'relocations.tsv')
RESOURCE_EXT = ('.java', '.json', '.mcmeta', '.toml')
IDENT = r'[\w$]'


class Row:
    def __init__(self, piece, kind, old, new):
        self.piece, self.kind, self.old, self.new = piece, kind, old, new


def load_rows(tsv, pieces=None):
    rows = []
    with open(tsv, encoding='utf-8') as f:
        for n, line in enumerate(f):
            line = line.rstrip('\n')
            if n == 0 or not line.strip() or line.startswith('#'):
                continue
            parts = line.split('\t')
            if len(parts) != 4:
                raise SystemExit(f'{tsv}:{n + 1}: expected 4 tab-separated columns')
            if pieces is None or parts[0] in pieces:
                rows.append(Row(*parts))
    return rows


def source_files(root, exts=('.java',)):
    out = []
    for d, dirs, files in os.walk(os.path.join(root, 'src')):
        out.extend(os.path.join(d, f) for f in files if f.endswith(exts))
    return sorted(out)


def read(path):
    with open(path, encoding='utf-8', newline='') as f:
        return f.read()


def write(path, text):
    with open(path, 'w', encoding='utf-8', newline='') as f:
        f.write(text)


def mask(text, strings=True):
    """Blank comments (and string/char literal contents) keeping length and newlines."""
    out = list(text)
    i, n = 0, len(text)

    def blank(a, b):
        for k in range(a, b):
            if out[k] != '\n':
                out[k] = ' '

    while i < n:
        c = text[i]
        if text.startswith('//', i):
            j = text.find('\n', i)
            j = n if j < 0 else j
            blank(i, j)
            i = j
        elif text.startswith('/*', i):
            j = text.find('*/', i + 2)
            j = n if j < 0 else j + 2
            blank(i, j)
            i = j
        elif text.startswith('"""', i):
            j = text.find('"""', i + 3)
            while j > 0 and text[j - 1] == '\\':
                j = text.find('"""', j + 1)
            j = n if j < 0 else j + 3
            if strings:
                blank(i + 3, max(i + 3, j - 3))
            i = j
        elif c in '"\'':
            j = i + 1
            while j < n and text[j] != c and text[j] != '\n':
                j += 2 if text[j] == '\\' else 1
            if strings:
                blank(i + 1, min(j, n))
            i = j + 1
        else:
            i += 1
    return ''.join(out)


PKG_RE = re.compile(r'^[ \t]*package\s+([\w.]+)\s*;', re.M)
IMPORT_RE = re.compile(r'^import\s+(static\s+)?([\w.$]+(?:\.\*)?)\s*;[ \t]*\r?\n?', re.M)
TOP_RE = re.compile(r'^(?:(?:public|final|abstract|sealed|non-sealed|strictfp)\s+)*(?:class|interface|enum|record|@interface)\s+(\w+)', re.M)


def package_of(text):
    m = PKG_RE.search(text)
    return m.group(1) if m else ''


def split_fqcn(fq):
    pkg, _, simple = fq.rpartition('.')
    return pkg, simple


def code_only(text):
    """Code with comments, literals, package and import lines blanked."""
    m = mask(text)
    return re.sub(r'^[ \t]*(?:package|import)\b[^;\n]*;', lambda x: ' ' * len(x.group(0)), m, flags=re.M)


def uses_simple(code, simple):
    return re.search(r'(?<![\w$.])' + re.escape(simple) + r'(?![\w$])', code) is not None


def declares(code, simple):
    return re.search(r'\b(?:class|interface|enum|record)\s+' + re.escape(simple) + r'\b', code) is not None


class Move:
    def __init__(self, old, new):
        self.old, self.new = old, new
        self.opkg, self.osimple = split_fqcn(old)
        self.npkg, self.nsimple = split_fqcn(new)


def fq_alternation(names):
    names = sorted(names, key=len, reverse=True)
    return re.compile(r'(?<![\w$.])(' + '|'.join(re.escape(n) for n in names) + r')(?![\w$])')


def simple_token(simple):
    return re.compile(r'(?<![\w$.])' + re.escape(simple) + r'(?![\w$])')


def rename_applies(m, pkg, own, imports, code):
    """Simple-name uses of moved class `m` can occur in this file."""
    if own is m:
        return True
    explicit = {i for s, i in imports if not s}
    if any((i.rpartition('.')[2] == m.osimple and i != m.old) for i in explicit):
        return False
    if pkg == m.opkg:
        return not declares(code, m.osimple)
    if any(i == m.old or i.startswith(m.old + '.') for i in explicit):
        return True
    return f'{m.opkg}.*' in explicit


def add_imports(text, pkg, imports):
    existing = {(m.group(1) or '') + m.group(2) for m in IMPORT_RE.finditer(text)}
    new = sorted(i for i in set(imports) if i not in existing)
    if not new:
        return text
    block = ''.join(f'import {i};\n' for i in new)
    nl = '\r\n' if '\r\n' in text else '\n'
    block = block.replace('\n', nl)
    plain = [m for m in IMPORT_RE.finditer(text) if not m.group(1)]
    anyimp = list(IMPORT_RE.finditer(text))
    if plain:
        at = plain[-1].end()
        if not text[:at].endswith('\n'):
            block = nl + block
            at = plain[-1].end()
        return text[:at] + block + text[at:]
    if anyimp:
        at = anyimp[0].start()
        return text[:at] + block + nl + text[at:]
    m = PKG_RE.search(text)
    if m:
        eol = text.find('\n', m.end())
        eol = len(text) if eol < 0 else eol + 1
        return text[:eol] + nl + block + text[eol:]
    return block + nl + text


def dedupe_imports(text):
    seen = set()

    def keep(m):
        key = (bool(m.group(1)), m.group(2))
        if key in seen:
            return ''
        seen.add(key)
        return m.group(0)
    return IMPORT_RE.sub(keep, text)


def package_root_present(npkg, pkgs):
    head = '.'.join(npkg.split('.')[:3])
    return any(p == head or p.startswith(head + '.') for p in pkgs)


TEXT_SKIP_DIRS = {'.git', 'build', '.gradle', 'node_modules', '__pycache__'}


def rewrite_extra_roots(dirs, moves, dry_run):
    """Dotted FQCN strings in non-Java text under `dirs`; returns changed paths."""
    if not moves:
        return []
    pat = fq_alternation(list(moves))
    changed = []
    for top in dirs:
        for d, subdirs, files in os.walk(top):
            subdirs[:] = [x for x in subdirs if x not in TEXT_SKIP_DIRS]
            for f in files:
                if f.endswith('.java'):
                    continue
                path = os.path.join(d, f)
                try:
                    t = read(path)
                except (UnicodeDecodeError, OSError):
                    continue
                t2 = pat.sub(lambda m: moves[m.group(1)].new, t)
                if t2 != t:
                    changed.append(path)
                    if not dry_run:
                        write(path, t2)
    return changed


def cmd_apply(args):
    root = os.path.abspath(args.repo_root)
    rows = load_rows(args.tsv, set(args.pieces.split(',')))
    res = run_apply(root, rows, args)
    extra = rewrite_extra_roots([os.path.abspath(d) for d in args.extra_root],
                                {r.old: Move(r.old, r.new) for r in rows if r.kind == 'class'}, args.dry_run)
    changed = res['changed']
    for k in ('class', 'member', 'resource', 'id', 'store'):
        print(f'{k}: {len(changed[k])} files changed')
    manual_rows = [r for r in rows if r.kind == 'manual']
    if manual_rows:
        print('hand edits (call shape changes; apply skips, verify reports remaining hits):')
        for r in manual_rows:
            print(f'  {r.old} -> {r.new}')
    print(f'extra roots: {len(extra)} files changed')
    print(f'files moved: {len(res["moved"])}; imports added: {res["stats"]["imports added"]}')
    if res['manual']:
        print('manual fix:')
        for m in res['manual']:
            print('  ' + m)


def run_apply(root, rows, args, git=True, guard=True):
    class_rows = [r for r in rows if r.kind == 'class']
    member_rows = [r for r in rows if r.kind == 'member']
    str_rows = [r for r in rows if r.kind in ('resource', 'id')]
    moves = {r.old: Move(r.old, r.new) for r in class_rows}
    files = source_files(root)
    texts = {p: read(p) for p in files}
    pkgs = {p: package_of(t) for p, t in texts.items()}
    by_pkg = defaultdict(set)
    fq_path = {}
    for p, t in texts.items():
        names = {os.path.basename(p)[:-5]} | set(TOP_RE.findall(t))
        for n in names:
            by_pkg[pkgs[p]].add(n)
        fq_path.setdefault(f'{pkgs[p]}.{os.path.basename(p)[:-5]}', p)
    moved_files = {fq_path[o]: m for o, m in moves.items() if o in fq_path}
    manual = []
    if guard:
        present = set(pkgs.values())
        for path, m in list(moved_files.items()):
            if not package_root_present(m.npkg, present):
                del moved_files[path]
                manual.append(f'{os.path.relpath(path, root)}: move skipped, package root of {m.new} absent; refs rewritten only')
    changed = defaultdict(set)
    stats = defaultdict(int)

    def eff(fq):
        m = moves.get(fq)
        return m.new if m else fq

    cls_re = fq_alternation(list(moves)) if moves else None
    results = {}
    src_root = os.path.join(os.path.dirname(os.path.abspath(args.tsv)), '..', 'src', 'main', 'java')
    moved_simple = defaultdict(set)
    for m in moves.values():
        moved_simple[m.opkg].add(m.osimple)

    def vacated_pkg(pkg):
        d = os.path.join(src_root, *pkg.split('.'))
        if os.path.isdir(d) and any(f.endswith('.java') for f in os.listdir(d)):
            return False
        return not (by_pkg.get(pkg, set()) - moved_simple[pkg])

    vacated = {m.opkg for m in moves.values() if vacated_pkg(m.opkg)}

    for path in files:
        text = orig = texts[path]
        pkg = pkgs[path]
        own = moved_files.get(path)
        eff_pkg = own.npkg if own else pkg
        rel = os.path.relpath(path, root)

        for r in member_rows:
            oc, name = r.old.split('#')
            nc, expr = r.new.split('#')
            opkg, osimple = split_fqcn(oc)
            npkg, nsimple = split_fqcn(nc)
            imported = re.search(r'^import\s+' + re.escape(oc) + r'\s*;', text, re.M) is not None \
                or re.search(r'^import\s+' + re.escape(opkg) + r'\.\*\s*;', text, re.M) is not None
            simple_scope = imported or pkg == opkg
            hit_static = False
            sm = re.search(r'^import\s+static\s+' + re.escape(oc) + r'\.' + re.escape(name) + r'\s*;', text, re.M)
            if sm:
                if re.fullmatch(r'[\w$]+', expr):
                    text = text[:sm.start()] + f'import static {nc}.{expr};' + text[sm.end():]
                    if expr != name:
                        manual.append(f'{rel}: bare uses of {name} => {expr}')
                else:
                    manual.append(f'{rel}: static import of {r.old} needs manual fix ({expr})')
                hit_static = True
            before = text
            text = re.sub(r'(?<![\w$.])' + re.escape(oc) + r'\.' + re.escape(name) + r'(?![\w$])',
                          lambda _: f'{nc}.{expr}', text)
            hit = hit_static or text != before
            if simple_scope:
                code = code_only(text)
                pat = re.compile(r'(?<![\w$.])' + re.escape(osimple) + r'\.' + re.escape(name) + r'(?![\w$])')
                if pat.search(code):
                    spans = [m.span() for m in pat.finditer(code)]
                    for a, b in reversed(spans):
                        text = text[:a] + f'{nsimple}.{expr}' + text[b:]
                    hit = True
                    if npkg != pkg and nc != oc:
                        text = add_imports(text, pkg, [nc])
                        if not uses_simple(code_only(text), osimple):
                            text = re.sub(r'^import\s+' + re.escape(oc) + r'\s*;[ \t]*\r?\n?', '', text, flags=re.M)
                    elif npkg != pkg:
                        text = add_imports(text, pkg, [nc])
            if hit:
                changed['member'].add(path)

        if cls_re:
            renames = [m for m in moves.values() if m.osimple != m.nsimple]
            if renames:
                code0 = code_only(text)
                imps0 = [(m.group(1), m.group(2)) for m in IMPORT_RE.finditer(text)]
                spans = {}
                for m in renames:
                    if rename_applies(m, pkg, own, imps0, code0):
                        for x in simple_token(m.osimple).finditer(code0):
                            spans[x.start()] = (x.end(), m.nsimple)
                for a in sorted(spans, reverse=True):
                    b, repl = spans[a]
                    text = text[:a] + repl + text[b:]
                if spans:
                    changed['class'].add(path)
            t2 = cls_re.sub(lambda m: moves[m.group(1)].new, text)
            if t2 != text:
                changed['class'].add(path)
                text = t2
            code = code_only(text)
            imports = [(m.group(1), m.group(2)) for m in IMPORT_RE.finditer(text)]
            wild = {i[:-2] for s, i in imports if not s and i.endswith('.*')}
            imported_simple = {i.rpartition('.')[2] for s, i in imports if not s}
            cands = []
            if own:
                cands = [f'{pkg}.{n}' for n in by_pkg[pkg]] + [o for o, m in moves.items() if m.opkg in wild and m.opkg != pkg]
            else:
                cands = [o for o, m in moves.items() if m.opkg == pkg or m.opkg in wild]
            need = []
            for ofq in cands:
                if own and ofq == own.old:
                    continue
                if not own and ofq == f'{pkg}.{os.path.basename(path)[:-5]}':
                    continue
                opk, osm = split_fqcn(ofq)
                efq = eff(ofq)
                epkg, esimple = split_fqcn(efq)
                if epkg == eff_pkg:
                    continue
                if epkg in wild or esimple in imported_simple or declares(code, esimple) or not uses_simple(code, esimple):
                    continue
                need.append(efq)
            if need:
                text = add_imports(text, pkg, need)
                changed['class'].add(path)
                stats['imports added'] += len(need)
            def drop(m):
                if not m.group(1) and m.group(2).endswith('.*'):
                    wp = m.group(2)[:-2]
                    return '' if wp in vacated else m.group(0)
                if m.group(1) or m.group(2).endswith('.*'):
                    return m.group(0)
                p, _, s = m.group(2).rpartition('.')
                return '' if p == eff_pkg and s[:1].isupper() else m.group(0)
            t3 = IMPORT_RE.sub(drop, text)
            if t3 != text:
                text = t3
                changed['class'].add(path)
            touched = set()
            for m in moves.values():
                touched |= {m.old, m.new}
            body = IMPORT_RE.sub('', text)

            def unused(m):
                fq = m.group(2)
                if m.group(1) or fq.endswith('.*') or not any(fq == t or fq.startswith(t + '.') for t in touched):
                    return m.group(0)
                return m.group(0) if simple_token(fq.rpartition('.')[2]).search(body) else ''
            t4 = IMPORT_RE.sub(unused, text)
            if t4 != text:
                text = t4
                changed['class'].add(path)
            if own:
                text = PKG_RE.sub(lambda m: m.group(0).replace(pkg, own.npkg, 1), text, count=1)
                changed['class'].add(path)

        text = dedupe_imports(text)
        if text != orig:
            results[path] = text

    moved_to = {}
    for path, m in moved_files.items():
        d = os.path.dirname(path)
        base = os.path.join(*([d] + ['..'] * len(m.opkg.split('.'))))
        newdir = os.path.normpath(os.path.join(base, *m.npkg.split('.')))
        moved_to[path] = os.path.join(newdir, m.nsimple + '.java')

    for path, text in results.items():
        dest = moved_to.get(path)
        if args.dry_run:
            print(f'would change {os.path.relpath(path, root)}' + (f' -> {os.path.relpath(dest, root)}' if dest else ''))
            continue
        if dest:
            os.makedirs(os.path.dirname(dest), exist_ok=True)
            if not git or subprocess.run(['git', '-C', root, 'mv', path, dest], capture_output=True).returncode != 0:
                os.rename(path, dest)
            write(dest, text)
        else:
            write(path, text)
    for path, dest in moved_to.items():
        if path not in results:
            manual.append(f'{os.path.relpath(path, root)}: moved file had no content change')

    if str_rows:
        mapping = {r.old: r for r in str_rows}
        pat = re.compile('|'.join(re.escape(o) for o in sorted(mapping, key=len, reverse=True)))
        for path in source_files(root, RESOURCE_EXT):
            t = read(path)
            kinds = set()
            def sub(m):
                kinds.add(mapping[m.group(0)].kind)
                return mapping[m.group(0)].new
            t2 = pat.sub(sub, t)
            if t2 != t:
                for k in kinds:
                    changed[k].add(path)
                if not args.dry_run:
                    write(path, t2)
                else:
                    print(f'would change {os.path.relpath(path, root)}')

    if moves:
        store_re = re.compile('(' + '|'.join(re.escape(o) for o in sorted(moves, key=len, reverse=True)) + r')(?![\w])')
        store = os.path.join(root, 'src', 'test', 'resources', 'archunit_store')
        for d, _, files in os.walk(store):
            for f in files:
                path = os.path.join(d, f)
                t = read(path)
                t2 = store_re.sub(lambda m: moves[m.group(1)].new, t)
                if t2 != t:
                    changed['store'].add(path)
                    if args.dry_run:
                        print(f'would change {os.path.relpath(path, root)}')
                    else:
                        write(path, t2)

    return {'changed': changed, 'stats': stats, 'manual': manual, 'moves': moves,
            'moved': [(m, p, moved_to[p]) for p, m in moved_files.items()]}


def new_call_guard(r, name):
    """New form = old member name as a call (field -> accessor): exclude `name(` so the migrated site is not a hit."""
    head = r.new.split()[0] if r.new.split() else ''
    m = re.match(r'(?:[\w.$]+[#.])?([\w$]+)\(', head)
    return r'(?!\s*\()' if m and m.group(1) == name else ''


def find_hits(root, rows, exclude=(), renames=None):
    """renames: old FQCN -> new FQCN of class rows; a renamed owner is also searched under its new simple name."""
    renames = renames or {}
    hits = []
    for path in source_files(root, RESOURCE_EXT):
        if path in exclude:
            continue
        text = read(path)
        lines = text.split('\n')
        is_java = path.endswith('.java')
        rel = os.path.relpath(path, root)
        pkg = package_of(text) if is_java else ''
        for r in rows:
            if r.kind == 'class' and is_java:
                pat = re.compile(r'(?<![\w$.])' + re.escape(r.old) + r'(?![\w$])')
            elif r.kind == 'manual' and is_java and '#' not in r.old:
                pat = re.compile(r'(?<![\w$.])' + re.escape(r.old) + r'(?![\w$])')
            elif r.kind in ('member', 'manual') and is_java:
                oc, name = r.old.split('#')
                opkg, osimple = split_fqcn(oc)
                if r.kind == 'manual':
                    alt = re.escape(oc) + r'|' + re.escape(osimple)
                    if oc in renames:
                        alt += r'|' + re.escape(split_fqcn(renames[oc])[1])
                else:
                    scoped = pkg == opkg or re.search(r'^import\s+' + re.escape(oc) + r'\s*;', text, re.M) \
                        or re.search(r'^import\s+' + re.escape(opkg) + r'\.\*\s*;', text, re.M)
                    alt = re.escape(oc) + (r'|' + re.escape(osimple) if scoped else '')
                pat = re.compile(r'(?<![\w$.])(?:' + alt + r')\.' + re.escape(name) + r'(?![\w$])' + new_call_guard(r, name))
            elif r.kind in ('resource', 'id'):
                pat = re.compile(re.escape(r.old))
            else:
                continue
            for i, line in enumerate(lines, 1):
                if pat.search(line):
                    hits.append(f'{rel}:{i}: {r.kind} {r.old}')
    return hits


def declared_classes(root):
    """FQCNs the tree declares now: a `class` row whose old name is declared again beside its new one (an entry replacing a renamed facade) is not a leftover."""
    out = set()
    for path in source_files(root):
        text = read(path)
        pkg = package_of(text)
        out.update(f'{pkg}.{n}' for n in set(TOP_RE.findall(text)))
    return out


def cmd_verify(args):
    root = os.path.abspath(args.repo_root)
    rows = load_rows(args.tsv, set(args.pieces.split(',')) if args.pieces else None)
    renames = {r.old: r.new for r in load_rows(args.tsv) if r.kind == 'class'}
    declared = declared_classes(root)
    rows = [r for r in rows if not (r.kind == 'class' and r.old in declared and r.new in declared)]
    hits = find_hits(root, rows, renames=renames)
    for h in hits:
        print(h)
    return 1 if hits else 0


def implicit_hits(root, moves, exclude):
    """Same-package or wildcard-import simple-name uses of moved classes."""
    hits = []
    for path in source_files(root):
        if path in exclude:
            continue
        text = read(path)
        pkg = package_of(text)
        code = code_only(text)
        imps = {m.group(2) for m in IMPORT_RE.finditer(text) if not m.group(1)}
        wild = {i[:-2] for i in imps if i.endswith('.*')}
        for m in moves:
            if m.new in imps or m.npkg in wild:
                continue
            if (pkg == m.opkg or m.opkg in wild) and not declares(code, m.osimple) and uses_simple(code, m.osimple):
                hits.append(f'{os.path.relpath(path, root)}: implicit use of {m.old}')
    return hits


def git_out(repo, *argv):
    r = subprocess.run(['git', '-C', repo, *argv], capture_output=True, text=True)
    if r.returncode != 0:
        raise SystemExit(f'git {" ".join(argv)} failed in {repo}: {r.stderr.strip()}')
    return r.stdout.strip()


def cmd_transplant(args):
    src, dst = os.path.abspath(args.src), os.path.abspath(args.dst)
    rows = [r for r in load_rows(args.tsv, set(args.pieces.split(','))) if r.kind == 'class']
    row_of = {r.old: r for r in rows}
    tmp = tempfile.mkdtemp()
    try:
        for p in source_files(src):
            q = os.path.join(tmp, os.path.relpath(p, src))
            os.makedirs(os.path.dirname(q), exist_ok=True)
            shutil.copyfile(p, q)
        res = run_apply(tmp, rows, argparse.Namespace(tsv=args.tsv, dry_run=False), git=False, guard=False)
        sha = git_out(src, 'rev-parse', '--short=12', 'HEAD')
        repo = os.path.basename(src)
        copied, origins, gone = [], [], []
        for m, told, tnew in res['moved']:
            rel_old, rel_new = os.path.relpath(told, tmp), os.path.relpath(tnew, tmp)
            gone.append((m, os.path.join(src, rel_old)))
            target = os.path.join(dst, rel_new)
            if os.path.exists(target):
                print(f'exists, not overwritten: {rel_new}')
                continue
            copied.append(target)
            origins.append((row_of[m.old].piece, m.new, f'{repo}@{sha}:{rel_old.replace(os.sep, "/")}'))
            if not args.dry_run:
                os.makedirs(os.path.dirname(target), exist_ok=True)
                write(target, read(tnew))
            else:
                print(f'would copy {rel_old} -> {rel_new}')
        for note in res['manual']:
            print('  ' + note)
        if not args.dry_run:
            if copied:
                git_out(dst, 'add', '--', *copied)
            known = {(r.piece, r.new) for r in load_rows(args.tsv) if r.kind == 'origin'}
            fresh = [o for o in origins if (o[0], o[1]) not in known]
            if fresh:
                tsv_text = read(args.tsv)
                tail = '' if tsv_text.endswith('\n') or not tsv_text else '\n'
                write(args.tsv, tsv_text + tail + ''.join(f'{p}\torigin\t{n}\t{o}\n' for p, n, o in fresh))
        moves_map = {r.old: Move(r.old, r.new) for r in rows}
        extra = rewrite_extra_roots([os.path.abspath(d) for d in args.extra_root], moves_map, args.dry_run)
        print(f'copied: {len(copied)}; extra roots: {len(extra)} files changed')
        if args.keep_src:
            return 0
        exclude = {p for _, p in gone}
        gone_rows = [Row(row_of[m.old].piece, 'class', m.old, m.new) for m, _ in gone]
        hits = find_hits(src, gone_rows, exclude) + implicit_hits(src, [m for m, _ in gone], exclude)
        if hits:
            print('source kept, references remain:')
            for h in hits:
                print('  ' + h)
            return 1
        if not args.dry_run:
            for _, p in gone:
                git_out(src, 'rm', '-q', '-f', '--', p)
        print(f'source removed: {len(gone)}')
        return 0
    finally:
        shutil.rmtree(tmp)


def cmd_census(args):
    root = os.path.abspath(args.repo_root)
    pat = re.compile(r'(?<![\w$.])com\.wf\.wflib\.((?:[a-z_]\w*\.)*)([A-Z]\w*)')
    refs = defaultdict(set)
    for path in source_files(root):
        for m in pat.finditer(mask(read(path), strings=False)):
            refs[(m.group(1).rstrip('.'), m.group(2))].add(path)
    print('type\tfiles\tapi')
    for (pkg, t), fs in sorted(refs.items()):
        api = 'api' in pkg.split('.')
        print(f'com.wf.wflib.{pkg + "." if pkg else ""}{t}\t{len(fs)}\t{"api" if api else "NON_API"}')


def main(argv=None):
    ap = argparse.ArgumentParser(description=__doc__)
    ap.add_argument('--tsv', default=DEFAULT_TSV)
    common = argparse.ArgumentParser(add_help=False)
    common.add_argument('--tsv', default=argparse.SUPPRESS)
    sub = ap.add_subparsers(dest='cmd', required=True)
    a = sub.add_parser('apply', parents=[common])
    a.add_argument('--pieces', required=True)
    a.add_argument('--dry-run', action='store_true')
    a.add_argument('--extra-root', action='append', default=[], metavar='DIR')
    a.add_argument('repo_root')
    a.set_defaults(fn=cmd_apply)
    t = sub.add_parser('transplant', parents=[common])
    t.add_argument('--pieces', required=True)
    t.add_argument('--src', required=True)
    t.add_argument('--dst', required=True)
    t.add_argument('--keep-src', action='store_true')
    t.add_argument('--dry-run', action='store_true')
    t.add_argument('--extra-root', action='append', default=[], metavar='DIR')
    t.set_defaults(fn=cmd_transplant)
    v = sub.add_parser('verify', parents=[common])
    v.add_argument('--pieces')
    v.add_argument('repo_root')
    v.set_defaults(fn=cmd_verify)
    c = sub.add_parser('census', parents=[common])
    c.add_argument('repo_root')
    c.set_defaults(fn=cmd_census)
    args = ap.parse_args(argv)
    return args.fn(args) or 0


if __name__ == '__main__':
    sys.exit(main())
