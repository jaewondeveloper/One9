"""Attribute level diff of layout files between SESL 8 and a One UI 9 app.
usage: diff_layouts.py <sesl8 aar dir> <app decoded res dir> <out file>"""
import glob, io, os, re, sys, zipfile
import xml.etree.ElementTree as ET

AARS, APP, OUT = sys.argv[1:4]
ANDROID = '{http://schemas.android.com/apk/res/android}'
AUTO = '{http://schemas.android.com/apk/res-auto}'

def norm(v):
    v = re.sub(r'(-?\d+(?:\.\d+)?)(dp|dip|sp|px)\b', lambda m: ('%g' % float(m.group(1))) + ('dp' if m.group(2) == 'dip' else m.group(2)), v)
    return v.replace('@+id/', '@id/')

def flat(root):
    out = {}
    def walk(el, path):
        ident = None
        for k, v in el.attrib.items():
            if k == ANDROID + 'id': ident = v.split('/')[-1]
        tag = el.tag.split('}')[-1]
        p = f'{path}/{tag}' + (f'#{ident}' if ident else '')
        n = 1
        while p in out: n += 1; p = f'{path}/{tag}' + (f'#{ident}' if ident else '') + f'[{n}]'
        out[p] = {(k.replace(ANDROID, 'android:').replace(AUTO, 'app:')): norm(v) for k, v in el.attrib.items()}
        for c in el: walk(c, p)
    walk(root, '')
    return out

s8 = {}
for aar in glob.glob(AARS + '/*.aar'):
    z = zipfile.ZipFile(aar)
    for n in z.namelist():
        m = re.fullmatch(r'res/layout/([^/]+)\.xml', n)
        if m:
            try: s8[m.group(1)] = ET.fromstring(z.read(n))
            except ET.ParseError: pass

lines = []
changed = 0
for p in sorted(glob.glob(APP + '/layout/*.xml')):
    name = os.path.basename(p)[:-4]
    if name not in s8: continue
    if not re.search(r'preference|sesl_|list|appbar|toolbar|tab|navigation|dialog|switch|search', name): continue
    try: a = flat(s8[name]); b = flat(ET.parse(p).getroot())
    except ET.ParseError: continue
    diffs = []
    for path in sorted(set(a) | set(b)):
        if path not in a: diffs.append(f'  + {path}')
        elif path not in b: diffs.append(f'  - {path}')
        else:
            for k in sorted(set(a[path]) | set(b[path])):
                va, vb = a[path].get(k), b[path].get(k)
                if va != vb and not (k in ('android:layout_width', 'android:layout_height') and {va, vb} <= {'-1', 'match_parent', '-2', 'wrap_content', 'fill_parent'}):
                    diffs.append(f'  ~ {path} {k}: {va} -> {vb}')
    if diffs:
        changed += 1
        lines.append(f'### layout/{name} ({len(diffs)} differences)')
        lines.extend(diffs[:40])
open(OUT, 'w').write(f'{changed} layouts differ\n\n' + '\n'.join(lines) + '\n')
print(f'{changed} layouts differ')
