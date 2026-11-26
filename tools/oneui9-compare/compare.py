"""Compare SESL 8 resources with resources of apps that ship One UI 9 design.
Only names/values are compared and reported; nothing from the apps is copied."""
import glob, os, re, sys, json, zipfile, collections
S8, APPS = sys.argv[1], sys.argv[2:]

def values_of(resdir):
    d = collections.defaultdict(dict)  # kind -> name -> text
    for f in glob.glob(resdir + '/values*/*.xml'):
        if re.search(r'values-(?!night|v3[0-9]|sw|w|land|port|ldrtl)[a-z]{2}', f): continue  # skip locales
        txt = open(f, encoding='utf-8', errors='ignore').read()
        for m in re.finditer(r'<(color|dimen|integer|bool|item|style|attr|declare-styleable)\b[^>]*?name="([^"]+)"[^>]*?(?:/>|>(.*?)</\1>)', txt, re.S):
            kind, name, body = m.group(1), m.group(2), (m.group(3) or '').strip()
            tag = f.split('/')[-2]
            d[kind][name + '|' + tag] = re.sub(r'\s+', ' ', body)
    return d

def files_of(resdir):
    s = set()
    for p in glob.glob(resdir + '/*/*'):
        t = os.path.basename(os.path.dirname(p)).split('-')[0]
        if t != 'values': s.add(t + '/' + os.path.splitext(os.path.basename(p))[0])
    return s

key = re.compile(r'^(sesl|oui|basic_token|Widget\.Design|TextAppearance\.Design|Theme\.Design|Base\.Widget\.Design|Widget\.AppCompat|Base\.Widget\.AppCompat|Widget\.Sesl)', re.I)
def interesting(n): return bool(key.match(n.split('|')[0]))

# unpack SESL8 aars
sesl_dir = '/tmp/sesl8_res'
os.makedirs(sesl_dir, exist_ok=True)
for aar in glob.glob(S8 + '/*.aar'):
    z = zipfile.ZipFile(aar)
    nm = os.path.basename(aar)[:-4]
    for n in z.namelist():
        if n.startswith('res/') and not n.endswith('/'):
            t = os.path.join(sesl_dir, nm + '__' + n[4:].replace('/', '/', 1))
            # flatten per-module res into one tree: res/<type>/<file>
            dst = os.path.join(sesl_dir, 'res', n[4:])
            os.makedirs(os.path.dirname(dst), exist_ok=True)
            if os.path.exists(dst) and n.startswith('res/values'):
                dst = os.path.join(sesl_dir, 'res', n[4:] + '.' + nm + '.xml')
            open(dst, 'wb').write(z.read(n))
s8 = os.path.join(sesl_dir, 'res')
s8v, s8f = values_of(s8), files_of(s8)
print(f'SESL8: {sum(len(v) for v in s8v.values())} value entries, {len(s8f)} files')

report = {}
for app in APPS:
    name = os.path.basename(os.path.dirname(app.rstrip('/'))) if app.endswith('/res') else os.path.basename(app)
    av, af = values_of(app), files_of(app)
    r = {'new_files': sorted(f for f in af - s8f if interesting(f.split('/')[1])),
         'removed_files': sorted(f for f in s8f - af if interesting(f.split('/')[1])),
         'new_values': {}, 'changed_values': {}}
    for kind in av:
        nv = sorted(k for k in av[kind] if interesting(k) and k not in s8v.get(kind, {}))
        ch = sorted((k, s8v[kind][k], av[kind][k]) for k in av[kind]
                    if interesting(k) and k in s8v.get(kind, {}) and s8v[kind][k] != av[kind][k])
        if nv: r['new_values'][kind] = nv
        if ch: r['changed_values'][kind] = ch
    report[name] = r
    print(f'\n=== {name}: new files {len(r["new_files"])}, removed {len(r["removed_files"])}')
    for k, v in r['new_values'].items(): print(f'  new {k}: {len(v)}')
    for k, v in r['changed_values'].items(): print(f'  changed {k}: {len(v)}')
json.dump(report, open('compare-report.json', 'w'), indent=1)

# compact human summary
def show(title, items, n=80):
    print(f'\n##### {title} ({len(items)})')
    for i in items[:n]: print('  ', i if isinstance(i, str) else ' -> '.join(map(str, i))[:230])
for app, r in report.items():
    print(f'\n################ {app}')
    show('NEW FILES (not in SESL8)', r['new_files'], 150)
    for kind in ('dimen', 'integer', 'bool', 'color'):
        show(f'CHANGED {kind}', r['changed_values'].get(kind, []), 70)
    show('CHANGED style', r['changed_values'].get('style', []), 50)
    show('NEW style', r['new_values'].get('style', []), 60)
    show('NEW attr', r['new_values'].get('attr', []), 40)
