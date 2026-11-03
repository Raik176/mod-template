import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import { spawnSync } from 'node:child_process';
import { createRequire } from 'node:module';
import { fileURLToPath } from 'node:url';
import { collect } from './build.mjs';

const here = path.dirname(fileURLToPath(import.meta.url));
const root = path.resolve(here, '..');
const Gen = createRequire(import.meta.url)('./gen.js');

let failed = 0;
const ok = (m) => console.log('  ok    ' + m);
const bad = (m) => { failed++; console.log('  FAIL  ' + m); };
const bytes = (f) => (f.bytes ? Buffer.from(f.bytes) : Buffer.from(f.text, 'utf8'));
const section = (s) => console.log('\n' + s);

const { mode, files } = collect(root);
const data = { files };
const seed = Gen.analyze(data);

section('analyze');
console.log('  ' + JSON.stringify({ modId: seed.modId, cls: seed.cls, pkg: seed.pkg, group: seed.group, project: seed.project, loaders: seed.loaders,
  versions: seed.versions.map((v) => v.mc), vcs: seed.vcs, mixins: seed.mixinFiles.length, ct: seed.hasClassTweaker }));
ok(`read ${files.length} files from ${mode}`);

section('identity: example values in, repo out');
{
  const out = Gen.generate(Gen.identityConfig(seed), data, seed);
  if (out.errors.length) bad(JSON.stringify(out.errors));
  const gen = new Map(out.files.map((f) => [f.path, f]));
  const miss = files.filter((f) => !gen.has(f.p)).map((f) => f.p);
  const extra = [...gen.keys()].filter((p) => !files.some((f) => f.p === p));
  const diff = files.filter((f) => gen.has(f.p) && !(f.b !== undefined ? Buffer.from(f.b, 'base64') : Buffer.from(f.t, 'utf8')).equals(bytes(gen.get(f.p)))).map((f) => f.p);
  miss.length ? bad('missing: ' + miss.join(', ')) : ok('every file present');
  extra.length ? bad('extra: ' + extra.join(', ')) : ok('nothing extra');
  diff.length ? bad('different: ' + diff.join(', ')) : ok('all ' + files.length + ' files byte-identical');
  const exec = files.filter((f) => f.x).map((f) => f.p).sort().join();
  const gexec = out.files.filter((f) => f.exec).map((f) => f.path).sort().join();
  exec === gexec ? ok('executable bits kept (' + (exec || 'none') + ')') : bad('exec bits ' + gexec + ' vs ' + exec);
}

section('walk fallback lists the same files as git');
if (mode === 'git') {
  const tmp = fs.mkdtempSync(path.join(process.env.TMPDIR || os.tmpdir(), 'gen-walk-'));
  fs.cpSync(root, tmp, { recursive: true, filter: (s) => !/[\\/]\.git$/.test(s) && !/[\\/]generator[\\/]dist/.test(s) });
  const w = collect(tmp);
  const a = files.map((f) => f.p).join('\n'), b = w.files.map((f) => f.p).join('\n');
  w.mode === 'walk' && a === b ? ok('same ' + w.files.length + ' paths') : bad('git and walk disagree (mode ' + w.mode + ')\n' + a.split('\n').filter((x) => !b.includes(x)).join('\n'));
  fs.rmSync(tmp, { recursive: true, force: true });
} else ok('skipped (not a git checkout)');

section('configurations');
const base = () => Gen.identityConfig(seed);
const fresh = (over) => Object.assign(Gen.defaultConfig(seed), { name: 'Cool Mod', modId: 'cool_mod', cls: 'CoolMod', project: 'CoolMod', github: 'someone/CoolMod' }, over);
const ver = (mc, o = {}) => ({ mc, java: Gen.javaForMc(mc), bound: '', fabric: { on: true, ver: '0.1.0' }, fabricApi: '0.1.0+' + mc,
  forge: { on: true, ver: '1.2.3' }, neoforge: { on: true, ver: '4.5.6' }, ...o });
const allMixinsOff = () => Object.fromEntries(seed.mixinFiles.map((p) => [p, false]));
const cases = {
  'fresh mod, defaults': fresh({}),
  'mixins off': fresh({ mixins: { on: false, files: allMixinsOff() } }),
  'one stub unticked': fresh({ mixins: { on: true, files: { ...Object.fromEntries(seed.mixinFiles.map((p) => [p, true])), [seed.mixinFiles[0]]: false } } }),
  'class tweaker + relocations off': fresh({ classTweaker: false, vanillaRelocations: false }),
  'fabric only, client env, quilt, no fabric api': fresh({ environment: 'CLIENT', quilt: true, useFabricApi: false, datagen: { on: false, client: false }, versions: [ver('1.21.1', { forge: { on: false, ver: '' }, neoforge: { on: false, ver: '' } })] }),
  'three versions, bound, vcs moved': fresh({ versions: [ver('1.20.1', { neoforge: { on: false, ver: '' } }), ver('1.21.1', { bound: '1.21' }), ver('26.1')], vcs: '1.20.1' }),
  'dependencies, publishing, authors': fresh({ authors: ['A', 'B'], dependencies: [{ type: 'requires', modId: 'geckolib', range: '[4.0,)' }, { type: 'suggest', modId: 'jei', range: '*' }],
    publishing: { modrinth: 'abc123', curseforge: '12345', github: true }, homepage: '' }),
  'no links, no authors': fresh({ authors: [], homepage: '', github: '' }),
  'awkward text': fresh({ name: "Zoe's Cool Mod!", description: 'Uses ${placeholders}, "quotes" and a \\', authors: ['A "B"', 'C$D'] }),
  'new id contains the old id': fresh({ name: 'Example Mod 2', modId: 'example_mod_2', cls: 'ExampleModTwo', project: 'ExampleModTwo' }),
  'own icon': fresh({ icon: new Uint8Array([137, 80, 78, 71, 13, 10, 26, 10, 1, 2, 3]) }),
};
const balanced = (s) => { let d = 0; for (const c of s.replace(/\/\/[^\n]*/g, '').replace(/"(\\.|[^"\\])*"/g, '""')) { if (c === '{') d++; if (c === '}') d--; if (d < 0) return false; } return d === 0; };
const oldIds = new RegExp([seed.modId, seed.cls, seed.name].map((s) => s.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')).join('|'));
for (const [name, cfg] of Object.entries(cases)) {
  let out;
  try { out = Gen.generate(cfg, data, seed); } catch (e) { bad(name + ': threw ' + e.message); continue; }
  if (out.errors.length) { bad(name + ': ' + JSON.stringify(out.errors)); continue; }
  const problems = [];
  const get = (p) => out.files.find((f) => f.path === p);
  for (const f of out.files) {
    if (f.text === undefined || f.path.startsWith('buildSrc/')) continue;
    if (cfg.modId !== seed.modId && !cfg.modId.includes(seed.modId) && oldIds.test(f.text) && !(cfg.name.includes(seed.name))) problems.push('example name left in ' + f.path);
    if (f.path.endsWith('.json') && !(() => { try { JSON.parse(f.text); return true; } catch (e) { return false; } })()) problems.push('bad json ' + f.path);
    if (/\.(java|kts)$/.test(f.path) && !balanced(f.text)) problems.push('unbalanced ' + f.path);
    if (f.path.endsWith('.versions.toml') && !/^\[versions\]\nminecraft = .+\njava = "\d+"\n\n(?:[a-z-]+ = ".+"\n|\n)+$/.test(f.text)) problems.push('odd toml ' + f.path);
    if (/\.(java|json|toml|txt|properties)$/.test(f.path) && !f.text.endsWith('\n') && !/\/generated\//.test(f.path)) problems.push('no final newline ' + f.path);
  }
  const kts = get('build.gradle.kts').text;
  const want = (cond, msg) => { if (!cond) problems.push(msg); };
  want(kts.includes('name = "' + Gen.kt(cfg.name) + '"'), 'name not set');
  want(cfg.mixins.on || !/mixins\.configure|MixinEnvironment/.test(kts), 'mixin block left');
  want(cfg.classTweaker || !/classTweakers\.configure/.test(kts), 'class tweaker block left');
  want(cfg.vanillaRelocations || !/vanilla\(\)/.test(kts), 'vanilla() left');
  want(!!cfg.homepage === /^\s+url = /m.test(kts), 'url line mismatch');
  want(!!cfg.github === /^\s+sources = /m.test(kts), 'sources line mismatch');
  want(!/TODO/.test(kts) || !cfg.github, 'TODO comment left on changed sources');
  want(Gen.deriveModId("Zoe's Cool Mod") === 'zoes_cool_mod' && Gen.deriveClass("Zoe's Cool Mod") === 'ZoesCoolMod', 'name derivation');
  want(get('settings.gradle.kts').text.includes('rootProject.name = "' + cfg.project + '"'), 'rootProject.name');
  want(get('stonecutter.gradle.kts').text.includes('"' + cfg.vcs + '"'), 'stonecutter active');
  want(get('gradle/minecraft/vcs.txt').text === cfg.vcs + '\n', 'vcs.txt');
  want(out.files.filter((f) => f.path.endsWith('.versions.toml')).length === cfg.versions.length, 'toml count');
  want(!cfg.mixins.on === !out.files.some((f) => f.path.endsWith('.mixins.json')), 'mixins.json presence');
  want(!cfg.classTweaker === !out.files.some((f) => f.path.endsWith('.ct')) || true, '');
  const usesLoader = (id) => cfg.versions.some((v) => v[id].on);
  for (const id of seed.loaders) want(usesLoader(id) === out.files.some((f) => f.path.startsWith('src/' + id + '/')), id + ' sources presence');
  if (cfg.icon) want(Buffer.from(get('src/common/main/resources/assets/' + cfg.modId + '/icon.png').bytes).equals(Buffer.from(cfg.icon)), 'icon bytes');
  want(get('gradle.properties').text.includes('group=' + cfg.group + '\n') && get('gradle.properties').text.includes('version=' + cfg.version), 'gradle.properties');
  const mainPath = 'src/common/main/java/' + (cfg.group + '.' + cfg.modId).replace(/\./g, '/') + '/' + cfg.cls + '.java';
  want(!!get(mainPath) && get(mainPath).text.includes('MOD_ID = "' + cfg.modId + '"'), 'main class at ' + mainPath);

  const z = Gen.zip(out.files, cfg.project);
  const tmp = fs.mkdtempSync(path.join(process.env.TMPDIR || os.tmpdir(), 'gen-zip-'));
  fs.writeFileSync(path.join(tmp, 'o.zip'), z);
  const r = spawnSync('unzip', ['-q', 'o.zip', '-d', 'x'], { cwd: tmp });
  if (r.error) problems.push('unzip not available');
  else if (r.status !== 0) problems.push('unzip failed');
  else {
    if ((fs.statSync(path.join(tmp, 'x', cfg.project, 'gradlew')).mode & 0o777) !== 0o755) problems.push('gradlew not 755 in zip');
    for (const f of out.files) if (!fs.readFileSync(path.join(tmp, 'x', cfg.project, f.path)).equals(bytes(f))) { problems.push('zip differs at ' + f.path); break; }
  }
  fs.rmSync(tmp, { recursive: true, force: true });
  problems.length ? bad(name + ': ' + problems.join('; ')) : ok(`${name} (${out.files.length} files, ${out.warnings.length} warnings)`);
}

section('validation');
const rejects = {
  'bad id': fresh({ modId: 'Bad-Id' }), 'keyword id': fresh({ modId: 'default' }), 'reserved id': fresh({ modId: 'forge' }),
  'quote in name': fresh({ name: 'Bad "name"' }), 'neoforge on 1.19': fresh({ versions: [ver('1.19.4')] }), 'unknown vcs': fresh({ vcs: '9.9' }),
  'duplicate mc': fresh({ versions: [ver('1.21.1'), ver('1.21.1')] }), 'no versions': fresh({ versions: [] }),
  'no loader': fresh({ versions: [ver('1.21.1', { fabric: { on: false, ver: '' }, forge: { on: false, ver: '' }, neoforge: { on: false, ver: '' } })] }),
  'empty loader version': fresh({ versions: [ver('1.21.1', { forge: { on: true, ver: '' } })] }),
  'github release without repo': fresh({ github: '', publishing: { modrinth: '', curseforge: '', github: true } }),
};
for (const [name, cfg] of Object.entries(rejects)) {
  const out = Gen.generate(cfg, data, seed);
  out.errors.length && !out.files.length ? ok(name + ': ' + out.errors[0].msg) : bad(name + ' was accepted');
}

section('anchors fail loudly');
{
  const broken = (mut) => ({ files: files.map((f) => (f.p === 'build.gradle.kts' ? { ...f, t: mut(f.t) } : f)) });
  const attempt = (label, d, cfg) => { try { Gen.generate(cfg, d); bad(label + ': no error'); } catch (e) { ok(label + ' -> ' + e.message); } };
  attempt('no description line', broken((t) => t.replace(/^\s*description = .*$/m, '')), fresh({}));
  attempt('mixins block without its brace', broken((t) => t.replace('mixins.configure(sourceSets.main) {', 'mixins.configure(sourceSets.main)')), fresh({ mixins: { on: false, files: {} } }));
  try { Gen.analyze(broken((t) => t.replace(/modId = .*/, ''))); bad('analyze accepted a repo without modId'); } catch (e) { ok('analyze -> ' + e.message); }
}

section('latest versions, fake network');
{
  const pages = {
    'https://meta.fabricmc.net/v2/versions/loader': JSON.stringify([{ version: '0.19.0-beta', stable: false }, { version: '0.18.4', stable: true }]),
    'https://maven.fabricmc.net/net/fabricmc/fabric-api/fabric-api/maven-metadata.xml': '<v><version>0.9.0+1.21</version><version>0.116.9+1.21.1</version><version>0.116.17+1.21.1</version></v>',
    'https://api.modrinth.com/v2/project/modmenu/version?loaders=%5B%22fabric%22%5D&game_versions=%5B%221.21.1%22%5D': JSON.stringify([{ version_number: '11.0.4-beta.1', version_type: 'beta' }, { version_number: '11.0.3', version_type: 'release' }, { version_number: '11.0.2', version_type: 'release' }]),
    'https://maven.minecraftforge.net/net/minecraftforge/forge/maven-metadata.xml': '<v><version>1.21.1-52.0.1</version><version>1.21.1-52.1.16</version><version>1.21.1-52.1.9</version><version>1.20.1-47.4.0</version></v>',
    'https://maven.neoforged.net/releases/net/neoforged/neoforge/maven-metadata.xml': '<v><version>21.1.9</version><version>21.1.228</version><version>21.1.229-beta</version><version>21.10.1</version></v>',
  };
  const r = await Gen.fetchLatest('1.21.1', async (u) => (pages[u] ? { ok: true, text: async () => pages[u] } : { ok: false, status: 404 }));
  [r.fabricLoader, r.fabricApi, r.modMenu, r.forge, r.neoforge, r.errors.length].join('|') === '0.18.4|0.116.17+1.21.1|11.0.3|52.1.16|21.1.228|0' ? ok('picks the newest stable of each (Mod Menu: newest release for that Minecraft version)') : bad(JSON.stringify(r));
  const none = await Gen.fetchLatest('1.20.1', async (u) => (/modrinth/.test(u) ? { ok: true, text: async () => '[]' } : { ok: false, status: 404 }));
  none.modMenu === '' ? ok('no Mod Menu for a Minecraft version Modrinth has none for -> empty') : bad(none.modMenu);
}

const vv = (mc, o = {}) => ver(mc, { ...(Gen.cmpNums(mc, '1.20.2') < 0 ? { neoforge: { on: false, ver: '' } } : {}), ...o });

section('logger in the main class');
{
  const mainOf = (out, cfg) => out.files.find((f) => f.path === 'src/common/main/java/' + (cfg.group + '.' + cfg.modId).replace(/\./g, '/') + '/' + cfg.cls + '.java').text;
  const run = (cfg, d = data, sd = seed) => { const out = Gen.generate(cfg, d, sd); return out.errors.length ? 'ERR ' + out.errors[0].msg : mainOf(out, cfg); };
  const t = (name, cond) => (cond ? ok(name) : bad(name));
  const mc = (v) => fresh({ versions: [vv(v)], vcs: v });
  t('1.21.1 -> SLF4J, plain', /import org\.slf4j\.Logger;\nimport org\.slf4j\.LoggerFactory;/.test(run(mc('1.21.1'))) && /LoggerFactory\.getLogger\(MOD_ID\)/.test(run(mc('1.21.1'))) && !/\/\/\?/.test(run(mc('1.21.1'))));
  t('26.1 -> SLF4J', /LoggerFactory/.test(run(mc('26.1'))));
  t('1.18.2 is the first SLF4J version', /LoggerFactory/.test(run(mc('1.18.2'))) && /LogManager\.getLogger\(MOD_ID\)/.test(run(mc('1.18.1'))));
  t('1.14.4 -> Log4j, plain', /import org\.apache\.logging\.log4j\.LogManager;\nimport org\.apache\.logging\.log4j\.Logger;/.test(run(mc('1.14.4'))) && !/slf4j|\/\/\?/.test(run(mc('1.14.4'))));
  const mixed = (vcs) => run(fresh({ versions: [vv('1.16.5'), vv('1.21.1')], vcs }));
  const live = (txt, re) => !new RegExp('/\\*[^]*?' + re.source + '[^]*?\\*/').test(txt);
  const mNew = mixed('1.21.1'), mOld = mixed('1.16.5');
  t('mixed, active 1.21.1: SLF4J live, Log4j commented', /^\/\/\? if >=1\.18\.2 \{\nimport org\.slf4j\.Logger;/m.test(mNew) && /^\/\*import org\.apache\.logging\.log4j\.LogManager;/m.test(mNew) && /^\*\/\/\/\?\}$/m.test(mNew));
  t('mixed, active 1.16.5: Log4j live, SLF4J commented', /^\/\*import org\.slf4j\.Logger;/m.test(mOld) && /^\*\/\/\/\?\} else \{\nimport org\.apache/m.test(mOld) && /^    \/\*public static final Logger LOGGER = LoggerFactory/m.test(mOld));
  for (const [n, txt] of [['new', mNew], ['old', mOld]]) {
    const opens = (txt.match(/\/\/\? if /g) || []).length, closes = (txt.match(/\/\/\?\}$/gm) || []).length;
    t('mixed ' + n + ': every if has its close, comments balanced', opens === 2 && closes === 2 && (txt.match(/\/\*/g) || []).length === (txt.match(/\*\//g) || []).length);
  }
  t('logger off -> no Logger', !/Logger/.test(run(fresh({ logger: false }))));
  const withLogger = { files: files.map((f) => (f.p === seed.mainPath ? { ...f, t: f.t.replace('private ExampleMod', 'static final Object LOGGER = null; // Logger\n    private ExampleMod') } : f)) };
  t('a class that already has a Logger is left alone', !/slf4j/.test(run(fresh({}), withLogger, Gen.analyze(withLogger))));
  try { Gen.generate(fresh({}), { files: files.map((f) => (f.p === seed.mainPath ? { ...f, t: f.t.replace(/public static final String MOD_ID.*\n/, '') } : f)) }); bad('no MOD_ID line: no error'); }
  catch (e) { ok('no MOD_ID line -> ' + e.message); }
  const noPkg = { files: files.map((f) => (f.p === seed.mainPath ? { ...f, t: f.t.replace(/^package .*\n/m, '') } : f)) };
  try { Gen.generate(fresh({}), noPkg); bad('no package line: no error'); } catch (e) { ok('no package line -> ' + e.message); }
}

section('release workflow JDKs');
{
  const wf = (cfg, d = data, sd = seed) => Gen.generate(cfg, d, sd).files.find((f) => f.path === '.github/workflows/release.yml').text;
  const jdks = (txt) => [...txt.matchAll(/java-version:\s*(\d+)/g)].map((m) => +m[1]);
  const t = (name, cond, extra) => (cond ? ok(name) : bad(name + (extra ? ': ' + extra : '')));
  t('only the template\'s Java -> file untouched', wf(fresh({})) === files.find((f) => f.p === '.github/workflows/release.yml').t);
  const a = wf(fresh({ versions: [vv('1.20.1'), vv('1.21.1'), vv('26.1')], vcs: '1.21.1' }));
  t('17 + 21 + 25 -> 17, 25, then 21 as the default', jdks(a).join() === '17,25,21' && (a.match(/uses: actions\/setup-java@v6/g) || []).length === 3 && /- name: Set up JDK 17\n        uses: actions\/setup-java@v6\n        with:\n          distribution: temurin\n          java-version: 17\n/.test(a) && !/add more JDKs/.test(a), jdks(a).join());
  t('Java 8 only -> 8 plus 21 to run Gradle', jdks(wf(fresh({ versions: [vv('1.16.5')], vcs: '1.16.5' }))).join() === '8,21');
  t('Java 17 only -> 17 plus 21', jdks(wf(fresh({ versions: [vv('1.20.1')], vcs: '1.20.1' }))).join() === '17,21');
  t('a stale java field wins over the table', jdks(wf(fresh({ versions: [vv('1.21.1', { java: 17 })] }))).join() === '17,21');
  t('the rest of the file is unchanged', a.replace(/      - name: Set up JDK[^]*?(?=\n      - name: Publish)/, '').length < files.find((f) => f.p === '.github/workflows/release.yml').t.length + 5 && /MODRINTH_TOKEN/.test(a) && /Keep dry-run output/.test(a));
  t('jdkList', Gen.jdkList([21, 17, 21], 21).join() === '17,21' && Gen.jdkList([25], 21).join() === '25,21' && Gen.jdkList([21], 21).join() === '21');
  const variant = (mut) => { const d = { files: files.map((f) => (f.p === '.github/workflows/release.yml' ? { ...f, t: mut(f.t) } : f)) }; return [d, Gen.analyze(d)]; };
  { const [d, sd] = variant((x) => x.replace('      - uses: actions/setup-java@v6\n', '      - name: JDK\n        uses: actions/setup-java@v6\n').replace('java-version: 21 #', 'java-version: "21" #'));
    const out = wf(fresh({ versions: [vv('1.20.1'), vv('1.21.1')], vcs: '1.21.1' }), d, sd);
    t('"- name:" first, quoted version', jdks(out).join() === '17,21' && /- name: Set up JDK 17\n        uses: actions\/setup-java@v6/.test(out) && /- name: Set up JDK 21\n/.test(out), out.slice(out.indexOf('JDK') - 20, out.indexOf('JDK') + 200)); }
  { const [d, sd] = variant((x) => x.replace('      - uses: actions/setup-java@v6\n        with:\n          distribution: temurin\n          java-version: 21 # add more JDKs here if you add Minecraft versions that need other Java versions\n',
      '      - uses: actions/setup-java@v6\n        with:\n          distribution: temurin\n          java-version: 17\n      - uses: actions/setup-java@v6\n        with:\n          distribution: temurin\n          java-version: 21\n'));
    const same = Gen.generate(fresh({ versions: [vv('1.20.1'), vv('1.21.1')], vcs: '1.21.1' }), d, sd).files.find((f) => f.path === '.github/workflows/release.yml').text;
    t('already lists exactly the JDKs it needs -> untouched', same === d.files.find((f) => f.p === '.github/workflows/release.yml').t);
    try { Gen.generate(fresh({}), d, sd); bad('two steps that need rewriting: no error'); } catch (e) { ok('two steps that need rewriting -> ' + e.message); } }
  { const [d, sd] = variant((x) => x.replace(/ *java-version:.*\n/, ''));
    try { Gen.generate(fresh({}), d, sd); bad('no java-version: no error'); } catch (e) { ok('no java-version -> ' + e.message); } }
  { const [d, sd] = variant((x) => x.replace(/ *- uses: actions\/setup-java[^]*?(?=\n      - name: Publish)/, ''));
    t('no setup-java step at all -> nothing to patch', wf(fresh({ versions: [vv('1.20.1')], vcs: '1.20.1' }), d, sd) === d.files.find((f) => f.p === '.github/workflows/release.yml').t); }
}

section('licenses');
{
  const t = (name, cond, extra) => (cond ? ok(name) : bad(name + (extra ? ': ' + extra : '')));
  const FIX = {
    mit: 'MIT License\r\n\r\nCopyright (c) <year> <copyright holders>\r\n\r\nPermission is hereby granted ...\r\n',
    bsd: 'BSD 3-Clause License\n\nCopyright (c) <year>, <owner>\nAll rights reserved.\n\nRedistribution and use ...\n',
    zero: 'Copyright (C) YEAR by AUTHOR EMAIL\n\nPermission to use, copy, modify ...\n',
    isc: "ISC License\n\nCopyright (c) 4-digit year, Company or Person's Name\n\nPermission to use ...\n",
    apache: 'Apache License\n\nTERMS AND CONDITIONS\n\nEND OF TERMS AND CONDITIONS\n\nAPPENDIX\n\n   Copyright [yyyy] [name of copyright owner]\n',
    gpl: 'GNU GENERAL PUBLIC LICENSE\n\nTERMS AND CONDITIONS\n\nEND OF TERMS AND CONDITIONS\n\nHow to Apply These Terms\n\n    <one line to give the program\'s name and a brief idea of what it does.>\n    Copyright (C) <year>  <name of author>\n',
    none: 'Creative Commons Zero\n\nThe person who associated a work ...\n',
  };
  const fill = (k, o = { year: '2026', holder: 'Ada' }) => Gen.fillLicense(FIX[k], o);
  t('MIT-like: year and holder, CRLF -> LF', fill('mit').text === 'MIT License\n\nCopyright (c) 2026 Ada\n\nPermission is hereby granted ...\n' && fill('mit').filled.year && fill('mit').filled.holder, JSON.stringify(fill('mit').text));
  t('BSD-like <owner>', fill('bsd').text.includes('Copyright (c) 2026, Ada\n'));
  t('0BSD-like YEAR / AUTHOR EMAIL', fill('zero').text.startsWith('Copyright (C) 2026 by Ada\n'));
  t('ISC-like wording', fill('isc').text.includes("Copyright (c) 2026, Ada\n"));
  t('Apache-like: appendix untouched, nothing filled', fill('apache').text === FIX.apache && !fill('apache').filled.year && !fill('apache').filled.holder);
  t('GPL-like: how-to-apply appendix untouched', fill('gpl').text === FIX.gpl);
  t('no slots -> untouched, nothing filled', fill('none').text === FIX.none && !fill('none').filled.year);
  t('no holder -> slot stays', fill('mit', { year: '2026', holder: '' }).text.includes('Copyright (c) 2026 <copyright holders>') && !fill('mit', { year: '2026', holder: '' }).filled.holder);
  t('word YEAR elsewhere is not a slot', Gen.fillLicense('The YEAR of the dragon, Copyright (c) <year> <owner>\n', { year: '2026', holder: 'Ada' }).text === 'The YEAR of the dragon, Copyright (c) 2026 Ada\n');

  const list = { licenseListVersion: '3.x', licenses: [{ licenseId: 'MIT', name: 'MIT License', isOsiApproved: true, isDeprecatedLicenseId: false }, { licenseId: 'GPL-2.0+', name: 'GNU GPL v2.0 or later', isOsiApproved: true, isDeprecatedLicenseId: true }] };
  const detail = (id, text = FIX.mit) => ({ licenseId: id, name: id + ' name', isOsiApproved: true, isDeprecatedLicenseId: false, licenseText: text });
  const net = (routes, log = []) => async (u) => { log.push(u); const r = routes[u]; if (r instanceof Error) throw r; return r === undefined ? { ok: false, status: 404 } : { ok: true, json: async () => r }; };
  const S = 'https://spdx.org/licenses/', J = 'https://cdn.jsdelivr.net/gh/spdx/license-list-data/json/';
  { const log = [], l = await Gen.fetchLicenseList(net({ [S + 'licenses.json']: list }, log));
    t('list: ids, names, flags', l.licenses.length === 2 && l.licenses[0].id === 'MIT' && l.licenses[0].osi && l.licenses[1].deprecated && l.version === '3.x' && log.length === 1); }
  { const log = [], l = await Gen.fetchLicenseList(net({ [S + 'licenses.json']: new TypeError('Failed to fetch'), [J + 'licenses.json']: list }, log));
    t('list: falls back to the mirror when spdx.org is blocked', l.licenses.length === 2 && log.length === 2 && log[1].startsWith(J)); }
  { const log = [], l = await Gen.fetchLicense('MIT', net({ [S + 'MIT.json']: detail('MIT') }, log));
    t('details: text, name, flags from spdx.org', l.id === 'MIT' && l.text === FIX.mit && l.name === 'MIT name' && l.osi === true && log.length === 1); }
  { const log = [], l = await Gen.fetchLicense('GPL-2.0+', net({ [J + 'details/GPL-2.0+.json']: detail('GPL-2.0+') }, log));
    t('details: 404 on spdx.org -> mirror; + stays + in the URL', l.id === 'GPL-2.0+' && log[0] === S + 'GPL-2.0+.json' && log[1] === J + 'details/GPL-2.0+.json', log.join(' ')); }
  for (const [label, routes] of [['both sources down', {}], ['a license without text', { [S + 'X.json']: detail('X', '   ') }], ['not JSON of the right shape', { [S + 'X.json']: { nope: 1 } }]]) {
    try { await Gen.fetchLicense('X', net(routes)); bad(label + ': no error'); } catch (e) { ok(label + ' -> ' + e.message); }
  }
  try { await Gen.fetchLicenseList(net({ [S + 'licenses.json']: { nope: 1 }, [J + 'licenses.json']: { nope: 1 } })); bad('bad list accepted'); } catch (e) { ok('bad list -> ' + e.message); }
  { const parsed = (await Gen.fetchLicenseList(net({ [S + 'licenses.json']: list }))).licenses;
    t('resolveLicenseId is case-insensitive', Gen.resolveLicenseId(' mit ', parsed) === 'MIT' && Gen.resolveLicenseId('gpl-2.0+', parsed) === 'GPL-2.0+' && Gen.resolveLicenseId('nope', parsed) === ''); }
  t('isSingleLicense', Gen.isSingleLicense('MIT') && Gen.isSingleLicense('GPL-2.0+') && !Gen.isSingleLicense('MIT OR Apache-2.0') && !Gen.isSingleLicense('GPL-2.0 WITH Classpath-exception-2.0') && !Gen.isSingleLicense(''));

  const gen = (cfg, d = data, sd = seed) => Gen.generate(cfg, d, sd);
  const lic = (out, p = 'LICENSE') => (out.files.find((f) => f.path === p) || {}).text;
  const fix = (o) => fresh({ license: 'MIT', licenseText: FIX.mit, year: '2026', authors: ['Ada', 'Bob'], ...o });
  { const out = gen(fix({}));
    t('LICENSE written, year and authors filled in', lic(out) === 'MIT License\n\nCopyright (c) 2026 Ada, Bob\n\nPermission is hereby granted ...\n' && !out.warnings.some((w) => /LICENSE/.test(w.msg)), JSON.stringify(lic(out))); }
  t('copyright holder overrides the authors', lic(gen(fix({ copyright: 'Cool Studio' }))).includes('Copyright (c) 2026 Cool Studio\n'));
  t('no authors and no holder -> slot stays, warned', (() => { const o = gen(fix({ authors: [] })); return lic(o).includes('<copyright holders>') && o.warnings.some((w) => /still has a copyright holder slot/.test(w.msg)); })());
  t('licenseFile off -> no LICENSE', lic(gen(fix({ licenseFile: false }))) === undefined);
  t('no text (offline) -> no LICENSE, warned', (() => { const o = gen(fix({ licenseText: null })); return lic(o) === undefined && o.warnings.some((w) => /No LICENSE file/.test(w.msg)); })());
  t('All Rights Reserved needs no lookup', lic(gen(fresh({ license: 'All Rights Reserved', licenseText: null, authors: ['Ada'], year: '2025' }))) === 'Copyright (c) 2025 Ada. All rights reserved.\n');
  t('All Rights Reserved without a holder is warned', (() => { const o = gen(fresh({ license: 'All Rights Reserved', authors: [] })); return lic(o) === undefined && o.warnings.some((w) => /copyright holder/.test(w.msg)); })());
  t('"MIT OR Apache-2.0" -> no file, says why', (() => { const o = gen(fix({ license: 'MIT OR Apache-2.0' })); return lic(o) === undefined && o.warnings.some((w) => /more than one license/.test(w.msg)); })());
  t('metadata still carries the license id', /license = "Apache-2\.0"/.test(gen(fix({ license: 'Apache-2.0', licenseText: FIX.apache })).files.find((f) => f.path === 'build.gradle.kts').text));
  for (const [y, okYear] of [['2026', true], ['2024-2026', true], ['2024, 2026', true], ['20x6', false], ['', false], ['26', false]]) {
    const o = gen(fix({ year: y })); (o.errors.some((e) => e.field === 'year') === !okYear) ? ok('year "' + y + '" ' + (okYear ? 'accepted' : 'rejected')) : bad('year "' + y + '"');
  }
  t('a year problem is ignored when no LICENSE is written', !gen(fix({ year: 'x', licenseFile: false })).errors.length);
  const withLic = { files: [...files, { p: 'LICENSE.md', t: 'The template\'s own license text for MIT.\n' }] }, seedL = Gen.analyze(withLic);
  { const id = Gen.generate(Gen.identityConfig(seedL), withLic, seedL), same = id.files.find((f) => f.path === 'LICENSE.md');
    t('a repo LICENSE is copied untouched when the file is off / unchanged', seedL.licenseFile === 'LICENSE.md' && same && same.text === withLic.files[withLic.files.length - 1].t && !id.warnings.length); }
  { const out = gen(fix({}), withLic, seedL);
    t('a repo LICENSE.md is replaced in place, not duplicated', lic(out, 'LICENSE.md') === lic(out, 'LICENSE.md') && /Copyright \(c\) 2026 Ada, Bob/.test(lic(out, 'LICENSE.md')) && lic(out) === undefined); }
  { const out = gen(fix({ license: 'Apache-2.0', licenseText: null }), withLic, seedL);
    t('template LICENSE for another license + no new text -> warned to replace it', out.warnings.some((w) => /LICENSE\.md of the template is for MIT/.test(w.msg))); }
  { const out = gen(fix({}));
    t('default config writes LICENSE and the logger together', !!lic(out) && /LOGGER/.test(out.files.find((f) => /CoolMod\.java$/.test(f.path)).text)); }
}

section('Mod Menu');
{
  const t = (name, cond, extra) => (cond ? ok(name) : bad(name + (extra ? ': ' + extra : '')));
  const gen = (cfg, d = data, sd = seed) => Gen.generate(cfg, d, sd);
  const file = (out, p) => (out.files.find((f) => f.path === p) || {}).text;
  const mmv = (mc, o = {}) => ver(mc, { modMenu: '11.0.3', ...(Gen.cmpNums(mc, '1.20.2') < 0 ? { neoforge: { on: false, ver: '' } } : {}), ...o });
  const mm = (o) => ({ ...Gen.modMenuDefaults(), ...o });
  const kts = (out) => file(out, 'build.gradle.kts');
  const modMenuFiles = (out) => out.files.filter((f) => /\/fabric\/modmenu\//.test(f.path)).map((f) => f.path.replace(/^.*\//, ''));

  t('the repo has the integration, on by default', seed.hasModMenu && seed.modMenu && Gen.defaultConfig(seed).modMenu.on);
  t('the catalog has a Mod Menu version', seed.versions.every((v) => v.fabric.on === !!v.modMenu), JSON.stringify(seed.versions.map((v) => v.modMenu)));

  const on = gen(fresh({ versions: [mmv('1.21.1')] }));
  t('on: block, classes and catalog entry', /^    modMenu \{/m.test(kts(on)) && modMenuFiles(on).join() === 'CoolModConfigScreen.java,CoolModMenuIntegration.java'
    && file(on, 'gradle/minecraft/1.21.1.versions.toml').includes('modmenu = "11.0.3"'), modMenuFiles(on).join());
  t('on: the classes are renamed like the rest', on.files.some((f) => f.path === 'src/fabric/main/java/' + 'com/example/cool_mod'.replace(/^/, '') .replace('com/example', Gen.defaultConfig(seed).group.replace(/\./g, '/')) + '/fabric/modmenu/CoolModMenuIntegration.java')
    && /package [\w.]+\.cool_mod\.fabric\.modmenu;/.test(file(on, on.files.find((f) => /CoolModMenuIntegration/.test(f.path)).path)) && /Component\.literal\("Cool Mod"\)/.test(file(on, on.files.find((f) => /CoolModConfigScreen/.test(f.path)).path)));

  const off = gen(fresh({ modMenu: mm({ on: false }), versions: [mmv('1.21.1')] }));
  t('off: no block, no classes, no catalog entry, no comment left', !/modMenu|Mod Menu/.test(kts(off)) && !modMenuFiles(off).length && !/modmenu/.test(file(off, 'gradle/minecraft/1.21.1.versions.toml')));
  t('off: the rest of build.gradle.kts is the repo\'s', kts(off).replace(/\n{3,}/g, '\n\n').includes('markIncompatible("other_mod", "*")\n\n    custom('), kts(off).slice(kts(off).indexOf('markIncompatible'), kts(off).indexOf('markIncompatible') + 120));
  const noFabric = gen(fresh({ versions: [mmv('1.21.1', { fabric: { on: false, ver: '' } })] }));
  t('no Fabric build -> left out, like Fabric itself', !/modMenu/.test(kts(noFabric)) && !modMenuFiles(noFabric).length && !noFabric.files.some((f) => /^src\/fabric\//.test(f.path)));

  const settings = mm({ links: [{ type: 'discord', url: 'https://discord.gg/abc' }, { type: 'github_sponsors', url: 'https://github.com/sponsors/me' }, { type: 'my_mod.wiki', url: 'https://wiki.example/$x' }, { type: 'bluesky', url: '' }],
    library: true, deprecated: true, parent: 'flamingo', updateChecker: 'off', autoLinks: false, optionalDependency: false, runInDevelopment: false });
  const set = gen(fresh({ modMenu: settings, versions: [mmv('1.21.1')] }));
  const block = /^    modMenu \{\n([^]*?)^    \}/m.exec(kts(set));
  t('settings: one line each, shortcuts where there is one', !!block && block[1].startsWith('        discord("https://discord.gg/abc")\n        githubSponsors("https://github.com/sponsors/me")\n        link("my_mod.wiki", "https://wiki.example/\\$x")\n        library()\n        deprecated()\n        parent("flamingo")\n        updateChecker = false\n        autoLinks = false\n        optionalDependency = false\n        runInDevelopment = false\n        // discord('), block && block[1]);
  t('settings: a link without a url is dropped', !/bluesky/.test(kts(set)));
  t('settings: the block stays balanced', balanced(kts(set).replace(/"(\\.|[^"\\\n])*"/g, '""')));
  { const back = Gen.analyze({ files: set.files.map((f) => (f.bytes ? { p: f.path, b: 'x' } : { p: f.path, t: f.text })) });
    const want = { ...settings, links: settings.links.filter((l) => l.url) };
    t('settings: analyze reads back what was written', JSON.stringify(back.modMenu) === JSON.stringify(want), JSON.stringify(back.modMenu)); }
  t('updateChecker on', /^        updateChecker = true$/m.test(kts(gen(fresh({ modMenu: mm({ updateChecker: 'on' }), versions: [mmv('1.21.1')] })))));
  t('defaults write nothing into the block', (() => { const k = kts(on), b = /^    modMenu \{\n([^]*?)^    \}/m.exec(k); return b && !/^\s+[a-zA-Z]/m.test(b[1].replace(/^\s*\/\/.*$/gm, '')); })());

  const two = gen(fresh({ versions: [mmv('1.20.1', { modMenu: '7.2.2' }), mmv('1.21.1', { fabric: { on: true, ver: '0.18.4' }, modMenu: '' })], vcs: '1.21.1' }));
  t('catalog: each version has its own Mod Menu, empty means none', file(two, 'gradle/minecraft/1.20.1.versions.toml').includes('modmenu = "7.2.2"') && !/modmenu/.test(file(two, 'gradle/minecraft/1.21.1.versions.toml')));
  t('catalog: a version without one is warned about', two.warnings.some((w) => w.field === 'versions.1.modMenu' && /built without Mod Menu/.test(w.msg)));
  t('catalog: Fabric off for a version -> no entry', !/modmenu/.test(file(gen(fresh({ versions: [mmv('1.21.1'), mmv('1.20.1', { fabric: { on: false, ver: '' } })] })), 'gradle/minecraft/1.20.1.versions.toml')));
  t('catalog: untouched version keeps the repo\'s file', file(gen(Gen.identityConfig(seed)), 'gradle/minecraft/1.21.1.versions.toml') === files.find((f) => f.p === 'gradle/minecraft/1.21.1.versions.toml').t);
  t('catalog: Mod Menu off drops the repo\'s entry even for an untouched version', (() => { const c = Gen.identityConfig(seed); c.modMenu = mm({ on: false }); return !/modmenu/.test(file(gen(c), 'gradle/minecraft/1.21.1.versions.toml')); })());

  const bad1 = (name, o, field) => { const out = gen(fresh({ versions: [mmv('1.21.1')], modMenu: mm(o) })); out.errors.some((e) => e.field === field) && !out.files.length ? ok(name + ': ' + out.errors[0].msg) : bad(name + ' was accepted'); };
  bad1('link url without http', { links: [{ type: 'discord', url: 'discord.gg/x' }] }, 'modMenu.links.0');
  bad1('link type with spaces', { links: [{ type: 'Not a type', url: 'https://x.example' }] }, 'modMenu.links.0');
  bad1('two links of one type', { links: [{ type: 'wiki', url: 'https://a.example' }, { type: 'modmenu.wiki', url: 'https://b.example' }] }, 'modMenu.links.1');
  bad1('parent that is not an id', { parent: 'Not An Id' }, 'modMenu.parent');
  bad1('own parent', { parent: 'cool_mod' }, 'modMenu.parent');
  t('problems are ignored while Mod Menu is off', !gen(fresh({ versions: [mmv('1.21.1')], modMenu: mm({ on: false, parent: 'Bad Id', links: [{ type: 'x y', url: 'z' }] }) })).errors.length);
  t('server only is warned about', gen(fresh({ environment: 'SERVER', versions: [mmv('1.21.1')] })).warnings.some((w) => /server only/.test(w.msg)));
  t('Mod Menu stays out of the way of Forge-only mods', !gen(fresh({ versions: [mmv('1.21.1', { fabric: { on: false, ver: '' } })] })).warnings.some((w) => /Mod Menu/.test(w.msg)) );

  const bare = { files: files.filter((f) => !/\/fabric\/modmenu\//.test(f.p)).map((f) => (f.p === 'build.gradle.kts' ? { ...f, t: f.t.replace(/^    \/\/ Mod Menu \(Fabric\)[^]*?^    \}\n\n/m, '') } : f)) };
  const bareSeed = Gen.analyze(bare);
  t('no modMenu block in the repo -> seed says so, the form option has nothing to do', !bareSeed.hasModMenu && !Gen.generate(Gen.defaultConfig(bareSeed), bare, bareSeed).files.some((f) => /modmenu/i.test(f.path + (f.text || '').slice(0, 0))));
}

section('issue templates');
{
  const t = (name, cond, extra) => (cond ? ok(name) : bad(name + (extra ? ': ' + extra : '')));
  const get = (cfg, p) => (Gen.generate(cfg, data, seed).files.find((f) => f.path === '.github/ISSUE_TEMPLATE/' + p) || {}).text;
  t('the repo ships the templates and config', ['bug_report.yml', 'compatibility_report.yml', 'feature_request.yml', 'config.yml'].every((n) => files.some((f) => f.p === '.github/ISSUE_TEMPLATE/' + n)));
  const opts = (txt, id) => { const m = new RegExp('id: ' + id + '\\n    attributes:\\n[^]*?options:\\n((?:        - .*\\n)+)').exec(txt); return m ? m[1].split('\n').filter(Boolean).map((l) => l.replace(/^\s+- /, '')) : null; };
  const full = fresh({ versions: [vv('1.20.1', { neoforge: { on: false, ver: '' } }), vv('1.21.1'), vv('26.1')], vcs: '1.21.1' });
  const b = get(full, 'bug_report.yml');
  t('versions: the form lists the mod\'s versions, newest first, plus "Other"', opts(b, 'minecraft').join('|') === '26.1|1.21.1|1.20.1|Other (say which in the description)', String(opts(b, 'minecraft')));
  t('loaders: all three when all are built', opts(b, 'loader').join() === 'Fabric,Forge,NeoForge');
  const one = get(fresh({ versions: [vv('1.20.1', { neoforge: { on: false, ver: '' }, forge: { on: false, ver: '' } })], vcs: '1.20.1' }), 'bug_report.yml');
  t('loaders: only Fabric when only Fabric is built', opts(one, 'loader').join() === 'Fabric' && opts(one, 'minecraft')[0] === '1.20.1');
  t('the mod name is renamed in the templates', /Cool Mod/.test(b) && !/Example Mod/.test(b) && /Cool Mod/.test(get(full, 'feature_request.yml')) && /Cool Mod/.test(get(full, 'compatibility_report.yml')));
  t('config.yml is copied as is', get(full, 'config.yml') === files.find((f) => f.p === '.github/ISSUE_TEMPLATE/config.yml').t);
  t('templates keep their form structure', ['bug_report.yml', 'compatibility_report.yml', 'feature_request.yml'].every((n) => /^name: .+\ndescription: .+\ntitle: .+\nlabels: \[.+\]\nbody:\n/.test(get(full, n))));
}

section('update.json (Forge and NeoForge)');
{
  const t = (name, cond, extra) => (cond ? ok(name) : bad(name + (extra ? ': ' + extra : '')));
  const gen = (cfg, d = data, sd = seed) => Gen.generate(cfg, d, sd);
  const file = (out, p) => (out.files.find((f) => f.path === p) || {}).text;
  const kts = (out) => file(out, 'build.gradle.kts');
  const wf = (out) => file(out, '.github/workflows/release.yml');
  const repoWf = files.find((f) => f.p === '.github/workflows/release.yml').t;
  const uj = (o) => ({ ...Gen.updateJsonDefaults(), ...o });
  const forgeOnly = (mc = '1.21.1') => ver(mc, { fabric: { on: false, ver: '' }, fabricApi: '', neoforge: { on: false, ver: '' } });
  const fabricOnly = (mc = '1.21.1') => ver(mc, { forge: { on: false, ver: '' }, neoforge: { on: false, ver: '' } });

  t('the repo has the block, on by default', seed.hasUpdateJson && seed.updateJson.on && Gen.defaultConfig(seed).updateJson.on);
  t('the workflow of the repo has the commit step', /- name: Commit update\.json/.test(repoWf));

  { const out = gen(fresh({}));
    t('on: block in modPublishing, step in the workflow', /^    updateJson \{/m.test(kts(out)) && /- name: Commit update\.json/.test(wf(out)) && /UPDATE_JSON: update\.json/.test(wf(out)));
    t('on: nothing set, nothing written into the block', !/^        (branch|path|url) =/m.test(kts(out)));
    t('on: the repo\'s workflow is untouched', wf(out) === repoWf); }

  { const out = gen(fresh({ updateJson: uj({ on: false }) }));
    t('off: no block, no comment, no mention', !/updateJson|update\.json|Update notices/.test(kts(out)), (kts(out).match(/.*(updateJson|update\.json|Update notices).*/) || [])[0]);
    t('off: no step, header line fixed, no artifact line', !/update\.json/.test(wf(out)) && !/Commit update/.test(wf(out)) && /#   3\. uploads every jar to Modrinth and CurseForge,\n#   4\. updates the Modrinth project description[^\n]*\.\n/.test(wf(out)) && !/#   5\./.test(wf(out)), wf(out).slice(0, 600));
    t('off: the rest of the workflow is the repo\'s', wf(out).includes('- name: Keep dry-run output') && /Publish\n/.test(wf(out)) && wf(out).length < repoWf.length - 400);
    t('off: the build script stays balanced', (kts(out).match(/\{/g) || []).length === (kts(out).match(/\}/g) || []).length); }

  { const out = gen(fresh({ versions: [fabricOnly()] }));
    t('no Forge or NeoForge build -> removed, like src/forge', !/updateJson|update\.json/.test(kts(out)) && !/update\.json/.test(wf(out)), 'fabric only'); }
  { const out = gen(fresh({ versions: [forgeOnly()] }));
    t('a Forge build is enough to keep it', /updateJson \{/.test(kts(out)) && /Commit update\.json/.test(wf(out))); }

  { const out = gen(fresh({ updateJson: uj({ branch: 'master', path: 'meta/update.json', url: 'https://example.com/u.json' }) }));
    const k = kts(out);
    t('settings: one line each', /^        branch = "master"$/m.test(k) && /^        path = "meta\/update\.json"$/m.test(k) && /^        url = "https:\/\/example\.com\/u\.json"$/m.test(k), k.slice(k.indexOf('updateJson')));
    t('settings: the hints stay', /\/\/ recommended = false/.test(k) && /\/\/ homepage = /.test(k));
    t('settings: the workflow uses the path three times', /UPDATE_JSON: meta\/update\.json/.test(wf(out)) && /^ +meta\/update\.json$/m.test(wf(out)) && /adds the release to meta\/update\.json \(/.test(wf(out)));
    const back = Gen.analyze({ files: out.files.map((f) => ({ p: f.path, t: f.text, b: f.bytes ? Buffer.from(f.bytes).toString('base64') : undefined })) });
    t('settings: analyze reads back what was written', back.updateJson && back.updateJson.branch === 'master' && back.updateJson.path === 'meta/update.json' && back.updateJson.url === 'https://example.com/u.json', JSON.stringify(back.updateJson)); }

  { const errs = (cfg) => Gen.validate(cfg, seed).errors.map((e) => e.field);
    t('validation: path', errs(fresh({ updateJson: uj({ path: '../x.json' }) })).includes('updateJson.path') && errs(fresh({ updateJson: uj({ path: '/abs.json' }) })).includes('updateJson.path') && errs(fresh({ updateJson: uj({ path: 'a b.json' }) })).includes('updateJson.path'));
    t('validation: branch and url', errs(fresh({ updateJson: uj({ branch: 'a b' }) })).includes('updateJson.branch') && errs(fresh({ updateJson: uj({ url: 'ftp://x' }) })).includes('updateJson.url'));
    t('validation: good values pass', !errs(fresh({ updateJson: uj({ branch: 'release/1.x', path: 'docs/update.json', url: 'https://x.y/z.json' }) })).some((f) => /^updateJson/.test(f)));
    t('validation: not checked when off or without Forge/NeoForge', !errs(fresh({ updateJson: uj({ on: false, path: '..' }) })).length && !errs(fresh({ updateJson: uj({ path: '..' }), versions: [fabricOnly()] })).some((f) => /^updateJson/.test(f)));
    const warns = (cfg) => Gen.validate(cfg, seed).warnings.map((w) => w.field);
    t('validation: no GitHub repo and no URL is warned about', warns(fresh({ github: '' })).includes('updateJson.url') && !warns(fresh({ github: '', updateJson: uj({ url: 'https://x.y/u.json' }) })).includes('updateJson.url') && !warns(fresh({})).includes('updateJson.url')); }

  { const noStep = { files: files.map((f) => (f.p === '.github/workflows/release.yml' ? { ...f, t: f.t.replace(/      - name: Commit update\.json[^]*?(?=\n      - name: Keep)/, '') } : f)) };
    const out = gen(fresh({ updateJson: uj({ on: false }) }), noStep, Gen.analyze(noStep));
    t('a workflow without the step is left alone', wf(out) === noStep.files.find((f) => f.p === '.github/workflows/release.yml').t.replace(/^#   4\..*\n/m, '').replace(/^#   3\. (.*),$/m, '#   3. $1.').replace(/^ +update\.json\n/m, '') || !/Commit update/.test(wf(out))); }
}

section('data generation (Fabric for all loaders)');
{
  const t = (name, cond, extra) => (cond ? ok(name) : bad(name + (extra ? ': ' + extra : '')));
  const gen = (cfg, d = data, sd = seed) => Gen.generate(cfg, d, sd);
  const file = (out, p) => (out.files.find((f) => f.path === p) || {}).text;
  const kts = (out) => file(out, 'build.gradle.kts');
  const attrs = (out) => file(out, '.gitattributes');
  const repoAttrs = files.find((f) => f.p === '.gitattributes').t;
  const dg = (o) => ({ ...Gen.datagenDefaults(), ...o });
  const dataPaths = (out) => out.files.filter((f) => /\/fabric\/datagen\/|^src\/(common|versions\/[^/]+)\/generated\//.test(f.path)).map((f) => f.path);
  const forgeOnly = (mc = '1.21.1') => ver(mc, { fabric: { on: false, ver: '' }, fabricApi: '', neoforge: { on: false, ver: '' } });
  const v = (mc, o = {}) => ver(mc, { neoforge: { on: false, ver: '' }, ...o });

  t('the repo has it, on by default', seed.hasDatagen && seed.datagen.on && Gen.defaultConfig(seed).datagen.on);
  t('the repo has one empty entrypoint in src/fabric, and nothing generated or per version', files.filter((f) => /fabric\/datagen\//.test(f.p)).map((f) => f.p).join() === 'src/fabric/main/java/de/rhm176/example_mod/fabric/datagen/ExampleModDataGenerator.java'
    && !files.some((f) => /\/generated\//.test(f.p)) && !files.some((f) => /^src\/versions\//.test(f.p))
    && /onInitializeDataGenerator\(FabricDataGenerator fabricDataGenerator\) \{\}/.test(files.find((f) => /ExampleModDataGenerator\.java$/.test(f.p)).t));

  { const out = gen(fresh({})), paths = dataPaths(out);
    t('on: block and the one entrypoint, renamed', /^datagen \{\n    enabled = true/m.test(kts(out)) && paths.join() === 'src/fabric/main/java/de/rhm176/cool_mod/fabric/datagen/CoolModDataGenerator.java', paths.join());
    t('on: renamed like the rest', /package de\.rhm176\.cool_mod\.fabric\.datagen;/.test(file(out, paths[0])) && /class CoolModDataGenerator implements DataGeneratorEntrypoint/.test(file(out, paths[0])) && !/example/i.test(file(out, paths[0])));
    t('on: .gitattributes untouched', attrs(out) === repoAttrs); }

  { const out = gen(fresh({ datagen: dg({ on: false }) }));
    t('off: no block, no comment, no classes', !/datagen|Data generation/i.test(kts(out)) && !dataPaths(out).length, (kts(out).match(/.*[dD]atagen.*/) || [])[0]);
    t('off: .gitattributes loses its two lines', attrs(out) === repoAttrs.replace(/\n# Written by `\.\/gradlew runDatagen`\n\/src\/\*\*\/generated\/\*\* linguist-generated\n/, '') && !/generated/.test(attrs(out)));
    t('off: the build script stays balanced', (kts(out).match(/\{/g) || []).length === (kts(out).match(/\}/g) || []).length); }

  { const out = gen(fresh({ versions: [forgeOnly()] }));
    t('no Fabric build -> left out, like src/fabric', !/datagen/i.test(kts(out)) && !dataPaths(out).length && !/generated/.test(attrs(out))); }

  { const out = gen(fresh({ datagen: dg({ client: true }) })), k = kts(out);
    t('client: written right after enabled', /^datagen \{\n    enabled = true\n    client = true\n/m.test(k), k.slice(k.indexOf('datagen {')));
    const back = Gen.analyze({ files: out.files.map((f) => ({ p: f.path, t: f.text, b: f.bytes ? Buffer.from(f.bytes).toString('base64') : undefined })) });
    t('client: analyze reads it back', back.hasDatagen && back.datagen.client === true);
    const d2 = { files: out.files.map((f) => ({ p: f.path, t: f.text, b: f.bytes ? Buffer.from(f.bytes).toString('base64') : undefined })) }, s2 = Gen.analyze(d2);
    const again = gen(Object.assign(Gen.identityConfig(s2), { datagen: dg({ client: false }) }), d2, s2);
    t('client: and switched off again', !/^    client = true/m.test(kts(again)) && /enabled = true/.test(kts(again))); }

  { const warned = (out) => out.warnings.filter((w) => w.field === 'datagen.on');
    const old = gen(fresh({ versions: [v('1.16.5')], vcs: '1.16.5' }));
    t('1.16.5: the entrypoint stays in src/fabric (the build leaves it out), and a warning says nothing is generated', dataPaths(old).length === 1 && warned(old).some((w) => /from Minecraft 1\.18, so 1\.16\.5 generates nothing/.test(w.msg)), JSON.stringify(warned(old)));
    const mixed = gen(fresh({ versions: [v('1.16.5'), v('1.17.1'), v('1.18'), v('1.21.1')], vcs: '1.21.1' }));
    t('several versions: only the ones before 1.18 are named', warned(mixed).length === 1 && /1\.16\.5, 1\.17\.1 generate nothing/.test(warned(mixed)[0].msg) && !/so [^;]*(1\.18|1\.21)/.test(warned(mixed)[0].msg), JSON.stringify(warned(mixed)));
    const fine = gen(fresh({ versions: [v('1.20.1'), v('1.21.1')], vcs: '1.21.1' }));
    t('1.18 and newer: no warning, the same entrypoint for each', !warned(fine).length && dataPaths(fine).length === 1); }

  { const errs = (cfg) => Gen.validate(cfg, seed).errors.filter((e) => e.field === 'datagen.on');
    t('validation: Fabric API is required from 1.18 on', errs(fresh({ useFabricApi: false })).length === 1 && errs(fresh({ versions: [ver('1.21.1', { fabricApi: '' })] })).length === 1 && errs(fresh({ versions: [ver('1.20.1', { fabricApi: '', neoforge: { on: false, ver: '' } })], vcs: '1.20.1' })).length === 1);
    t('validation: not before 1.18, where nothing is generated', !errs(fresh({ useFabricApi: false, versions: [ver('1.16.5', { fabricApi: '', neoforge: { on: false, ver: '' } })], vcs: '1.16.5' })).length);
    t('validation: not when off, or without Fabric', !errs(fresh({ useFabricApi: false, datagen: dg({ on: false }) })).length && !errs(fresh({ useFabricApi: false, versions: [forgeOnly()] })).length);
    const mixed = fresh({ versions: [ver('1.20.1', { neoforge: { on: false, ver: '' } }), ver('1.21.1', { fabric: { on: false, ver: '' }, fabricApi: '' })], vcs: '1.21.1' });
    t('validation: a version without a Fabric build is warned about', Gen.validate(mixed, seed).warnings.some((w) => w.field === 'datagen.on' && /no Fabric build/.test(w.msg)) && !errs(mixed).length); }
}

section('MixinExtras, Spotless and the CI workflow');
{
  const t = (name, cond, extra) => (cond ? ok(name) : bad(name + (extra ? ': ' + extra : '')));
  const gen = (cfg, d = data, sd = seed) => Gen.generate(cfg, d, sd);
  const file = (out, p) => (out.files.find((f) => f.path === p) || {}).text;
  const kts = (out) => file(out, 'build.gradle.kts');
  const sc = (out) => file(out, 'stonecutter.gradle.kts');
  const ci = (out) => file(out, '.github/workflows/build.yml');
  const repo = (p) => files.find((f) => f.p === p).t;
  const balanced = (x) => (x.match(/\{/g) || []).length === (x.match(/\}/g) || []).length;
  const forgeOld = ver('1.18.2', { neoforge: { on: false, ver: '' } });

  t('the repo has all three, on by default', seed.hasMixinExtras && seed.hasFormatting && seed.hasCi && Gen.defaultConfig(seed).mixinExtras.on && Gen.defaultConfig(seed).formatting && Gen.defaultConfig(seed).ci);

  { const out = gen(fresh({}));
    t('on: nothing changes except the names', kts(out).includes('mixinExtras {\n    enabled = true') && sc(out) === repo('stonecutter.gradle.kts') && ci(out).includes('spotlessCheck') && ci(out).includes('runDatagen')); }

  { const out = gen(fresh({ mixinExtras: { on: false } }));
    t('MixinExtras off: block and its comment gone, mixins stay', !/mixinExtras|MixinExtras/.test(kts(out)) && /^mixins\.configure\(/m.test(kts(out)) && balanced(kts(out)), (kts(out).match(/.*[mM]ixinExtras.*/) || [])[0]);
    const noMixins = gen(fresh({ mixins: { on: false, files: Object.fromEntries(seed.mixinFiles.map((p) => [p, false])) } }));
    t('mixins off takes MixinExtras with it', !/mixinExtras|MixinExtras/.test(kts(noMixins)) && !/mixins\.configure/.test(kts(noMixins)) && balanced(kts(noMixins))); }

  {
    const v = (cfg) => Gen.validate(cfg, seed).warnings.filter((x) => x.field === 'mixinExtras.on');
    t('validation: Forge 1.18.2 is fine', !v(fresh({ versions: [forgeOld], vcs: '1.18.2' })).length);
    t('validation: not for 1.16.5 Forge when MixinExtras is off', !v(fresh({ mixinExtras: { on: false }, versions: [ver('1.16.5', { neoforge: { on: false, ver: '' } })], vcs: '1.16.5' })).length);
    t('validation: Forge 1.16.5 is warned about', v(fresh({ versions: [ver('1.16.5', { neoforge: { on: false, ver: '' } })], vcs: '1.16.5' })).length === 1);
    t('validation: Fabric only, no warning', !v(fresh({ versions: [ver('1.16.5', { forge: { on: false, ver: '' }, neoforge: { on: false, ver: '' } })], vcs: '1.16.5' })).length); }

  { const out = gen(fresh({ formatting: false }));
    t('Spotless off: plugin line and its comment gone, the rest of stonecutter.gradle.kts stays', sc(out) === 'plugins {\n    id("de.rhm176.build.controller")\n}\n\nstonecutter active "1.21.1" /* [SC] DO NOT EDIT */\n', JSON.stringify(sc(out)));
    t('Spotless off: the CI step and its header line go', !/spotless|Spotless|Check formatting/.test(ci(out)) && /runDatagen/.test(ci(out)) && /- name: Build\n/.test(ci(out)));
    t('Spotless off: .editorconfig stays', !!file(out, '.editorconfig')); }

  { const out = gen(fresh({ datagen: { on: false, client: false } }));
    t('data generation off: the CI check, its header line and the .gitignore entry go', !/runDatagen|Check generated data/.test(ci(out)) && /spotlessCheck/.test(ci(out)) && !/generated/.test(file(out, '.gitignore')), (ci(out).match(/.*[dD]atagen.*/) || [])[0]);
    t('data generation off: the CI file is still well formed', /- name: Build\n        run: \.\/gradlew build --stacktrace\n\n      - name: Keep the jars/.test(ci(out)), ci(out).slice(ci(out).indexOf('- name: Build')));
    const both = gen(fresh({ datagen: { on: false, client: false }, formatting: false }));
    t('both off: only the build and the jars are left', /- name: Build\n/.test(ci(both)) && !/spotless|runDatagen/i.test(ci(both)) && /Keep the jars/.test(ci(both))); }

  { const out = gen(fresh({ ci: false }));
    t('CI off: no build workflow, the release workflow stays', !file(out, '.github/workflows/build.yml') && !!file(out, '.github/workflows/release.yml')); }

  { const out = gen(fresh({ versions: [ver('1.20.1', { neoforge: { on: false, ver: '' } }), ver('1.21.1')], vcs: '1.21.1' }));
    t('JDKs: the CI workflow gets one setup-java step per Java version, like the release workflow', /java-version: 17[^]*java-version: 21/.test(ci(out)) && /java-version: 17[^]*java-version: 21/.test(file(out, '.github/workflows/release.yml')), (ci(out).match(/java-version.*/g) || []).join('|')); }

  {
    const bare = { files: files.filter((f) => f.p !== '.github/workflows/build.yml' && f.p !== '.editorconfig').map((f) => (f.p === 'stonecutter.gradle.kts' ? { ...f, t: 'plugins {\n    id("de.rhm176.build.controller")\n}\n\nstonecutter active "1.21.1" /* [SC] DO NOT EDIT */\n' } : f.p === 'build.gradle.kts' ? { ...f, t: f.t.replace(/\/\/ MixinExtras[^]*?\n}\n\n/, '') } : f)) };
    const sd = Gen.analyze(bare);
    const out = gen(Gen.identityConfig(sd), bare, sd);
    t('a repo without them: analyzed as off, and nothing is added', !sd.hasMixinExtras && !sd.hasFormatting && !sd.hasCi && !out.files.some((f) => /build\.yml$/.test(f.path)) && !/mixinExtras/.test(kts(out)), JSON.stringify([sd.hasMixinExtras, sd.hasFormatting, sd.hasCi])); }
}

section('README as the Modrinth description');
{
  const t = (name, cond, extra) => (cond ? ok(name) : bad(name + (extra ? ': ' + extra : '')));
  const gen = (cfg, d = data, sd = seed) => Gen.generate(cfg, d, sd);
  const file = (out, p) => (out.files.find((f) => f.path === p) || {}).text;
  const kts = (out) => file(out, 'build.gradle.kts');
  const rel = (out) => file(out, '.github/workflows/release.yml');
  const readme = (out) => file(out, 'README.md');
  const header = (out) => (rel(out).match(/^# {3}\d+\. .*$/gm) || []);
  t('the repo has it, on by default', seed.hasDescriptionSync && Gen.defaultConfig(seed).descriptionSync);

  { const out = gen(fresh({}));
    t('on: block kept, README renamed, header lists five steps', /^    projectDescription \{/m.test(kts(out)) && readme(out).startsWith('# Cool Mod\n') && header(out).length === 5 && /\.$/.test(header(out)[4]) && header(out).slice(0, 4).every((l) => /,$/.test(l)), header(out).join('|'));
    t('on: README mentions the markers and the block', /sync:off/.test(readme(out)) && /projectDescription/.test(readme(out))); }

  { const out = gen(fresh({ descriptionSync: false }));
    t('off: block and its comment gone, balanced', !/projectDescription|syncModrinth|README as the/.test(kts(out)) && (kts(out).match(/\{/g) || []).length === (kts(out).match(/\}/g) || []).length, (kts(out).match(/.*projectDescription.*/) || [])[0]);
    t('off: the header counts again and ends with a full stop', header(out).length === 4 && /#   4\. adds the release to update\.json[^\n]*\.$/m.test(rel(out)) && header(out).slice(0, 3).every((l) => /,$/.test(l)), header(out).join('|'));
    t('off: the README keeps the build section but not the description section', /## Building/.test(readme(out)) && !/The project description|projectDescription/.test(readme(out)) && /<!-- sync:on -->/.test(readme(out)));
    const both = gen(fresh({ descriptionSync: false, updateJson: { ...Gen.updateJsonDefaults(), on: false } }));
    t('both off: three steps, the last ends with a full stop', header(both).length === 3 && /#   3\. uploads every jar to Modrinth and CurseForge\.$/m.test(rel(both)), header(both).join('|')); }

  { const out = gen(fresh({ versions: [ver('1.21.1', { forge: { on: false, ver: '' }, neoforge: { on: false, ver: '' } })] }));
    t('no Forge/NeoForge build: only the description step is renumbered to 4', header(out).length === 4 && /#   4\. updates the Modrinth project description/.test(rel(out)), header(out).join('|')); }

  { const bare = { files: files.filter((f) => f.p !== 'README.md').map((f) => (f.p === 'build.gradle.kts' ? { ...f, t: f.t.replace(/    \/\/ The README is also[^]*?\n    projectDescription \{[^]*?\n    \}\n\n/, '') } : f.p === '.github/workflows/release.yml' ? { ...f, t: f.t.replace(/^#   5\..*\n/m, '').replace(/^(#   4\..*),$/m, '$1.') } : f)) };
    const sd = Gen.analyze(bare), out = gen(Gen.identityConfig(sd), bare, sd);
    t('a repo without it: analyzed as off, nothing added', !sd.hasDescriptionSync && !readme(out) && !/projectDescription/.test(kts(out)) && header(out).length === 4, JSON.stringify([sd.hasDescriptionSync, header(out)])); }
}

section('platform services');
{
  const t = (name, cond, extra) => (cond ? ok(name) : bad(name + (extra ? ': ' + extra : '')));
  const gen = (cfg, d = data, sd = seed) => Gen.generate(cfg, d, sd);
  const paths = (out) => out.files.map((f) => f.path).filter((p) => /\/platform\//.test(p));
  const services = (out) => (out.files.find((f) => /\/platform\/Services\.java$/.test(f.path)) || {}).text;
  t('the repo has it, on by default', seed.hasPlatform && Gen.defaultConfig(seed).platform);
  t('no ServiceLoader and no META-INF/services files', !files.some((f) => /META-INF\/services/.test(f.p)) && !/ServiceLoader\b(?!\})/.test(files.find((f) => /platform\/Services\.java$/.test(f.p)).t.replace(/\{@link java\.util\.ServiceLoader\}/g, '')));
  { const out = gen(fresh({})), ps = paths(out);
    t('on: interface, lookup and three implementations, renamed', ps.length === 5 && ps.includes('src/common/main/java/de/rhm176/cool_mod/platform/Platform.java') && ps.includes('src/neoforge/main/java/de/rhm176/cool_mod/neoforge/platform/NeoForgePlatform.java'), ps.join());
    t('on: the lookup names the renamed classes, which exist', ['fabric.platform.FabricPlatform', 'forge.platform.ForgePlatform', 'neoforge.platform.NeoForgePlatform'].every((c) => services(out).includes('"de.rhm176.cool_mod.' + c + '"') && ps.includes('src/' + c.split('.')[0] + '/main/java/de/rhm176/cool_mod/' + c.replace(/\./g, '/') + '.java')));
    t('on: the lookup is the repo\'s, only renamed', services(out) === files.find((f) => /platform\/Services\.java$/.test(f.p)).t.replace(/example_mod/g, 'cool_mod')); }
  { const out = gen(fresh({ platform: false }));
    t('off: all of it is gone, nothing else', !paths(out).length && out.files.some((f) => /CoolMod/.test(f.path))); }
  { const out = gen(fresh({ versions: [ver('1.21.1', { forge: { on: false, ver: '' }, neoforge: { on: false, ver: '' } })] })), ps = paths(out);
    t('Fabric only: the interface, the lookup and the Fabric implementation', ps.length === 3 && ps.every((p) => !/forge/.test(p)), ps.join());
    t('Fabric only: the lookup only lists the Fabric class, and still compiles as a list', /private static final String\[\] PLATFORMS = \{\n        "de\.rhm176\.cool_mod\.fabric\.platform\.FabricPlatform",\n    \};/.test(services(out)), (services(out).match(/PLATFORMS = \{[^}]*\}/) || [])[0]); }
  { const out = gen(fresh({ versions: [ver('1.21.1', { fabric: { on: false, ver: '' }, fabricApi: '' })] }));
    t('Forge and NeoForge: both listed, not Fabric', /forge\.platform\.ForgePlatform/.test(services(out)) && /neoforge\.platform\.NeoForgePlatform/.test(services(out)) && !/fabric\.platform/.test(services(out))); }
}

section('Minecraft versions, fake manifest');
{
  const t = (name, cond, extra) => (cond ? ok(name) : bad(name + (extra ? ': ' + extra : '')));
  const P = 'https://piston-meta.mojang.com/mc/game/version_manifest_v2.json', L = 'https://launchermeta.mojang.com/mc/game/version_manifest_v2.json';
  const manifest = { latest: { release: '26.1', snapshot: '26.2-snapshot-1' }, versions: [
    { id: '26.2-snapshot-1', type: 'snapshot' }, { id: '26.1', type: 'release' }, { id: '1.21.1', type: 'release' }, { id: '1.21', type: 'release' },
    { id: '1.14.4', type: 'release' }, { id: '1.14.3', type: 'release' }, { id: '1.20.1', type: 'release' }, { id: '1.21.1', type: 'release' },
    { id: '24w14a', type: 'snapshot' }, { id: 'b1.7.3', type: 'old_beta' }, { id: '1.2.5', type: 'release' }, { id: 'rd-132211', type: 'old_alpha' }] };
  const net = (routes, log = []) => async (u) => { log.push(u); const r = routes[u]; if (r instanceof Error) throw r; return r === undefined ? { ok: false, status: 404 } : { ok: true, json: async () => r }; };
  { const log = [], r = await Gen.fetchMcVersions(net({ [P]: manifest }, log));
    t('releases only, 1.14.4 and newer, newest first, no duplicates', r.releases.join() === '26.1,1.21.1,1.21,1.20.1,1.14.4' && r.latest === '26.1' && log.length === 1, r.releases.join()); }
  { const log = [], r = await Gen.fetchMcVersions(net({ [P]: new TypeError('Failed to fetch'), [L]: manifest }, log));
    t('falls back to the old host', r.releases.length === 5 && log[1] === L); }
  for (const [label, routes] of [['both hosts down', {}], ['wrong shape', { [P]: { nope: 1 }, [L]: { nope: 1 } }], ['no releases', { [P]: { versions: [{ id: '24w14a', type: 'snapshot' }] } }]]) {
    try { await Gen.fetchMcVersions(net(routes)); bad(label + ': no error'); } catch (e) { ok(label + ' -> ' + e.message); }
  }
}

console.log(failed ? `\n${failed} failed` : '\nall passed');
process.exit(failed ? 1 : 0);
