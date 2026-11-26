"""Import One UI 9 design resources that differ from SESL 8.

usage: import_res.py <sesl8 aar dir> <app decoded res dir> <lib res dir> <report out>
Imports only resources that (a) exist in SESL 8 with different content, or (b) are new sesl_* resources,
and only when every class / attribute / resource they reference is available (so nothing breaks at runtime).
"""
import glob, os, re, sys, zipfile, shutil, json
import xml.etree.ElementTree as ET

AARS, APP, LIB, REPORT = sys.argv[1:5]
FILE_TYPES = ('drawable', 'anim', 'animator', 'interpolator', 'color')
QUAL_OK = re.compile(r'^(night-)?(v\d+)?$|^night$')

def split_dir(d):
    t, _, q = d.partition('-')
    return t, q
def normq(q):
    # minSdk is 24: version qualifiers <= v24 are redundant and would collide with the unqualified dir
    out = []
    for part in [x for x in q.split('-') if x]:
        m = re.fullmatch(r'v(\d+)', part)
        if m and int(m.group(1)) <= 24: continue
        out.append(part)
    return '-'.join(out)
def qual_ok(q):
    return q == '' or bool(re.fullmatch(r'(night)?(-?v\d+)?', q))

# ---------- load SESL8
s8_tmp = '/tmp/s8'
shutil.rmtree(s8_tmp, ignore_errors=True)
attr_enums = {}
classes = set(); s8_files = {}; s8_values = {}; s8_attrs = set(); s8_names = {}
for aar in glob.glob(AARS + '/*.aar'):
    z = zipfile.ZipFile(aar); mod = os.path.basename(aar)[:-4]
    for n in z.namelist():
        if n == 'classes.jar':
            import io
            jz = zipfile.ZipFile(io.BytesIO(z.read(n)))
            for jn in jz.namelist():
                if jn.endswith('.class'): classes.add(jn[:-6].replace('/', '.'))
        elif n.startswith('res/') and not n.endswith('/'):
            d, f = n[4:].split('/', 1)
            t, q = split_dir(d)
            dst = os.path.join(s8_tmp, d, f)
            os.makedirs(os.path.dirname(dst), exist_ok=True)
            open(dst, 'wb').write(z.read(n))
            if t != 'values':
                s8_files.setdefault((t, os.path.splitext(f)[0]), set()).add(q)
                s8_names.setdefault(t, set()).add(os.path.splitext(f)[0])
            elif f.endswith('.xml'):
                try: root = ET.fromstring(z.read(n))
                except ET.ParseError: continue
                for e in root:
                    nm = e.get('name')
                    if not nm: continue
                    tag = e.tag
                    if tag == 'item': tag = e.get('type', 'item')
                    if tag == 'attr':
                        s8_attrs.add(nm)
                        for c in e:
                            if c.tag in ('enum', 'flag'): attr_enums.setdefault(nm, set()).add(c.get('name'))
                    if tag == 'declare-styleable':
                        for a in e:
                            s8_attrs.add(a.get('name'))
                            for c in a:
                                if c.tag in ('enum', 'flag'): attr_enums.setdefault(a.get('name'), set()).add(c.get('name'))
                    s8_names.setdefault(tag, set()).add(nm)
                    s8_values[(tag, nm, q)] = ET.tostring(e, encoding='unicode')
print('SESL8: classes', len(classes), 'file groups', len(s8_files), 'value entries', len(s8_values), 'attrs', len(s8_attrs))

# ---------- remove files imported by a previous run (idempotent)
MANIFEST = os.path.join(os.path.dirname(os.path.abspath(__file__)), 'imported.txt')
if os.path.exists(MANIFEST):
    for line in open(MANIFEST).read().split():
        fp = os.path.join(LIB, line)
        if os.path.exists(fp): os.remove(fp)

for _p in glob.glob(LIB + '/*/oui_des_oneui9_import*.xml'): os.remove(_p)

# ---------- existing lib resources (never redefine)
lib_names = {}
for p in glob.glob(LIB + '/*/*'):
    d = os.path.basename(os.path.dirname(p)); t, q = split_dir(d)
    if t != 'values': lib_names.setdefault(t, set()).add(os.path.splitext(os.path.basename(p))[0])
    elif p.endswith('.xml'):
        try: root = ET.parse(p).getroot()
        except ET.ParseError: continue
        for e in root:
            nm = e.get('name')
            if not nm: continue
            tag = e.tag if e.tag != 'item' else e.get('type', 'item')
            lib_names.setdefault(tag, set()).add(nm)
            if tag == 'attr': s8_attrs.add(nm)
            if tag == 'declare-styleable':
                for a in e: s8_attrs.add(a.get('name'))

# ---------- load app
app_files = {}   # (type,name) -> {qual: path}
app_values = {}  # (tag,name,qual) -> element xml string ; plus elements
app_elems = {}
for d in sorted(os.listdir(APP)):
    t, q = split_dir(d)
    if not qual_ok(q): continue
    for p in glob.glob(os.path.join(APP, d, '*')):
        if t == 'values':
            if not p.endswith('.xml') or os.path.basename(p) in ('strings.xml', 'public.xml', 'ids.xml', 'plurals.xml', 'arrays.xml', 'attrs.xml'): continue
            try: root = ET.parse(p).getroot()
            except ET.ParseError: continue
            for e in root:
                nm = e.get('name'); tag = e.tag if e.tag != 'item' else e.get('type', 'item')
                if nm and tag in ('color', 'dimen', 'integer', 'bool', 'fraction', 'style'):
                    app_elems[(tag, nm, q)] = e
        elif t in FILE_TYPES and p.endswith('.xml'):
            app_files.setdefault((t, os.path.splitext(os.path.basename(p))[0]), {})[q] = p

def norm(s):
    s = re.sub(r'<!--.*?-->', '', s, flags=re.S)
    s = s.replace('@+id/', '@id/')
    s = re.sub(r'xmlns:\w+="[^"]*"', '', s)
    s = re.sub(r'ns\d+:', '', s)
    s = re.sub(r'(-?\d+(?:\.\d+)?)(dp|dip|sp|px)\b', lambda m: ('%g' % float(m.group(1))) + ('dp' if m.group(2) == 'dip' else m.group(2)), s)
    s = re.sub(r'(?<=[="> ])(-?\d+)\.0(?=[ "<])', r'\1', s)
    return re.sub(r'\s+', '', s)
def canon_file(path):
    try: return norm(ET.tostring(ET.parse(path).getroot(), encoding='unicode'))
    except ET.ParseError: return None

# Switch / toggle widgets draw with SESL 8 geometry; mixing in SESL 9 drawables clips them.
EXCLUDE = re.compile(r'(^sesl_(switch|toggle))|Switch|Toggle')

# ---------- candidate files
cand = {}  # (type,name) -> path of base content
for (t, n), quals in app_files.items():
    if EXCLUDE.search(n): continue
    base = quals.get('') or next(iter(quals.values()))
    in8 = (t, n) in s8_files
    if in8:
        s8base = None
        for q in ('', 'night'):
            pth = os.path.join(s8_tmp, f'{t}' + (f'-{q}' if q else ''), n + '.xml')
            if os.path.exists(pth): s8base = pth; break
        a = canon_file(quals.get('') or base); b = canon_file(s8base) if s8base else None
        if a is not None and a != b: cand[(t, n)] = 'changed'
    elif n.startswith('sesl_') and n not in lib_names.get(t, set()):
        cand[(t, n)] = 'new'

# ---------- reference checking (iterate to fixed point)
REF = re.compile(r'@(?!android:)(?:\+)?(\w+)/([\w.]+)')
ATTR = re.compile(r'(?<=["\'])\?(?!android:)(?:attr/)?([A-Za-z_][\w.]*)')
TAGS = re.compile(r'<([A-Za-z_][\w.]*\.[\w.$]+)')
def read(path): return open(path, encoding='utf-8', errors='ignore').read()
def known(t, n, imported):
    return n in s8_names.get(t, set()) or n in lib_names.get(t, set()) or (t, n) in imported
imported_vals = {}  # (tag,name) -> element
dropped = {}
def refs_of(text):
    text = re.sub(r'<\?xml[^>]*\?>', '', text)
    return REF.findall(text), ATTR.findall(text), TAGS.findall(text)

AUTO_NS = 'http://schemas.android.com/apk/res-auto'
def attr_names_in(text):
    names = set()
    try:
        root = ET.fromstring(text)
    except ET.ParseError:
        return names
    for el in root.iter():
        for k in el.attrib:
            if k.startswith('{' + AUTO_NS + '}'): names.add(k.split('}', 1)[1])
    return names

def check(text, imported_files):
    problems = []
    for a in attr_names_in(text):
        if a not in s8_attrs: problems.append('app:' + a)
    rs, at, tg = refs_of(text)
    for c in tg:
        if c.startswith(('android.', 'java.')): continue
        if c not in classes: problems.append('class ' + c)
    for a in at:
        if a not in s8_attrs and not a.startswith('android'):
            if a not in ('attr',): problems.append('attr ' + a)
    for t, n in rs:
        if t in ('id', 'string', 'plurals', 'array', 'layout', 'menu', 'raw', 'font', 'navigation', 'mipmap'):
            if t == 'id' or known(t, n, set()): continue
            continue
        if t in FILE_TYPES + ('color',):
            if known(t, n, imported_files) or ((t, n) in cand): continue
            if t == 'color' and ('color', n, '') in app_elems: continue
            if t == 'drawable' and ('color', n, '') in app_elems: continue
            problems.append(f'{t}/{n}')
        elif t in ('dimen', 'integer', 'bool', 'fraction', 'style'):
            if known(t, n, set()) or ((t, n, '') in app_elems): continue
            problems.append(f'{t}/{n}')
        else:
            if t in ('xml',): continue
            problems.append(f'{t}/{n}')
    return problems

# need-value closure
def elem_text(e): return ET.tostring(e, encoding='unicode')
changed = True
while changed:
    changed = False
    for key in list(cand):
        t, n = key
        quals = app_files[key]
        texts = [read(p) for p in quals.values()]
        probs = []
        for tx in texts: probs += check(tx, set(cand))
        # values referenced but only in app are allowed (imported below); verify those recursively later
        real = [p for p in probs if not re.match(r'(dimen|integer|bool|fraction|color)/', p) or True]
        if real:
            dropped[key] = real[:4]; del cand[key]; changed = True

# ---------- values: import changed/new sesl_*/basic_token_* values + whatever candidates need
needed = set()
for key in cand:
    for p in app_files[key].values():
        for t, n in REF.findall(read(p)):
            if t in ('dimen', 'integer', 'bool', 'fraction', 'color', 'style'): needed.add((t, n))
want = {}
for (tag, nm, q), e in app_elems.items():
    if q not in ('', 'night'): continue
    if tag == 'style': continue
    if nm in lib_names.get(tag, set()) or EXCLUDE.search(nm): continue
    in8 = (tag, nm, q) in s8_values or nm in s8_names.get(tag, set())
    sesl_like = nm.startswith(('sesl_', 'basic_token_'))
    if (tag, nm) in needed and not in8:
        want[(tag, nm, q)] = e
    elif sesl_like and in8:
        old = s8_values.get((tag, nm, q))
        if old is not None and norm(old) != norm(elem_text(e)):
            # compare by text of element value only
            want[(tag, nm, q)] = e
    elif sesl_like and not in8:
        want[(tag, nm, q)] = e
# verify value refs
def vref_ok(e):
    tx = elem_text(e) if not isinstance(e, str) else e
    for t, n in REF.findall(tx):
        if t in ('dimen', 'integer', 'bool', 'fraction', 'color') and not (known(t, n, set()) or (t, n, '') in want or (t, n, 'night') in want): return False
    return True
ch = True
while ch:
    ch = False
    for k in list(want):
        if not vref_ok(want[k]): del want[k]; ch = True
# drop candidate files that need dropped values
def vals_available(t, n):
    return known(t, n, set()) or (t, n, '') in want or (t, n, 'night') in want
for key in list(cand):
    for p in app_files[key].values():
        bad = [(t, n) for t, n in REF.findall(read(p)) if t in ('dimen', 'integer', 'bool', 'fraction') and not vals_available(t, n)]
        if bad:
            dropped[key] = ['value ' + '/'.join(bad[0])]; cand.pop(key, None); break

# ---------- styles
def style_canon(e):
    p = (e.get('parent') or '').replace('@style/', '').replace('@android:style/', 'android:')
    if not p and '.' in (e.get('name') or ''): p = e.get('name').rsplit('.', 1)[0]
    its = []
    for it in e:
        v = norm((it.text or '').strip())
        its.append((it.get('name'), v))
    return (p, tuple(sorted(its)))
style_out = {}
def ref_available(t, n):
    if t == 'style': return None  # decided after the style set is final
    if known(t, n, set()): return True
    if (t, n, '') in want or (t, n, 'night') in want: return True
    if t in FILE_TYPES and (t, n) in cand: return True
    return False
def item_ok(it):
    for t, n in REF.findall((it.text or '')):
        if t in ('id', 'string', 'plurals', 'array', 'layout', 'menu', 'raw', 'font', 'navigation', 'mipmap', 'xml'): continue
        if ref_available(t, n) is False: return False
    return True
def style_items(e):
    out = []
    for it in e:
        nm = it.get('name') or ''
        if not nm.startswith('android:') and nm not in s8_attrs: continue
        if not item_ok(it): continue
        enums = attr_enums.get(nm)
        if enums:
            val = (it.text or '').strip()
            if val and not val.startswith(('@', '?')):
                toks = [x.strip() for x in val.split('|')]
                if any(x not in enums and not re.fullmatch(r'-?\d+|0x[0-9a-fA-F]+', x) for x in toks): continue
        out.append(it)
    return out
for (tag, nm, q), e in app_elems.items():
    if tag != 'style' or q != '': continue
    if nm in lib_names.get('style', set()) or EXCLUDE.search(nm): continue
    in8 = nm in s8_names.get('style', set())
    if not (nm.startswith(('Widget.', 'TextAppearance.', 'Base.', 'Theme', 'ThemeOverlay.', 'Sesl', 'sesl')) or in8): continue
    parent = e.get('parent')
    if parent and not parent.startswith('@android:'):
        pn = parent.replace('@style/', '').replace('@', '')
        if pn not in s8_names.get('style', set()) and pn not in lib_names.get('style', set()) and ('style', pn, '') not in app_elems: continue
    items = style_items(e)
    old = s8_values.get(('style', nm, ''))
    if in8 and old is not None and style_canon(ET.fromstring(old)) == style_canon(e): continue
    if in8 and old is None: continue
    style_out[nm] = (parent, items)

def style_known(n):
    return n in s8_names.get('style', set()) or n in lib_names.get('style', set()) or n in style_out
changed_styles = True
while changed_styles:
    changed_styles = False
    for nm, (parent, items) in list(style_out.items()):
        if parent and not parent.startswith('@android:'):
            pn = parent.replace('@style/', '').replace('@', '')
            if not style_known(pn):
                del style_out[nm]; changed_styles = True; continue
        elif parent is None and '.' in nm:
            if not style_known(nm.rsplit('.', 1)[0]):
                del style_out[nm]; changed_styles = True; continue
        keep = [it for it in items if all(style_known(n) for t, n in REF.findall(it.text or '') if t == 'style')]
        if len(keep) != len(items):
            style_out[nm] = (parent, keep); changed_styles = True

# ---------- write
def wipe_generated():
    for p in glob.glob(LIB + '/*/oui_des_oneui9_import*.xml'): os.remove(p)
wipe_generated()
written = {'files': [], 'values': 0, 'styles': 0}
manifest = []
for (t, n) in sorted(cand):
    quals = app_files[(t, n)]
    base = quals.get('') or next(iter(quals.values()))
    targets = {}
    for q in sorted(quals, key=lambda x: (x != '', x)):
        targets.setdefault(normq(q), quals[q])
    has_night = any('night' in q.split('-') for q in quals)
    for q in s8_files.get((t, n), set()):
        if 'night' in q.split('-'):
            if not has_night: continue   # never copy day content into night
            targets.setdefault(normq(q), next(v for k, v in quals.items() if 'night' in k.split('-')))
        else:
            targets.setdefault(normq(q), base)
    for q, src in targets.items():
        d = os.path.join(LIB, t + (f'-{q}' if q else ''))
        os.makedirs(d, exist_ok=True)
        shutil.copyfile(src, os.path.join(d, n + '.xml'))
        manifest.append(os.path.join(t + (f'-{q}' if q else ''), n + '.xml'))
    written['files'].append(f'{t}/{n} [{cand[(t, n)]}]')
def render_value(tag, nm, e):
    txt = elem_text(e)
    txt = re.sub(r'\sxmlns:\w+="[^"]*"', '', txt)
    return '    ' + txt.strip().replace('\n', '\n    ') + '\n'
for q, fn in (('', 'values'), ('night', 'values-night')):
    items = [(k, e) for k, e in want.items() if k[2] == q]
    if not items: continue
    os.makedirs(os.path.join(LIB, fn), exist_ok=True)
    body = ''.join(render_value(k[0], k[1], e) for k, e in sorted(items))
    open(os.path.join(LIB, fn, 'oui_des_oneui9_import.xml'), 'w').write('<?xml version="1.0" encoding="utf-8"?>\n<!-- generated by tools/oneui9-import -->\n<resources>\n' + body + '</resources>\n')
    written['values'] += len(items)
if style_out:
    body = ''
    for nm, (parent, items) in sorted(style_out.items()):
        body += f'    <style name="{nm}"' + (f' parent="{parent}"' if parent is not None else '') + '>\n'
        for it in items: body += '        ' + re.sub(r'\sxmlns:\w+="[^"]*"', '', ET.tostring(it, encoding='unicode')).strip() + '\n'
        body += '    </style>\n'
    open(os.path.join(LIB, 'values', 'oui_des_oneui9_import_styles.xml'), 'w').write('<?xml version="1.0" encoding="utf-8"?>\n<resources xmlns:android="http://schemas.android.com/apk/res/android">\n' + body + '</resources>\n')
    written['styles'] = len(style_out)

open(MANIFEST, 'w').write('\n'.join(manifest) + '\n')
with open(REPORT, 'w') as f:
    f.write(f"imported files: {len(written['files'])}, values: {written['values']}, styles: {written['styles']}\n\n## FILES\n")
    f.write('\n'.join(written['files']) + '\n\n## DROPPED (reason)\n')
    for (t, n), why in sorted(dropped.items()): f.write(f'{t}/{n}: {", ".join(why)}\n')
    f.write('\n## STYLES\n' + '\n'.join(sorted(style_out)) + '\n')
print(open(REPORT).read()[:3000])
