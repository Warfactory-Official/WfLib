import io
import os
import shutil
import subprocess
import sys
import tempfile
import unittest
from contextlib import redirect_stdout

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import relocate

W = 'com/wf/wflib'

FILES = {
    f'src/main/java/{W}/util/OBB.java': '''package com.wf.wflib.util;

import java.util.List;

public class OBB {
    public static final int K = 1;
    public static class Hit {}
    Helper h;
}
''',
    f'src/main/java/{W}/util/OBBX.java': 'package com.wf.wflib.util;\n\npublic class OBBX {}\n',
    f'src/main/java/{W}/util/Helper.java': 'package com.wf.wflib.util;\n\nclass Helper {}\n',
    f'src/main/java/{W}/util/Sibling.java': '''package com.wf.wflib.util;

public class Sibling {
    OBB a; // OBB in comment only elsewhere
    OBB.Hit hit;
}
''',
    f'src/main/java/{W}/util/Unrelated.java': '''package com.wf.wflib.util;

/** OBB mentioned only in javadoc. */
public class Unrelated {
    String s = "OBB";
}
''',
    f'src/main/java/{W}/other/User.java': '''package com.wf.wflib.other;

import com.wf.wflib.util.OBB;
import com.wf.wflib.util.OBB.Hit;
import com.wf.wflib.util.OBBX;
import static com.wf.wflib.util.OBB.K;

public class User {
    com.wf.wflib.util.OBB q;
    String refl = "com.wf.wflib.util.OBB";
    String keep = "com.wf.wflib.util.OBBX";
    int v = OBB.K + K;
    int w = Sources.add(1);
}
''',
    f'src/main/java/{W}/other/Sources.java': '''package com.wf.wflib.other;

public class Sources {
    public static int add(int x) { return x; }
}
''',
    f'src/main/java/{W}/other/api/Registry.java': '''package com.wf.wflib.other.api;

public class Registry {
    public static final Registry SOURCES = new Registry();
    public int register(int x) { return x; }
}
''',
    'src/main/resources/data/wflib/x.json': '{"tex": "wflib:textures/x.png"}\n',
}

TSV = (
    'piece\tkind\told\tnew\n'
    'A1\tclass\tcom.wf.wflib.util.OBB\tcom.wf.wflib.geom.api.OBB\n'
    'A1\tclass\tcom.wf.wflib.util.OBBX\tcom.wf.wflib.geom.api.OBBX\n'
    'A1\tmember\tcom.wf.wflib.other.Sources#add\tcom.wf.wflib.other.api.Registry#SOURCES.register\n'
    'A1\tresource\twflib:textures/x.png\twflib:textures/geom/x.png\n'
    'A2\tclass\tcom.wf.wflib.util.Sibling\tcom.wf.wflib.zzz.Sibling\n'
)

DEP = {
    'src/main/java/dep/Dep.java': '''package dep;

import com.wf.wflib.geom.api.OBB;
import com.wf.wflib.util.Helper;
import com.wf.wflib.recon.api.Foo;
import static com.wf.wflib.recon.Bar.baz;

class Dep { Object o = com.wf.wflib.mine.Mine.X; }
''',
}


def run(*argv):
    out = io.StringIO()
    with redirect_stdout(out):
        rc = relocate.main(list(argv))
    return rc, out.getvalue()


class Base(unittest.TestCase):
    def tearDown(self):
        shutil.rmtree(self.tmp)

    def write_tree(self, root, files):
        for p, t in files.items():
            full = os.path.join(root, p)
            os.makedirs(os.path.dirname(full), exist_ok=True)
            with open(full, 'w') as f:
                f.write(t)

    def read(self, rel):
        with open(os.path.join(self.root, rel)) as f:
            return f.read()

    def apply(self, pieces='A1', *extra):
        return run('--tsv', self.tsv, 'apply', '--pieces', pieces, *extra, self.root)


class RelocateTest(Base):
    def setUp(self):
        self.tmp = tempfile.mkdtemp()
        self.root = os.path.join(self.tmp, 'repo')
        self.tsv = os.path.join(self.tmp, 'r.tsv')
        with open(self.tsv, 'w') as f:
            f.write(TSV)
        self.write_tree(self.root, FILES)
        subprocess.run(['git', 'init', '-q', self.root], check=True)
        subprocess.run(['git', '-C', self.root, 'add', '-A'], check=True)

    def test_apply(self):
        rc, out = self.apply()
        self.assertEqual(rc, 0, out)
        moved = f'src/main/java/{W}/geom/api/OBB.java'
        self.assertFalse(os.path.exists(os.path.join(self.root, f'src/main/java/{W}/util/OBB.java')))
        obb = self.read(moved)
        self.assertIn('package com.wf.wflib.geom.api;', obb)
        self.assertIn('import com.wf.wflib.util.Helper;', obb)
        self.assertNotIn('import com.wf.wflib.util.OBBX', obb)
        st = subprocess.run(['git', '-C', self.root, 'status', '--porcelain'], capture_output=True, text=True).stdout
        self.assertIn('R', st.split('\n')[0] + st)
        self.assertIn('package com.wf.wflib.geom.api;', self.read(f'src/main/java/{W}/geom/api/OBBX.java'))

        user = self.read(f'src/main/java/{W}/other/User.java')
        self.assertIn('import com.wf.wflib.geom.api.OBB;', user)
        self.assertNotIn('OBB.Hit', user)
        self.assertNotIn('import com.wf.wflib.geom.api.OBBX;', user)
        self.assertIn('import static com.wf.wflib.geom.api.OBB.K;', user)
        self.assertIn('com.wf.wflib.geom.api.OBB q;', user)
        self.assertIn('"com.wf.wflib.geom.api.OBB"', user)
        self.assertIn('"com.wf.wflib.geom.api.OBBX"', user)
        self.assertNotIn('com.wf.wflib.util', user)
        self.assertIn('Registry.SOURCES.register(1)', user)
        self.assertIn('import com.wf.wflib.other.api.Registry;', user)
        self.assertEqual(user.count('import com.wf.wflib.other.api.Registry;'), 1)

        sib = self.read(f'src/main/java/{W}/util/Sibling.java')
        self.assertIn('import com.wf.wflib.geom.api.OBB;', sib)
        unrelated = self.read(f'src/main/java/{W}/util/Unrelated.java')
        self.assertNotIn('import', unrelated)
        self.assertIn('"OBB"', unrelated)
        self.assertIn('textures/geom/x.png', self.read('src/main/resources/data/wflib/x.json'))

    def test_idempotent_and_verify(self):
        self.apply()
        rc, out = run('--tsv', self.tsv, 'verify', self.root)
        self.assertEqual(rc, 0, out)
        before = self.read(f'src/main/java/{W}/other/User.java')
        self.apply()
        self.assertEqual(before, self.read(f'src/main/java/{W}/other/User.java'))

    def test_verify_finds_leftovers(self):
        rc, out = run('--tsv', self.tsv, 'verify', self.root)
        self.assertEqual(rc, 1)
        self.assertIn('User.java:3: class com.wf.wflib.util.OBB', out)
        self.assertNotIn('OBBX.java', out.replace('class com.wf.wflib.util.OBBX', ''))
        self.assertIn('x.json:1: resource', out)
        self.assertIn('Sources.add' if False else 'member', out)

    def test_dry_run_changes_nothing(self):
        rc, out = self.apply('A1', '--dry-run')
        self.assertIn('would change', out)
        self.assertTrue(os.path.exists(os.path.join(self.root, f'src/main/java/{W}/util/OBB.java')))
        self.assertIn('com.wf.wflib.util.OBB;', self.read(f'src/main/java/{W}/other/User.java'))

    def test_piece_selection_and_cross_move(self):
        rc, out = self.apply('A1,A2')
        sib = self.read(f'src/main/java/{W}/zzz/Sibling.java')
        self.assertIn('package com.wf.wflib.zzz;', sib)
        self.assertIn('import com.wf.wflib.geom.api.OBB;', sib)
        self.assertNotIn('import com.wf.wflib.util.', sib)

    def test_static_member_import(self):
        self.write_tree(self.root, {
            f'src/main/java/{W}/other/S.java':
                'package com.wf.wflib.other.x;\n\nimport static com.wf.wflib.other.Sources.add;\n\nclass S {}\n'})
        rc, out = self.apply()
        self.assertIn('S.java', out)
        self.assertIn('needs manual fix', out)

    def test_census(self):
        dep = os.path.join(self.tmp, 'dep')
        self.write_tree(dep, DEP)
        rc, out = run('census', dep)
        rows = {l.split('\t')[0]: l.split('\t') for l in out.strip().split('\n')[1:]}
        self.assertEqual(rows['com.wf.wflib.geom.api.OBB'][2], 'api')
        self.assertEqual(rows['com.wf.wflib.util.Helper'][2], 'NON_API')
        self.assertEqual(rows['com.wf.wflib.recon.Bar'][2], 'NON_API')
        self.assertEqual(rows['com.wf.wflib.mine.Mine'][1], '1')
        self.assertEqual(rows['com.wf.wflib.recon.api.Foo'][2], 'api')


RENAME_FILES = {
    f'src/main/java/{W}/a/Old.java': '''package com.wf.wflib.a;

public class Old {
    public Old() {}
    public static class Inner {}
    static Old make() { return new Old(); }
    String s = "Old";
}
''',
    f'src/main/java/{W}/a/Neighbor.java': 'package com.wf.wflib.a;\n\nclass Neighbor {\n    Old o; Old.Inner i;\n}\n',
    f'src/main/java/{W}/b/Importer.java': '''package com.wf.wflib.b;

import com.wf.wflib.a.Old;
import com.wf.wflib.a.Old.Inner;

class Importer {
    Old o = new Old(); Inner i; Old.Inner j;
    String s = "com.wf.wflib.a.Old";
}
''',
    f'src/main/java/{W}/b/Wild.java': '''package com.wf.wflib.b;

import com.wf.wflib.a.*;
import java.util.List;

class Wild {
    Old o; List<Neighbor> l;
}
''',
    f'src/main/java/{W}/b/WildUnused.java': '''package com.wf.wflib.b;

import com.wf.wflib.a.*;

class WildUnused {
    Neighbor n;
}
''',
    f'src/main/java/{W}/b/Other.java': 'package com.wf.wflib.b;\n\nclass Other { class Old {} Old o; }\n',
    f'src/main/java/{W}/b/Dead.java': '''package com.wf.wflib.b;

import com.wf.wflib.a.Old;
import java.util.List;

class Dead { List<String> l; }
''',
}

RENAME_TSV = 'piece\tkind\told\tnew\nA1\tclass\tcom.wf.wflib.a.Old\tcom.wf.wflib.c.api.Fresh\n'


class RenameTest(Base):
    def setUp(self):
        self.tmp = tempfile.mkdtemp()
        self.root = os.path.join(self.tmp, 'repo')
        self.tsv = os.path.join(self.tmp, 'r.tsv')
        with open(self.tsv, 'w') as f:
            f.write(RENAME_TSV)
        self.write_tree(self.root, RENAME_FILES)
        subprocess.run(['git', 'init', '-q', self.root], check=True)
        subprocess.run(['git', '-C', self.root, 'add', '-A'], check=True)
        self.apply()

    def test_moved_file_renamed(self):
        t = self.read(f'src/main/java/{W}/c/api/Fresh.java')
        self.assertIn('package com.wf.wflib.c.api;', t)
        self.assertIn('public class Fresh {', t)
        self.assertIn('public Fresh() {}', t)
        self.assertIn('static Fresh make() { return new Fresh(); }', t)
        self.assertIn('"Old"', t)
        self.assertNotIn('import com.wf.wflib.a', t)

    def test_same_package_neighbor(self):
        t = self.read(f'src/main/java/{W}/a/Neighbor.java')
        self.assertIn('import com.wf.wflib.c.api.Fresh;', t)
        self.assertIn('Fresh o; Fresh.Inner i;', t)

    def test_importer(self):
        t = self.read(f'src/main/java/{W}/b/Importer.java')
        self.assertIn('import com.wf.wflib.c.api.Fresh;', t)
        self.assertIn('import com.wf.wflib.c.api.Fresh.Inner;', t)
        self.assertIn('Fresh o = new Fresh(); Inner i; Fresh.Inner j;', t)
        self.assertIn('"com.wf.wflib.c.api.Fresh"', t)

    def test_wildcard_keeps_wildcard_and_adds_import(self):
        t = self.read(f'src/main/java/{W}/b/Wild.java')
        self.assertIn('import com.wf.wflib.a.*;', t)
        self.assertIn('import com.wf.wflib.c.api.Fresh;', t)
        self.assertIn('Fresh o;', t)
        self.assertNotIn('Old', t)

    def test_wildcard_without_use_untouched(self):
        t = self.read(f'src/main/java/{W}/b/WildUnused.java')
        self.assertNotIn('Fresh', t)
        self.assertIn('import com.wf.wflib.a.*;', t)

    def test_shadowing_declaration_untouched(self):
        t = self.read(f'src/main/java/{W}/b/Other.java')
        self.assertIn('class Old {} Old o;', t)

    def test_unused_old_import_dropped(self):
        t = self.read(f'src/main/java/{W}/b/Dead.java')
        self.assertNotIn('Fresh', t)
        self.assertNotIn('a.Old', t)
        self.assertIn('import java.util.List;', t)

    def test_idempotent_and_verify(self):
        rc, out = run('--tsv', self.tsv, 'verify', self.root)
        self.assertEqual(rc, 0, out)
        before = self.read(f'src/main/java/{W}/b/Importer.java')
        self.apply()
        self.assertEqual(before, self.read(f'src/main/java/{W}/b/Importer.java'))


WILD_MOVE_FILES = {
    f'src/main/java/{W}/a/Alpha.java': 'package com.wf.wflib.a;\n\npublic class Alpha {}\n',
    f'src/main/java/{W}/build/User.java': 'package com.wf.wflib.build;\n\nimport com.wf.wflib.a.Alpha;\n\nclass User {\n    Alpha a;\n}\n',
    f'src/main/java/{W}/q/Mover.java': '''package com.wf.wflib.q;

import com.wf.wflib.a.*;

public class Mover {
    Alpha a;
}
''',
}

WILD_MOVE_TSV = (
    'piece\tkind\told\tnew\n'
    'A1\tclass\tcom.wf.wflib.a.Alpha\tcom.wf.wflib.c.api.Alpha\n'
    'A1\tclass\tcom.wf.wflib.q.Mover\tcom.wf.wflib.r.Mover\n')


class WildcardInMovedFileTest(Base):
    def setUp(self):
        self.tmp = tempfile.mkdtemp()
        self.root = os.path.join(self.tmp, 'repo')
        self.tsv = os.path.join(self.tmp, 'r.tsv')
        with open(self.tsv, 'w') as f:
            f.write(WILD_MOVE_TSV)
        self.write_tree(self.root, WILD_MOVE_FILES)
        subprocess.run(['git', 'init', '-q', self.root], check=True)
        subprocess.run(['git', '-C', self.root, 'add', '-A'], check=True)
        self.apply()

    def test_moved_file_with_old_package_wildcard_gets_explicit_import(self):
        t = self.read(f'src/main/java/{W}/r/Mover.java')
        self.assertIn('package com.wf.wflib.r;', t)
        self.assertNotIn('import com.wf.wflib.a.*;', t)
        self.assertIn('import com.wf.wflib.c.api.Alpha;', t)

    def test_package_named_build_is_scanned(self):
        t = self.read(f'src/main/java/{W}/build/User.java')
        self.assertIn('import com.wf.wflib.c.api.Alpha;', t)


STORE_FILES = {
    f'src/main/java/{W}/a/Alpha.java': 'package com.wf.wflib.a;\n\npublic class Alpha {}\n',
    f'src/main/java/{W}/a/Beta.java': 'package com.wf.wflib.a;\n\npublic class Beta {}\n',
    f'src/main/java/{W}/b/Wild.java': 'package com.wf.wflib.b;\n\nimport com.wf.wflib.a.*;\n\nclass Wild {\n    Alpha x;\n    Beta y;\n    com.wf.wflib.a.Beta z;\n}\n',
    'src/test/resources/archunit_store/stored.rules': 'rule=abc\n',
    'src/test/resources/archunit_store/abc': (
        'Method <com.wf.wflib.a.Alpha.go()> calls <com.wf.wflib.a.Beta.run()> in (Alpha.java:3)\n'
        'Class <com.wf.wflib.a.Alpha$Inner> depends on <com.wf.wflib.a.AlphaOther>\n'),
}

STORE_TSV = (
    'piece\tkind\told\tnew\n'
    'A1\tclass\tcom.wf.wflib.a.Alpha\tcom.wf.wflib.c.api.Alpha\n'
    'A2\tclass\tcom.wf.wflib.a.Beta\tcom.wf.wflib.d.api.Beta\n')


class StoreAndWildcardTest(Base):
    def setUp(self):
        self.tmp = tempfile.mkdtemp()
        self.root = os.path.join(self.tmp, 'repo')
        self.tsv = os.path.join(self.tmp, 'r.tsv')
        with open(self.tsv, 'w') as f:
            f.write(STORE_TSV)
        self.write_tree(self.root, STORE_FILES)
        subprocess.run(['git', 'init', '-q', self.root], check=True)
        subprocess.run(['git', '-C', self.root, 'add', '-A'], check=True)

    def test_partial_package_move_keeps_wildcard(self):
        self.apply('A1')
        t = self.read(f'src/main/java/{W}/b/Wild.java')
        self.assertIn('import com.wf.wflib.a.*;', t)
        self.assertIn('import com.wf.wflib.c.api.Alpha;', t)

    def test_fully_moved_package_drops_wildcard(self):
        self.apply('A1,A2')
        t = self.read(f'src/main/java/{W}/b/Wild.java')
        self.assertNotIn('a.*', t)
        self.assertIn('import com.wf.wflib.c.api.Alpha;', t)
        self.assertIn('import com.wf.wflib.d.api.Beta;', t)

    def test_archunit_store_rewritten(self):
        self.apply('A1')
        t = self.read('src/test/resources/archunit_store/abc')
        self.assertIn('<com.wf.wflib.c.api.Alpha.go()>', t)
        self.assertIn('<com.wf.wflib.c.api.Alpha$Inner>', t)
        self.assertIn('<com.wf.wflib.a.Beta.run()>', t)
        self.assertIn('<com.wf.wflib.a.AlphaOther>', t)
        self.assertEqual('rule=abc\n', self.read('src/test/resources/archunit_store/stored.rules'))

    def test_verify_respects_pieces(self):
        self.apply('A1')
        rc, out = run('--tsv', self.tsv, 'verify', '--pieces', 'A1', self.root)
        self.assertEqual(rc, 0, out)
        rc, out = run('--tsv', self.tsv, 'verify', self.root)
        self.assertNotEqual(rc, 0)


CA = 'com/wf/ca'
SRC_FILES = {
    f'src/main/java/{CA}/physics/Engine.java': '''package com.wf.ca.physics;

import java.util.List;

public class Engine {
    Hull hull;
    Left left;
    List<String> names;
}
''',
    f'src/main/java/{CA}/physics/Hull.java': 'package com.wf.ca.physics;\n\npublic class Hull {\n    Engine e;\n}\n',
    f'src/main/java/{CA}/physics/Left.java': 'package com.wf.ca.physics;\n\npublic class Left {\n    Engine e;\n}\n',
    f'src/main/java/{CA}/veh/Vehicle.java': '''package com.wf.ca.veh;

import com.wf.ca.physics.Engine;
import com.wf.ca.physics.Hull;

public class Vehicle {
    Engine e;
    Hull h;
}
''',
    f'src/test/java/{CA}/physics/EngineTest.java': 'package com.wf.ca.physics;\n\nclass EngineTest {\n    Engine e;\n    Left l;\n}\n',
    'scripts/scenario.mjs': "call('com.wf.ca.physics.Engine'); call('com.wf.ca.physics.EngineX');\n",
}
DST_FILES = {f'src/main/java/{W}/core/api/Existing.java': 'package com.wf.wflib.core.api;\n\npublic class Existing {}\n'}
TRANS_TSV = (
    'piece\tkind\told\tnew\n'
    'PH5\tclass\tcom.wf.ca.physics.Engine\tcom.wf.wflib.physics.Engine\n'
    'PH5\tclass\tcom.wf.ca.physics.Hull\tcom.wf.wflib.physics.api.Hull\n'
    'PH5\tclass\tcom.wf.ca.physics.EngineTest\tcom.wf.wflib.physics.EngineTest\n')


def slurp(path):
    with open(path) as f:
        return f.read()


def git(repo, *a):
    return subprocess.run(['git', '-C', repo, *a], capture_output=True, text=True, check=True).stdout


class TransplantTest(Base):
    def setUp(self):
        self.tmp = tempfile.mkdtemp()
        self.src = os.path.join(self.tmp, 'CombinedAssault')
        self.root = os.path.join(self.tmp, 'wflib')
        self.tsv = os.path.join(self.tmp, 'r.tsv')
        with open(self.tsv, 'w') as f:
            f.write(TRANS_TSV)
        self.write_tree(self.src, SRC_FILES)
        self.write_tree(self.root, DST_FILES)
        for r in (self.src, self.root):
            subprocess.run(['git', 'init', '-q', r], check=True)
            git(r, 'add', '-A')
        git(self.src, '-c', 'user.name=t', '-c', 'user.email=t@t', 'commit', '-qm', 'init')
        self.sha = git(self.src, 'rev-parse', '--short=12', 'HEAD').strip()

    def transplant(self, *extra):
        return run('--tsv', self.tsv, 'transplant', '--pieces', 'PH5', '--src', self.src, '--dst', self.root, *extra)

    def test_copy_keeps_source(self):
        rc, out = self.transplant('--keep-src')
        self.assertEqual(rc, 0, out)
        eng = self.read(f'src/main/java/{W}/physics/Engine.java')
        self.assertIn('package com.wf.wflib.physics;', eng)
        self.assertIn('import com.wf.wflib.physics.api.Hull;', eng)
        self.assertIn('import com.wf.ca.physics.Left;', eng)
        self.assertIn('import java.util.List;', eng)
        hull = self.read(f'src/main/java/{W}/physics/api/Hull.java')
        self.assertIn('package com.wf.wflib.physics.api;', hull)
        self.assertIn('import com.wf.wflib.physics.Engine;', hull)
        test = self.read(f'src/test/java/{W}/physics/EngineTest.java')
        self.assertIn('package com.wf.wflib.physics;', test)
        self.assertIn('import com.wf.ca.physics.Left;', test)
        self.assertFalse(os.path.exists(os.path.join(self.root, f'src/main/java/{W}/physics/Left.java')))
        staged = git(self.root, 'diff', '--cached', '--name-only').split()
        self.assertIn(f'src/main/java/{W}/physics/Engine.java', staged)
        self.assertIn(f'src/test/java/{W}/physics/EngineTest.java', staged)
        self.assertEqual(git(self.src, 'status', '--porcelain'), '')
        self.assertTrue(os.path.exists(os.path.join(self.src, f'src/main/java/{CA}/physics/Engine.java')))

    def test_origin_rows_written_once_and_ignored_by_apply(self):
        self.transplant('--keep-src')
        with open(self.tsv) as f:
            lines = f.read().split('\n')
        self.assertIn(f'PH5\torigin\tcom.wf.wflib.physics.Engine\tCombinedAssault@{self.sha}:src/main/java/{CA}/physics/Engine.java', lines)
        n = len([l for l in lines if '\torigin\t' in l])
        self.assertEqual(n, 3)
        before = self.read(f'src/main/java/{W}/physics/Engine.java')
        rc, out = self.transplant('--keep-src')
        self.assertIn('exists, not overwritten', out)
        with open(self.tsv) as f:
            self.assertEqual(len([l for l in f.read().split('\n') if '\torigin\t' in l]), n)
        self.assertEqual(before, self.read(f'src/main/java/{W}/physics/Engine.java'))
        rc, out = run('--tsv', self.tsv, 'apply', '--pieces', 'PH5', self.root)
        self.assertEqual(rc, 0, out)
        self.assertEqual(before, self.read(f'src/main/java/{W}/physics/Engine.java'))

    def test_source_removed_only_after_refs_gone(self):
        self.transplant('--keep-src')
        rc, out = self.transplant()
        self.assertEqual(rc, 1)
        self.assertIn('Vehicle.java', out)
        self.assertIn('implicit use of com.wf.ca.physics.Engine', out)
        self.assertTrue(os.path.exists(os.path.join(self.src, f'src/main/java/{CA}/physics/Engine.java')))
        rc, out = run('--tsv', self.tsv, 'apply', '--pieces', 'PH5', self.src)
        self.assertEqual(rc, 0, out)
        self.assertIn('move skipped', out)
        self.assertTrue(os.path.exists(os.path.join(self.src, f'src/main/java/{CA}/physics/Engine.java')))
        veh = slurp(os.path.join(self.src, f'src/main/java/{CA}/veh/Vehicle.java'))
        self.assertIn('import com.wf.wflib.physics.Engine;', veh)
        self.assertIn('import com.wf.wflib.physics.api.Hull;', veh)
        left = slurp(os.path.join(self.src, f'src/main/java/{CA}/physics/Left.java'))
        self.assertIn('import com.wf.wflib.physics.Engine;', left)
        rc, out = self.transplant()
        self.assertEqual(rc, 0, out)
        for n in ('Engine', 'Hull'):
            self.assertFalse(os.path.exists(os.path.join(self.src, f'src/main/java/{CA}/physics/{n}.java')))
        self.assertFalse(os.path.exists(os.path.join(self.src, f'src/test/java/{CA}/physics/EngineTest.java')))
        self.assertTrue(os.path.exists(os.path.join(self.src, f'src/main/java/{CA}/physics/Left.java')))
        self.assertIn('D ', git(self.src, 'status', '--porcelain'))
        rc, out = run('--tsv', self.tsv, 'verify', '--pieces', 'PH5', self.src)
        self.assertEqual(rc, 0, out)

    def test_extra_root(self):
        extra = os.path.join(self.src, 'scripts')
        rc, out = self.transplant('--keep-src', '--extra-root', extra)
        self.assertEqual(rc, 0, out)
        t = slurp(os.path.join(extra, 'scenario.mjs'))
        self.assertIn("'com.wf.wflib.physics.Engine'", t)
        self.assertIn("'com.wf.ca.physics.EngineX'", t)

    def test_apply_extra_root(self):
        extra = os.path.join(self.src, 'scripts')
        rc, out = run('--tsv', self.tsv, 'apply', '--pieces', 'PH5', '--extra-root', extra, self.src)
        self.assertEqual(rc, 0, out)
        self.assertIn("'com.wf.wflib.physics.Engine'", slurp(os.path.join(extra, 'scenario.mjs')))

    def test_dry_run(self):
        rc, out = self.transplant('--dry-run')
        self.assertIn('would copy', out)
        self.assertFalse(os.path.exists(os.path.join(self.root, f'src/main/java/{W}/physics')))
        self.assertNotIn('origin', slurp(self.tsv))




DEDUPE_FILES = {
    f'src/main/java/{W}/a/P.java': 'package com.wf.wflib.a;\n\npublic class P {}\n',
    f'src/main/java/{W}/a/Q.java': 'package com.wf.wflib.a;\n\npublic class Q {}\n',
    f'src/main/java/{W}/b/Use.java': (
        'package com.wf.wflib.b;\n\nimport com.wf.wflib.a.P;\nimport com.wf.wflib.a.Q;\n\nclass Use {\n    P p; Q q;\n}\n'),
    f'src/main/java/{W}/b/Calls.java': (
        'package com.wf.wflib.b;\n\nimport com.wf.wflib.a.P;\n\nclass Calls {\n    void f() { P.add(1); }\n}\n'),
}

DEDUPE_TSV = (
    'piece\tkind\told\tnew\n'
    'A1\tclass\tcom.wf.wflib.a.P\tcom.wf.wflib.c.api.Z\n'
    'A1\tclass\tcom.wf.wflib.a.Q\tcom.wf.wflib.c.api.Z\n'
    'A1\tmanual\tcom.wf.wflib.a.P#add\tcom.wf.wflib.a.P#ADDS.register\n'
    'A1\tmanual\tcom.wf.wflib.a.Q\tdeleted; no # in old\n')


class DedupeAndManualTest(Base):
    def setUp(self):
        self.tmp = tempfile.mkdtemp()
        self.root = os.path.join(self.tmp, 'repo')
        self.tsv = os.path.join(self.tmp, 'r.tsv')
        with open(self.tsv, 'w') as f:
            f.write(DEDUPE_TSV)
        self.write_tree(self.root, {k: v for k, v in DEDUPE_FILES.items() if k.endswith('Use.java') or k.endswith('Calls.java')})
        subprocess.run(['git', 'init', '-q', self.root], check=True)
        subprocess.run(['git', '-C', self.root, 'add', '-A'], check=True)
        self.rc, self.out = self.apply()

    def test_duplicate_import_collapsed(self):
        t = self.read(f'src/main/java/{W}/b/Use.java')
        self.assertEqual(t.count('import com.wf.wflib.c.api.Z;'), 1, t)

    def test_verify_survives_manual_row_without_member(self):
        args = relocate.argparse.Namespace(repo_root=self.root, tsv=self.tsv, pieces=None)
        with redirect_stdout(io.StringIO()):
            relocate.cmd_verify(args)

    def test_manual_row_not_rewritten_but_reported(self):
        t = self.read(f'src/main/java/{W}/b/Calls.java')
        self.assertIn('Z.add(1)', t)
        self.assertNotIn('ADDS', t)
        self.assertIn('hand edits', self.out)


MANUAL_TSV = (
    'piece\tkind\told\tnew\n'
    'A1\tclass\tcom.wf.wflib.a.Owner\tcom.wf.wflib.a.api.Owner\n'
    'A1\tmanual\tcom.wf.wflib.a.Owner#register\tcom.wf.wflib.a.api.Owner#PROVIDERS.register\n'
    'A1\tclass\tcom.wf.wflib.a.OldName\tcom.wf.wflib.a.api.NewName\n'
    'A1\tmanual\tcom.wf.wflib.a.OldName#ITEMS\tcom.wf.wflib.a.api.NewName#ALL\n')


class VerifyManualCallSiteTest(Base):
    """verify flags the old member call by simple name even after the owner import was rewritten."""

    def setUp(self):
        self.tmp = tempfile.mkdtemp()
        self.root = os.path.join(self.tmp, 'repo')
        self.tsv = os.path.join(self.tmp, 'r.tsv')
        with open(self.tsv, 'w') as f:
            f.write(MANUAL_TSV)
        self.write_tree(self.root, {
            'src/main/java/dep/Calls.java':
                'package dep;\n\nimport com.wf.wflib.a.Owner;\n\nclass Calls { void f() { Owner.register(1); } }\n',
            'src/main/java/dep/Other.java':
                'package dep;\n\nclass Other { void f() { Owner.register(2); } }\n',
            'src/main/java/dep/Renamed.java':
                'package dep;\n\nimport com.wf.wflib.a.OldName;\n\nclass Renamed { Object o = OldName.ITEMS; }\n',
        })
        subprocess.run(['git', 'init', '-q', self.root], check=True)
        subprocess.run(['git', '-C', self.root, 'add', '-A'], check=True)

    def test_apply_then_verify_flags_call_sites_and_fails(self):
        run('--tsv', self.tsv, 'apply', '--pieces', 'A1', self.root)
        rc, out = run('--tsv', self.tsv, 'verify', self.root)
        self.assertEqual(rc, 1, out)
        self.assertIn('Calls.java:5: manual com.wf.wflib.a.Owner#register', out)
        self.assertIn('Other.java:3: manual com.wf.wflib.a.Owner#register', out)
        self.assertIn('Renamed.java:5: manual com.wf.wflib.a.OldName#ITEMS', out)

    def test_verify_clean_exits_zero(self):
        for name in ('Calls', 'Other', 'Renamed'):
            with open(os.path.join(self.root, f'src/main/java/dep/{name}.java'), 'w') as f:
                f.write(f'package dep;\n\nclass {name} {{}}\n')
        rc, out = run('--tsv', self.tsv, 'verify', self.root)
        self.assertEqual(rc, 0, out)


ACCESSOR_TSV = (
    'piece\tkind\told\tnew\n'
    'A1\tmanual\tcom.wf.wflib.a.Lights#flashes\tLights#flashes() (field private)\n'
    'A1\tmember\tcom.wf.wflib.a.Clock#started\tcom.wf.wflib.a.Clock#started()\n')


class VerifyAccessorRowTest(Base):
    """Field -> same-named accessor: the migrated call is not a leftover; the bare field still is."""

    def setUp(self):
        self.tmp = tempfile.mkdtemp()
        self.root = os.path.join(self.tmp, 'repo')
        self.tsv = os.path.join(self.tmp, 'r.tsv')
        with open(self.tsv, 'w') as f:
            f.write(ACCESSOR_TSV)
        self.write_tree(self.root, {
            'src/main/java/dep/Uses.java':
                'package dep;\n\nimport com.wf.wflib.a.Lights;\nimport com.wf.wflib.a.Clock;\n\n'
                'class Uses { Object o = Lights.flashes (); boolean b = Clock.started(); }\n',
        })

    def test_new_form_clean_old_form_flagged(self):
        rc, out = run('--tsv', self.tsv, 'verify', self.root)
        self.assertEqual(rc, 0, out)
        with open(os.path.join(self.root, 'src/main/java/dep/Old.java'), 'w') as f:
            f.write('package dep;\n\nimport com.wf.wflib.a.Lights;\nimport com.wf.wflib.a.Clock;\n\n'
                    'class Old { Object o = Lights.flashes; boolean b = Clock.started; }\n')
        rc, out = run('--tsv', self.tsv, 'verify', self.root)
        self.assertEqual(rc, 1, out)
        self.assertIn('Old.java:6: manual com.wf.wflib.a.Lights#flashes', out)
        self.assertIn('Old.java:6: member com.wf.wflib.a.Clock#started', out)
        self.assertNotIn('Uses.java', out)


REUSE_TSV = (
    'piece\tkind\told\tnew\n'
    'A1\tclass\tcom.wf.wflib.a.Facade\tcom.wf.wflib.a.api.Resolution\n')


class VerifyReusedNameTest(Base):
    """A class row's old FQCN that the tree declares again (a new entry reusing a renamed facade's name) is not a leftover."""

    def setUp(self):
        self.tmp = tempfile.mkdtemp()
        self.root = os.path.join(self.tmp, 'repo')
        self.tsv = os.path.join(self.tmp, 'r.tsv')
        with open(self.tsv, 'w') as f:
            f.write(REUSE_TSV)
        self.write_tree(self.root, {
            'src/main/java/com/wf/wflib/a/Facade.java': 'package com.wf.wflib.a;\n\npublic final class Facade {}\n',
            'src/main/java/com/wf/wflib/a/api/Resolution.java':
                'package com.wf.wflib.a.api;\n\npublic final class Resolution {}\n',
            'src/main/java/dep/Uses.java':
                'package dep;\n\nimport com.wf.wflib.a.Facade;\n\nclass Uses { Facade f; }\n',
        })

    def test_reused_name_is_clean_but_dependent_is_not(self):
        rc, out = run('--tsv', self.tsv, 'verify', self.root)
        self.assertEqual(rc, 0, out)
        os.remove(os.path.join(self.root, 'src/main/java/com/wf/wflib/a/Facade.java'))
        rc, out = run('--tsv', self.tsv, 'verify', self.root)
        self.assertEqual(rc, 1, out)
        os.remove(os.path.join(self.root, 'src/main/java/com/wf/wflib/a/api/Resolution.java'))
        rc, out = run('--tsv', self.tsv, 'verify', self.root)
        self.assertEqual(rc, 1, out)


if __name__ == '__main__':
    unittest.main()
