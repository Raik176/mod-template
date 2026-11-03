(function (root, factory) {
  if (typeof module === 'object' && module.exports) module.exports = factory();
  else root.Gen = factory();
})(typeof self !== 'undefined' ? self : this, function () {
  'use strict';

  const LOADERS = [
    { id: 'fabric', name: 'Fabric', minMc: '1.14.4' },
    { id: 'forge', name: 'Forge', minMc: '1.14.4' },
    { id: 'neoforge', name: 'NeoForge', minMc: '1.20.2' },
  ];
  const LICENSES = ['MIT', 'Apache-2.0', 'LGPL-3.0-only', 'GPL-3.0-only', 'MPL-2.0', 'CC0-1.0', 'All Rights Reserved'];
  const DEP_TYPES = ['requires', 'recommends', 'suggest', 'markConflict', 'markIncompatible'];
  const LOGGER_FROM = '1.18.2';
  const ALL_RIGHTS_RESERVED = 'All Rights Reserved';
  const SPDX_SOURCES = [
    { list: 'https://spdx.org/licenses/licenses.json', detail: (id) => 'https://spdx.org/licenses/' + spdxPath(id) + '.json' },
    { list: 'https://cdn.jsdelivr.net/gh/spdx/license-list-data/json/licenses.json', detail: (id) => 'https://cdn.jsdelivr.net/gh/spdx/license-list-data/json/details/' + spdxPath(id) + '.json' },
  ];
  function spdxPath(id) { return encodeURIComponent(id).replace(/%2B/g, '+'); }
  const JAVA_KEYWORDS = new Set(('abstract assert boolean break byte case catch char class const continue default do double else ' +
    'enum extends final finally float for goto if implements import instanceof int interface long native new package private ' +
    'protected public return short static strictfp super switch synchronized this throw throws transient try void volatile ' +
    'while true false null var record yield sealed permits').split(' '));
  const MODMENU_LINKS = ['buymeacoffee', 'coindrop', 'crowdin', 'curseforge', 'discord', 'donate', 'flattr', 'github_releases', 'github_sponsors', 'kofi',
    'liberapay', 'mastodon', 'modrinth', 'opencollective', 'patreon', 'paypal', 'reddit', 'twitch', 'twitter', 'wiki', 'youtube'];
  const MODMENU_SHORTCUTS = { discord: 'discord', donate: 'donate', wiki: 'wiki', youtube: 'youtube', twitter: 'twitter', mastodon: 'mastodon', reddit: 'reddit',
    twitch: 'twitch', patreon: 'patreon', kofi: 'kofi', paypal: 'paypal', github_sponsors: 'githubSponsors' };
  const MODMENU_UPDATE = { auto: '', on: 'updateChecker = true', off: 'updateChecker = false' };
  const RESERVED_IDS = new Set(['minecraft', 'forge', 'neoforge', 'fabric', 'fabricloader', 'java', 'mixin', 'mods']);

  const DATAGEN_FROM = '1.18';
  const isMc = (s) => typeof s === 'string' && /^\d+(\.\d+)+$/.test(s);
  function cmpNums(a, b) {
    const pa = String(a).split(/[.\-+]/).map((x) => (/^\d+$/.test(x) ? +x : -1));
    const pb = String(b).split(/[.\-+]/).map((x) => (/^\d+$/.test(x) ? +x : -1));
    for (let i = 0; i < Math.max(pa.length, pb.length); i++) {
      const x = pa[i] === undefined ? 0 : pa[i], y = pb[i] === undefined ? 0 : pb[i];
      if (x !== y) return x < y ? -1 : 1;
    }
    return 0;
  }
  function javaForMc(mc) {
    if (!isMc(mc)) return 21;
    if (cmpNums(mc, '1.17') < 0) return 8;
    if (cmpNums(mc, '1.18') < 0) return 16;
    if (cmpNums(mc, '1.20.5') < 0) return 17;
    if (cmpNums(mc, '26.1') < 0) return 21;
    return 25;
  }
  const loggerKind = (mc) => (isMc(mc) && cmpNums(mc, LOGGER_FROM) < 0 ? 'log4j' : 'slf4j');
  const mixinCompat = (javas) => 'JAVA_' + Math.min(Math.max(Math.min.apply(null, javas), 8), 21);
  const kt = (s) => String(s).replace(/\\/g, '\\\\').replace(/"/g, '\\"').replace(/\$/g, '\\$').replace(/\r/g, '\\r').replace(/\n/g, '\\n').replace(/\t/g, '\\t');
  const unkt = (s) => String(s).replace(/\\(["\\$]|n|r|t)/g, (m, c) => ({ n: '\n', r: '\r', t: '\t' }[c] || c));
  const reEsc = (s) => s.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
  const clone = (o) => JSON.parse(JSON.stringify(o));
  const words = (s) => String(s).normalize('NFKD').replace(/[\u0300-\u036f]/g, '').replace(/['\u2019]/g, '').split(/[^A-Za-z0-9]+/).filter(Boolean);
  function deriveModId(name) {
    let id = words(name).map((w) => w.toLowerCase()).join('_');
    if (id && !/^[a-z]/.test(id)) id = 'mod_' + id;
    return id.slice(0, 64);
  }
  function deriveClass(name) {
    let c = words(name).map((w) => w[0].toUpperCase() + w.slice(1)).join('');
    if (c && !/^[A-Za-z]/.test(c)) c = 'Mod' + c;
    return c;
  }
  const sortedBy = (arr) => arr.slice().sort((a, b) => (a < b ? -1 : a > b ? 1 : 0));

  function parseToml(text) {
    const out = { mc: '', bound: '', java: '', fabric: { on: false, ver: '' }, fabricApi: '', modMenu: '', forge: { on: false, ver: '' }, neoforge: { on: false, ver: '' } };
    for (const raw of text.split('\n')) {
      const line = raw.replace(/#.*$/, '').trim();
      let m = /^minecraft\s*=\s*\[\s*"([^"]+)"\s*,\s*"([^"]+)"\s*\]$/.exec(line);
      if (m) { out.mc = m[1]; out.bound = m[2]; continue; }
      m = /^([A-Za-z0-9_-]+)\s*=\s*"([^"]*)"$/.exec(line);
      if (!m) continue;
      const [, k, v] = m;
      if (k === 'minecraft') out.mc = v;
      else if (k === 'java') out.java = v;
      else if (k === 'fabric-loader') out.fabric = { on: true, ver: v };
      else if (k === 'fabric-api') out.fabricApi = v;
      else if (k === 'modmenu') out.modMenu = v;
      else if (k === 'forge-loader') out.forge = { on: true, ver: v };
      else if (k === 'neoforge-loader') out.neoforge = { on: true, ver: v };
    }
    return out;
  }

  const versionKey = (v) => JSON.stringify([v.mc, v.bound || '', +v.java, v.fabric.on && v.fabric.ver, v.fabricApi || '', v.modMenu || '', v.forge.on && v.forge.ver, v.neoforge.on && v.neoforge.ver]);

  function findBlock(src, headRe) {
    const m = headRe.exec(src);
    if (!m) return null;
    const open = src.indexOf('{', m.index + m[0].length - 1);
    if (open < 0 || src.slice(m.index, open).includes('\n')) return null;
    let depth = 0, i = open;
    for (; i < src.length; i++) {
      const c = src[i];
      if (c === '"') { i++; while (i < src.length && src[i] !== '"') { if (src[i] === '\\') i++; i++; } continue; }
      if (c === '/' && src[i + 1] === '/') { while (i < src.length && src[i] !== '\n') i++; continue; }
      if (c === '{') depth++;
      else if (c === '}' && --depth === 0) break;
    }
    if (depth !== 0) return null;
    const lineStart = src.lastIndexOf('\n', m.index) + 1;
    return { start: lineStart, bodyStart: open + 1, bodyEnd: i, end: i + 1 };
  }

  const DEP_RE = new RegExp('^[ \\t]+(' + DEP_TYPES.join('|') + ')\\("((?:\\\\.|[^"\\\\])*)",\\s*"((?:\\\\.|[^"\\\\])*)"\\)[ \\t]*$', 'gm');

  function analyze(data) {
    const files = new Map(data.files.map((f) => [f.p, f]));
    const text = (p) => (files.get(p) && files.get(p).t !== undefined ? files.get(p).t : null);
    const need = (p) => { const t = text(p); if (t === null) throw new Error('The repo has no ' + p + ' (or it is not a text file).'); return t; };
    const kts = need('build.gradle.kts');
    const one = (re, what) => { const m = re.exec(kts); if (!m) throw new Error('build.gradle.kts: could not find ' + what); return unkt(m[1]); };
    const opt = (re) => { const m = re.exec(kts); return m ? unkt(m[1]) : ''; };
    const meta = findBlock(kts, /^modMetadata\s*\{/m);
    if (!meta) throw new Error('build.gradle.kts: no modMetadata { } block');
    const mb = kts.slice(meta.bodyStart, meta.bodyEnd);
    const pick = (re) => { const m = re.exec(mb); return m ? unkt(m[1]) : ''; };
    const str = (k) => pick(new RegExp('^[ \\t]*' + k + '\\s*=\\s*"((?:\\\\.|[^"\\\\])*)"', 'm'));

    const modId = str('modId');
    if (!modId) throw new Error('build.gradle.kts: modMetadata has no modId');
    const props = need('gradle.properties');
    const mainFile = data.files.find((f) => /^src\/common\/main\/java\/.+\.java$/.test(f.p) && f.t && /String\s+MOD_ID\b/.test(f.t));
    if (!mainFile) throw new Error('No class under src/common/main/java declares MOD_ID; cannot tell the main class.');
    const dir = mainFile.p.replace(/^src\/common\/main\/java\//, '').replace(/\/[^/]+$/, '');
    const cls = mainFile.p.replace(/^.*\//, '').replace(/\.java$/, '');
    const pkg = dir.replace(/\//g, '.');
    const groupM = /^group=(.*)$/m.exec(props);
    const settings = need('settings.gradle.kts');
    const projM = /rootProject\.name\s*=\s*"([^"]+)"/.exec(settings);

    const deps = [];
    let dm; DEP_RE.lastIndex = 0;
    while ((dm = DEP_RE.exec(mb))) deps.push({ type: dm[1], modId: unkt(dm[2]), range: unkt(dm[3]) });

    const loaders = LOADERS.map((l) => l.id).filter((id) => data.files.some((f) => f.p.startsWith('src/' + id + '/')));
    const mixinPrefix = 'src/common/main/java/' + dir + '/mixin/';
    const tomls = data.files.filter((f) => /^gradle\/minecraft\/[^/]+\.versions\.toml$/.test(f.p));
    const versions = tomls.map((f) => { const v = parseToml(f.t); v.orig = f.t; v.key = versionKey(v); return v; })
      .filter((v) => v.mc).sort((a, b) => cmpNums(a.mc, b.mc));
    const vcsText = text('gradle/minecraft/vcs.txt');
    const env = /^[ \t]*environment\s*=\s*ModMetadata\.Environment\.(\w+)/m.exec(mb);
    const pubBlock = findBlock(kts, /^modPublishing\s*\{/m);
    const pb = pubBlock ? kts.slice(pubBlock.bodyStart, pubBlock.bodyEnd) : '';
    const pubVal = (fn) => { const m = new RegExp('^[ \\t]*' + fn + '\\("([^"]*)"\\)', 'm').exec(pb); return m ? m[1] : ''; };

    const mm = findBlock(mb, /^[ \t]*modMenu\s*\{/m);
    const modMenu = mm ? parseModMenu(mb.slice(mm.bodyStart, mm.bodyEnd)) : null;
    const uj = pubBlock ? findBlock(pb, /^[ \t]*updateJson\s*\{/m) : null;
    const updateJson = uj ? parseUpdateJson(pb.slice(uj.bodyStart, uj.bodyEnd)) : null;
    const dg = findBlock(kts, /^datagen\s*\{/m);
    const datagen = dg && /^[ \t]*enabled\s*=\s*true\b/m.test(kts.slice(dg.bodyStart, dg.bodyEnd)) ? parseDatagen(kts.slice(dg.bodyStart, dg.bodyEnd)) : null;
    const me = findBlock(kts, /^mixinExtras\s*\{/m);
    const hasMixinExtras = !!me && /^[ \t]*enabled\s*=\s*true\b/m.test(kts.slice(me.bodyStart, me.bodyEnd));
    const hasFormatting = /^[ \t]*id\("de\.rhm176\.build\.formatting"\)/m.test(text('stonecutter.gradle.kts') || '');
    const hasCi = files.has('.github/workflows/build.yml');
    const hasDescriptionSync = !!pubBlock && !!findBlock(pb, /^[ \t]*projectDescription\s*\{/m);
    const hasPlatform = files.has('src/common/main/java/' + dir + '/platform/Platform.java');

    return {
      hasModMenu: !!mm, modMenu, hasUpdateJson: !!uj, updateJson, hasDatagen: !!datagen, datagen, hasMixinExtras, hasFormatting, hasCi, hasPlatform, hasDescriptionSync,
      modId, name: str('name'), description: str('description'), license: str('license'),
      homepage: str('url'), sources: str('sources'),
      authors: [...mb.matchAll(/^[ \t]*author\("((?:\\.|[^"\\])*)"\)/gm)].map((m) => unkt(m[1])),
      environment: env ? env[1] : 'BOTH', quilt: /^[ \t]*quilt\s*=\s*true/m.test(mb),
      dependencies: deps,
      publishing: { modrinth: pubVal('modrinth'), curseforge: pubVal('curseforge'), github: /^[ \t]*github\(\)/m.test(pb) },
      hasMixins: /^mixins\.configure\(/m.test(kts), hasClassTweaker: /^classTweakers\.configure\(/m.test(kts),
      hasVanilla: /^[ \t]*vanilla\(\)/m.test(kts),
      mixinFiles: sortedBy(data.files.filter((f) => f.p.startsWith(mixinPrefix) && f.p.endsWith('.java')).map((f) => f.p)),
      mainPath: mainFile.p, licenseFile: (data.files.find((f) => /^(LICEN[CS]E|COPYING)(\.(md|txt))?$/i.test(f.p)) || {}).p || '',
      cls, pkg, pkgPath: dir, group: groupM ? groupM[1].trim() : pkg.replace(new RegExp('\\.' + reEsc(modId) + '$'), ''),
      version: ((/^version=(.*)$/m.exec(props) || [])[1] || '1.0.0').trim(),
      project: projM ? projM[1] : cls, loaders, versions, vcs: vcsText ? vcsText.trim() : (versions[0] ? versions[0].mc : ''),
      useFabricApi: versions.some((v) => v.fabricApi),
    };
  }

  const SHORT_FN = Object.fromEntries(Object.entries(MODMENU_SHORTCUTS).map(([t, f]) => [f, t]));
  const MM_STR = '"((?:\\\\.|[^"\\\\])*)"';
  function modMenuDefaults() {
    return { on: true, links: [], library: false, deprecated: false, parent: '', updateChecker: 'auto', autoLinks: true, optionalDependency: true, runInDevelopment: true };
  }
  function parseModMenu(body) {
    const c = modMenuDefaults();
    for (const raw of body.split('\n')) {
      const line = raw.trim(); let m;
      if ((m = new RegExp('^link\\(' + MM_STR + ',\\s*' + MM_STR + '\\)').exec(line))) c.links.push({ type: unkt(m[1]).replace(/^modmenu\./, ''), url: unkt(m[2]) });
      else if ((m = new RegExp('^(' + Object.keys(SHORT_FN).join('|') + ')\\(' + MM_STR + '\\)').exec(line))) c.links.push({ type: SHORT_FN[m[1]], url: unkt(m[2]) });
      else if (/^library\(\)/.test(line)) c.library = true;
      else if (/^deprecated\(\)/.test(line)) c.deprecated = true;
      else if ((m = new RegExp('^parent\\(' + MM_STR + '\\)').exec(line))) c.parent = unkt(m[1]);
      else if ((m = /^updateChecker\s*=\s*(true|false)\b/.exec(line))) c.updateChecker = m[1] === 'true' ? 'on' : 'off';
      else if ((m = /^(autoLinks|optionalDependency|runInDevelopment)\s*=\s*(true|false)\b/.exec(line))) c[m[1]] = m[2] === 'true';
    }
    return c;
  }
  function modMenuLines(c, indent) {
    const out = [];
    c.links.filter((l) => l.url.trim()).forEach((l) => {
      const t = l.type.trim().replace(/^modmenu\./, '');
      out.push(indent + (MODMENU_SHORTCUTS[t] ? MODMENU_SHORTCUTS[t] + '("' + kt(l.url.trim()) + '")' : 'link("' + kt(t) + '", "' + kt(l.url.trim()) + '")'));
    });
    if (c.library) out.push(indent + 'library()');
    if (c.deprecated) out.push(indent + 'deprecated()');
    if (c.parent.trim()) out.push(indent + 'parent("' + kt(c.parent.trim()) + '")');
    if (MODMENU_UPDATE[c.updateChecker]) out.push(indent + MODMENU_UPDATE[c.updateChecker]);
    if (!c.autoLinks) out.push(indent + 'autoLinks = false');
    if (!c.optionalDependency) out.push(indent + 'optionalDependency = false');
    if (!c.runInDevelopment) out.push(indent + 'runInDevelopment = false');
    return out;
  }
  const modMenuSig = (c) => JSON.stringify(modMenuLines(c, ''));
  const modMenuActive = (cfg, seed) => !!(seed.hasModMenu && cfg.modMenu && cfg.modMenu.on && cfg.versions.some((v) => enabled(v, seed).some((l) => l.id === 'fabric')));

  function updateJsonDefaults() { return { on: true, branch: 'main', path: 'update.json', url: '' }; }
  function parseUpdateJson(body) {
    const c = updateJsonDefaults();
    for (const raw of body.split('\n')) {
      const line = raw.trim(); let m;
      if ((m = new RegExp('^(branch|path|url)\\s*=\\s*' + MM_STR).exec(line))) c[m[1]] = unkt(m[2]);
      else if (/^enabled\s*=\s*false\b/.test(line)) c.on = false;
    }
    return c;
  }
  function updateJsonLines(c, indent) {
    const out = [];
    if (c.branch.trim() && c.branch.trim() !== 'main') out.push(indent + 'branch = "' + kt(c.branch.trim()) + '"');
    if (c.path.trim() && c.path.trim() !== 'update.json') out.push(indent + 'path = "' + kt(c.path.trim()) + '"');
    if (c.url.trim()) out.push(indent + 'url = "' + kt(c.url.trim()) + '"');
    return out;
  }
  const updateJsonSig = (c) => JSON.stringify(updateJsonLines(c, ''));
  const hasLoader = (cfg, seed, ids) => cfg.versions.some((v) => enabled(v, seed).some((l) => ids.includes(l.id)));
  const updateJsonActive = (cfg, seed) => !!(seed.hasUpdateJson && cfg.updateJson && cfg.updateJson.on && hasLoader(cfg, seed, ['forge', 'neoforge']));

  function datagenDefaults() { return { on: true, client: false }; }
  function parseDatagen(body) {
    const c = datagenDefaults();
    if (/^[ \t]*client\s*=\s*true\b/m.test(body)) c.client = true;
    return c;
  }
  const datagenActive = (cfg, seed) => !!(seed.hasDatagen && cfg.datagen && cfg.datagen.on && hasLoader(cfg, seed, ['fabric']));

  const mixinExtrasActive = (cfg, seed) => !!(seed.hasMixinExtras && cfg.mixinExtras && cfg.mixinExtras.on && cfg.mixins.on);

  function defaultConfig(seed) {
    const owner = (/github\.com\/([^/]+)\//.exec(seed.sources) || [])[1] || '';
    return {
      name: '', modId: '', group: seed.group, cls: '', project: '', version: '1.0.0', description: '',
      authors: seed.authors.slice(), license: seed.license, homepage: seed.homepage, github: '', githubOwner: owner,
      environment: seed.environment, quilt: seed.quilt,
      versions: clone(seed.versions).map((v) => ({ ...v, java: +v.java })),
      vcs: seed.vcs, useFabricApi: seed.useFabricApi,
      mixins: { on: seed.hasMixins, files: Object.fromEntries(seed.mixinFiles.map((p) => [p, true])) },
      classTweaker: seed.hasClassTweaker, vanillaRelocations: seed.hasVanilla,
      dependencies: clone(seed.dependencies), publishing: clone(seed.publishing), icon: null,
      logger: true, modMenu: seed.modMenu ? clone(seed.modMenu) : modMenuDefaults(),
      updateJson: seed.updateJson ? clone(seed.updateJson) : updateJsonDefaults(),
      datagen: seed.datagen ? clone(seed.datagen) : datagenDefaults(),
      mixinExtras: { on: seed.hasMixinExtras }, formatting: seed.hasFormatting, ci: seed.hasCi, platform: seed.hasPlatform, descriptionSync: seed.hasDescriptionSync,
      licenseFile: true, licenseText: null, year: String(new Date().getFullYear()), copyright: '',
    };
  }
  function identityConfig(seed) {
    const c = defaultConfig(seed);
    return Object.assign(c, {
      name: seed.name, modId: seed.modId, cls: seed.cls, project: seed.project, version: seed.version, description: seed.description,
      github: (/github\.com\/(.+)$/.exec(seed.sources) || [])[1] || '',
      logger: false, licenseFile: false,
    });
  }

  const enabled = (v, seed) => LOADERS.filter((l) => (!seed || seed.loaders.includes(l.id)) && v[l.id] && v[l.id].on);

  function validate(cfg, seed) {
    const errors = [], warnings = [];
    const err = (field, msg) => errors.push({ field, msg });
    const warn = (field, msg) => warnings.push({ field, msg });
    if (!cfg.name.trim()) err('name', 'Give the mod a name.');
    else if (/["\\$\r\n]/.test(cfg.name)) err('name', 'The name can\'t contain " \\ or $, because it gets pasted straight into Java, Kotlin and JSON.');
    if (!/^[a-z][a-z0-9_]{1,63}$/.test(cfg.modId)) err('modId', 'The mod id needs 2 to 64 characters: lowercase letters, digits and underscores, starting with a letter.');
    else if (JAVA_KEYWORDS.has(cfg.modId)) err('modId', '"' + cfg.modId + '" is a Java keyword, and the id becomes part of a package name. Pick something else.');
    else if (RESERVED_IDS.has(cfg.modId)) err('modId', '"' + cfg.modId + '" is already used by Minecraft or a loader. Pick something else.');
    if (!/^[a-z][a-z0-9_]*(\.[a-z][a-z0-9_]*)*$/.test(cfg.group)) err('group', 'The group should be a lowercase package like com.example: letters, digits and underscores, split by dots.');
    else if (cfg.group.split('.').some((s) => JAVA_KEYWORDS.has(s))) err('group', 'One part of the group is a Java keyword. Rename it.');
    if (!/^[A-Z][A-Za-z0-9]*$/.test(cfg.cls)) err('cls', 'The class prefix has to be a valid Java class name, like MyMod.');
    if (!/^[A-Za-z0-9][A-Za-z0-9._-]*$/.test(cfg.project)) err('project', 'The folder name can only use letters, digits, dots, dashes and underscores.');
    if (!/^\d+\.\d+\.\d+([-+][0-9A-Za-z.-]+)?$/.test(cfg.version)) warn('version', 'That version isn\'t in x.y.z form, which the changelog and publishing tools expect.');
    if (/[\r\n]/.test(cfg.description)) err('description', 'The description has to be a single line.');
    if (cfg.homepage && !/^https?:\/\/\S+$/.test(cfg.homepage)) err('homepage', 'The homepage should start with http:// or https://.');
    if (cfg.github && !/^[A-Za-z0-9_.-]+\/[A-Za-z0-9_.-]+$/.test(cfg.github)) err('github', 'Write the GitHub repo as owner/repo.');
    if (!cfg.license.trim()) err('license', 'Pick a license or type one in.');
    if (cfg.licenseFile) {
      if (!/^\d{4}([-\u2013, ]+\d{4})*$/.test(String(cfg.year || '').trim())) err('year', 'The copyright year should look like 2026 or 2024-2026.');
      if (/[\r\n]/.test(cfg.copyright || '')) err('copyright', 'The copyright holder has to be a single line.');
    }
    const p = cfg.publishing;
    if (p.curseforge && !/^\d+$/.test(p.curseforge)) err('publishing.curseforge', 'The CurseForge project id is a number.');
    if (p.modrinth && !/^[A-Za-z0-9_-]{2,}$/.test(p.modrinth)) err('publishing.modrinth', 'That Modrinth id or slug doesn\'t look right.');
    if (p.github && !cfg.github) err('publishing.github', 'GitHub releases need a GitHub repo. Fill it in above, or untick this.');

    if (!cfg.versions.length) err('versions', 'Add at least one Minecraft version.');
    const seen = new Set();
    cfg.versions.forEach((v, i) => {
      const f = 'versions.' + i;
      if (!isMc(v.mc)) { err(f + '.mc', '"' + v.mc + '" isn\'t a Minecraft version. Something like 1.21.1 works.'); return; }
      if (seen.has(v.mc)) err(f + '.mc', v.mc + ' is in the list twice.');
      seen.add(v.mc);
      if (cmpNums(v.mc, '1.14.4') < 0) err(f + '.mc', 'The build setup only supports 1.14.4 and newer.');
      if (v.bound && !isMc(v.bound)) err(f + '.bound', '"Also works on" has to be a Minecraft version.');
      if (!(parseInt(v.java, 10) >= 8)) err(f + '.java', 'The Java version should be a number.');
      else if (+v.java !== javaForMc(v.mc)) warn(f + '.java', v.mc + ' normally uses Java ' + javaForMc(v.mc) + ', and the build warns when it doesn\'t match.');
      const on = enabled(v, seed);
      if (!on.length) err(f + '.loaders', v.mc + ': turn on at least one loader.');
      on.forEach((l) => {
        const ver = (v[l.id].ver || '').trim();
        if (!ver) err(f + '.' + l.id, v.mc + ': add a ' + l.name + ' version.');
        else if (/[\s"'\\]/.test(ver)) err(f + '.' + l.id, v.mc + ': the ' + l.name + ' version has characters it can\'t have (spaces, quotes or backslashes).');
        if (cmpNums(v.mc, l.minMc) < 0) err(f + '.' + l.id, l.name + ' needs Minecraft ' + l.minMc + ' or newer, and this is ' + v.mc + '.');
      });
      if (cfg.useFabricApi && enabled(v, seed).some((l) => l.id === 'fabric') && !(v.fabricApi || '').trim()) warn(f + '.fabricApi', v.mc + ': no Fabric API version, so the Fabric build won\'t get the API.');
      if (v.neoforge.on && cmpNums(v.mc, '1.20.4') < 0 && cmpNums(v.mc, '1.20.2') >= 0) warn(f + '.neoforge', v.mc + ': NeoForge below 1.20.4 builds with NeoGradle instead.');
    });
    if (cfg.versions.length && !cfg.versions.some((v) => v.mc === cfg.vcs)) err('vcs', 'The active version isn\'t one of the versions in the list.');
    if (cfg.modMenu && cfg.modMenu.on && seed.hasModMenu) {
      const mm = cfg.modMenu, fabric = cfg.versions.filter((v) => enabled(v, seed).some((l) => l.id === 'fabric'));
      if (fabric.length) {
        if (cfg.environment === 'SERVER') warn('modMenu.on', 'Mod Menu only exists on the client, and this mod is server only.');
        fabric.filter((v) => !(v.modMenu || '').trim()).forEach((v) => warn('versions.' + cfg.versions.indexOf(v) + '.modMenu', v.mc + ': no Mod Menu version, so the Fabric build for it is built without Mod Menu.'));
        fabric.filter((v) => /[\s"'\\]/.test(v.modMenu || '')).forEach((v) => err('versions.' + cfg.versions.indexOf(v) + '.modMenu', v.mc + ': the Mod Menu version has characters it can\'t have (spaces, quotes or backslashes).'));
      }
      const seenTypes = new Set();
      mm.links.forEach((l, i) => {
        if (!l.url.trim()) return;
        const t = l.type.trim().replace(/^modmenu\./, '');
        if (!/^[a-z][a-z0-9_]*$/.test(t) && !/^[A-Za-z0-9_-]+(\.[A-Za-z0-9_-]+)+$/.test(l.type.trim())) err('modMenu.links.' + i, '"' + l.type + '" isn\'t a Mod Menu link type (like discord).');
        else if (seenTypes.has(t)) err('modMenu.links.' + i, 'There are two "' + t + '" links.');
        seenTypes.add(t);
        if (!/^https?:\/\/[^\s"]+$/.test(l.url.trim())) err('modMenu.links.' + i, 'The ' + t + ' link should start with http:// or https://.');
      });
      const parent = (mm.parent || '').trim();
      if (parent && !/^[a-z][a-z0-9_-]{1,63}$/.test(parent)) err('modMenu.parent', 'The parent has to be a mod id: lowercase letters, digits, - and _.');
      else if (parent && parent === cfg.modId) err('modMenu.parent', 'A mod can\'t be its own parent.');
    }
    if (updateJsonActive(cfg, seed)) {
      const u = cfg.updateJson, path = (u.path || '').trim(), branch = (u.branch || '').trim(), url = (u.url || '').trim();
      if (!/^[A-Za-z0-9._-]+(\/[A-Za-z0-9._-]+)*$/.test(path) || path.split('/').some((x) => x === '.' || x === '..')) err('updateJson.path', 'The update file needs a relative path like update.json (letters, digits, . _ - and /).');
      if (!/^[A-Za-z0-9._-]+(\/[A-Za-z0-9._-]+)*$/.test(branch)) err('updateJson.branch', 'That doesn\'t look like a branch name.');
      if (url && !/^https?:\/\/[^\s"]+$/.test(url)) err('updateJson.url', 'The update file URL should start with http:// or https://.');
      if (!url && !cfg.github) warn('updateJson.url', 'Without a GitHub repo or a URL the build can\'t tell where update.json is served from, so Forge and NeoForge get no updateJSONURL until you set one.');
    }
    if (seed.hasDatagen && cfg.datagen && cfg.datagen.on && hasLoader(cfg, seed, ['fabric'])) {
      const fabricVersions = cfg.versions.map((v, i) => ({ v, i })).filter(({ v }) => enabled(v, seed).some((l) => l.id === 'fabric'));
      const supported = (v) => isMc(v.mc) && cmpNums(v.mc, DATAGEN_FROM) >= 0;
      fabricVersions.filter(({ v }) => supported(v) && (!cfg.useFabricApi || !(v.fabricApi || '').trim()))
        .forEach(({ v }) => err('datagen.on', 'Data generation needs Fabric API on ' + v.mc + '. Add its version, or untick data generation.'));
      const tooOld = fabricVersions.filter(({ v }) => isMc(v.mc) && !supported(v)).map(({ v }) => v.mc);
      if (tooOld.length) warn('datagen.on', 'Fabric API only has data generation from Minecraft ' + DATAGEN_FROM + ', so ' + tooOld.join(', ') + ' generate' + (tooOld.length === 1 ? 's' : '') + ' nothing. The generator class is left out of ' + (tooOld.length === 1 ? 'that build' : 'those builds') + '.');
      const noFabric = cfg.versions.filter((v) => !enabled(v, seed).some((l) => l.id === 'fabric')).map((v) => v.mc);
      if (noFabric.length) warn('datagen.on', noFabric.join(', ') + ': no Fabric build, so no generated data there. Data generation runs on Fabric.');
    }
    if (mixinExtrasActive(cfg, seed)) {
      const old = cfg.versions.filter((v) => isMc(v.mc) && enabled(v, seed).some((l) => l.id === 'forge') && cmpNums(v.mc, '1.18.2') < 0).map((v) => v.mc);
      if (old.length) warn('mixinExtras.on', 'MixinExtras is not available on Forge before 1.18.2 (' + old.join(', ') + '). The build leaves it out there, so mixins that use its annotations need a Stonecutter condition.');
    }
    cfg.dependencies.forEach((d, i) => {
      if (!/^[a-z][a-z0-9_-]{1,63}$/.test(d.modId)) err('dependencies.' + i, '"' + d.modId + '" doesn\'t look like a mod id.');
    });
    return { errors, warnings };
  }

  function lineRe(key) { return new RegExp('^([ \\t]*' + key + '\\s*=\\s*)"((?:\\\\.|[^"\\\\])*)"([^\\n]*)$', 'm'); }

  function setStr(src, key, value, file) {
    const re = lineRe(key), m = re.exec(src);
    if (!m) throw new Error(file + ': no `' + key + ' = "..."` line to set');
    if (m[2] === kt(value)) return src;
    return src.replace(re, (all, pre, old, tail) => pre + '"' + kt(value) + '"' + tail.replace(/\s*\/\/\s*TODO\b.*$/, ''));
  }
  function dropLine(src, re) {
    const m = re.exec(src);
    if (!m) return src;
    const start = m.index, end = src.indexOf('\n', start);
    return src.slice(0, start) + src.slice(end < 0 ? src.length : end + 1);
  }
  function insertAfter(src, anchors, line, file) {
    for (const re of anchors) {
      const m = re.exec(src);
      if (!m) continue;
      const end = src.indexOf('\n', m.index);
      return src.slice(0, end + 1) + line + '\n' + src.slice(end + 1);
    }
    throw new Error(file + ': nowhere to insert `' + line.trim() + '`');
  }
  function removeBlock(src, headRe, what, file) {
    const b = findBlock(src, headRe);
    if (!b) throw new Error(file + ': could not find the ' + what + ' block to remove');
    let end = b.end; if (src[end] === '\n') end++;
    if (src[end] === '\n') end++;
    return src.slice(0, b.start) + src.slice(end);
  }
  function removeBlockAndComments(src, headRe, what, file) {
    const b = findBlock(src, headRe);
    if (!b) throw new Error(file + ': could not find the ' + what + ' block to remove');
    let start = b.start;
    for (;;) {
      const prev = src.lastIndexOf('\n', start - 2) + 1;
      if (start > 0 && /^[ \t]*\/\//.test(src.slice(prev, start))) start = prev; else break;
    }
    let end = b.end; if (src[end] === '\n') end++;
    if (src[end] === '\n') end++;
    return src.slice(0, start) + src.slice(end);
  }
  function rewriteBlockLines(src, headRe, what, file, knownRe, lines) {
    const b = findBlock(src, headRe);
    if (!b) throw new Error(file + ': could not find the ' + what + ' block to rewrite');
    const old = src.slice(b.bodyStart, b.bodyEnd).split('\n');
    const kept = old.slice(1).filter((l) => !knownRe.test(l));
    return src.slice(0, b.bodyStart) + [old[0]].concat(lines, kept).join('\n') + src.slice(b.bodyEnd);
  }
  const modelImport = 'import de.rhm176.build.model.ModMetadata';

  function patchBuildScript(src, cfg, seed) {
    const F = 'build.gradle.kts';
    const meta = () => findBlock(src, /^modMetadata\s*\{/m);

    src = setStr(src, 'name', cfg.name.trim(), F);
    src = setStr(src, 'description', cfg.description.trim() || cfg.name.trim(), F);
    src = setStr(src, 'license', cfg.license, F);
    const hasLine = (k) => lineRe(k).test(src);
    const after = (k) => new RegExp('^[ \\t]*' + k + '\\s*=', 'm');
    const icon = after('icon'), url = after('url');
    if (cfg.homepage) { if (hasLine('url')) src = setStr(src, 'url', cfg.homepage, F); else src = insertAfter(src, [icon], '    url = "' + kt(cfg.homepage) + '"', F); }
    else src = dropLine(src, after('url'));
    if (cfg.github) {
      const href = 'https://github.com/' + cfg.github;
      if (hasLine('sources')) src = setStr(src, 'sources', href, F); else src = insertAfter(src, [url, icon], '    sources = "' + kt(href) + '"', F);
      if (href !== seed.sources) src = src.replace(/^([ \t]*sources\s*=\s*"(?:\\.|[^"\\])*")[ \t]*\/\/\s*TODO\b[^\n]*$/m, (m, keep) => keep);
      if (!hasLine('issues')) src = insertAfter(src, [after('sources')], '    issues = "$sources/issues"', F);
    } else { src = dropLine(src, after('sources')); src = dropLine(src, after('issues')); }

    const env = /^[ \t]*environment\s*=\s*ModMetadata\.Environment\.(\w+)[^\n]*$/m;
    const curEnv = env.exec(src) ? env.exec(src)[1] : 'BOTH';
    if (cfg.environment !== curEnv) {
      src = dropLine(src, env);
      if (cfg.environment !== 'BOTH') src = insertAfter(src, [icon], '    environment = ModMetadata.Environment.' + cfg.environment, F);
    }
    const quiltRe = /^[ \t]*quilt\s*=\s*true[^\n]*$/m;
    if (!!cfg.quilt !== quiltRe.test(src)) {
      if (cfg.quilt) src = insertAfter(src, [/^[ \t]*environment\s*=/m, icon], '    quilt = true // also ships quilt.mod.json inside the Fabric jar', F);
      else src = dropLine(src, quiltRe);
    }
    const usesModel = /ModMetadata\./.test(src.replace(/^import .*$/gm, ''));
    if (usesModel && !src.includes(modelImport)) src = modelImport + '\n' + src.replace(/^(?=plugins\s*\{)/m, '\n');
    if (!usesModel && src.includes(modelImport)) src = src.replace(modelImport + '\n', '').replace(/^\n+/, '');

    const authorRe = /^[ \t]*author\("(?:\\.|[^"\\])*"\)[^\n]*$/gm;
    const oldAuthors = [...src.matchAll(/^[ \t]*author\("((?:\\.|[^"\\])*)"\)/gm)].map((m) => unkt(m[1]));
    if (JSON.stringify(oldAuthors) !== JSON.stringify(cfg.authors)) {
      const first = /^[ \t]*author\(/m.exec(src);
      const lines = cfg.authors.map((a) => '    author("' + kt(a) + '")').join('\n');
      if (first) {
        src = src.replace(authorRe, () => '\u0000AUTHOR\u0000');
        src = src.replace(/\u0000AUTHOR\u0000\n?/, () => (lines ? lines + '\n' : '')).replace(/\u0000AUTHOR\u0000\n?/g, '');
      } else if (lines) src = insertAfter(src, [after('issues'), after('sources'), url, icon], '\n' + lines, F);
    }

    const b = meta();
    const body = src.slice(b.bodyStart, b.bodyEnd);
    const cur = [...body.matchAll(DEP_RE)].map((m) => ({ type: m[1], modId: unkt(m[2]), range: unkt(m[3]) }));
    const want = cfg.dependencies.map((d) => ({ type: d.type, modId: d.modId.trim(), range: (d.range || '*').trim() || '*' }));
    if (JSON.stringify(cur) !== JSON.stringify(want)) {
      let nb = body.replace(DEP_RE, '\u0000DEP\u0000').replace(/\u0000DEP\u0000\n?/g, '');
      const lines = want.map((d) => '    ' + d.type + '("' + kt(d.modId) + '", "' + kt(d.range) + '")').join('\n');
      if (lines) {
        const a = /^[ \t]*author\([^\n]*\n/m.exec(nb);
        const lastAuthor = [...nb.matchAll(/^[ \t]*author\([^\n]*\n/gm)].pop();
        const at = lastAuthor ? lastAuthor.index + lastAuthor[0].length : nb.indexOf('\n') + 1;
        nb = nb.slice(0, at) + '\n' + lines + '\n' + nb.slice(at);
      }
      src = src.slice(0, b.bodyStart) + nb + src.slice(b.bodyEnd);
    }

    const pubSet = (fn, value, comment) => {
      const active = new RegExp('^[ \\t]*' + fn + '\\("[^"]*"\\)[^\\n]*$', 'm'), commented = new RegExp('^[ \\t]*//\\s*' + fn + '\\([^\\n]*$', 'm');
      const has = active.exec(src);
      if (value) {
        const line = '    ' + fn + '("' + kt(value) + '")';
        if (has) { if (!new RegExp('"' + reEsc(kt(value)) + '"').test(has[0])) src = src.replace(active, () => line); }
        else if (commented.test(src)) src = src.replace(commented, () => line);
        else src = insertAfter(src, [/^modPublishing\s*\{/m], line, F);
      } else if (has) src = src.replace(active, () => comment);
    };
    const pubFlag = (on) => {
      const active = /^[ \t]*github\(\)[^\n]*$/m, commented = /^[ \t]*\/\/\s*github\(\)[^\n]*$/m;
      if (on && !active.test(src)) { if (commented.test(src)) src = src.replace(commented, () => '    github()'); else src = insertAfter(src, [/^modPublishing\s*\{/m], '    github()', F); }
      if (!on && active.test(src)) src = src.replace(active, () => '    // github()');
    };
    pubSet('modrinth', cfg.publishing.modrinth, '    // modrinth("AANobbMI")        // project id or slug');
    pubSet('curseforge', cfg.publishing.curseforge, '    // curseforge("123456")        // numeric project id');
    pubFlag(cfg.publishing.github);

    if (seed.hasModMenu) {
      const head = /^[ \t]*modMenu\s*\{/m;
      if (!modMenuActive(cfg, seed)) {
        src = removeBlockAndComments(src, head, 'modMenu', F);
      } else if (modMenuSig(cfg.modMenu) !== modMenuSig(seed.modMenu)) {
        const b = findBlock(src, head);
        if (!b) throw new Error(F + ': could not find the modMenu block to rewrite');
        const indent = /^[ \t]*/.exec(src.slice(b.start))[0] + '    ';
        const known = new RegExp('^[ \\t]*(?:link\\(|(?:' + Object.values(MODMENU_SHORTCUTS).join('|') + ')\\(|library\\(\\)|deprecated\\(\\)|parent\\(|updateChecker\\s*=|autoLinks\\s*=|optionalDependency\\s*=|runInDevelopment\\s*=)');
        const old = src.slice(b.bodyStart, b.bodyEnd).split('\n');
        const kept = old.slice(1).filter((l) => !known.test(l));
        const body = [old[0]].concat(modMenuLines(cfg.modMenu, indent), kept).join('\n');
        src = src.slice(0, b.bodyStart) + body + src.slice(b.bodyEnd);
      }
    }

    if (seed.hasUpdateJson) {
      const head = /^[ \t]*updateJson\s*\{/m;
      if (!updateJsonActive(cfg, seed)) src = removeBlockAndComments(src, head, 'updateJson', F);
      else if (updateJsonSig(cfg.updateJson) !== updateJsonSig(seed.updateJson) || seed.updateJson.on === false) {
        const b = findBlock(src, head), indent = (b ? /^[ \t]*/.exec(src.slice(b.start))[0] : '') + '    ';
        src = rewriteBlockLines(src, head, 'updateJson', F, /^[ \t]*(?:branch|path|url|enabled)\s*=/, updateJsonLines(cfg.updateJson, indent));
      }
    }
    if (seed.hasDescriptionSync && !cfg.descriptionSync) src = removeBlockAndComments(src, /^[ \t]*projectDescription\s*\{/m, 'projectDescription', F);
    if (seed.hasDatagen) {
      const head = /^datagen\s*\{/m;
      if (!datagenActive(cfg, seed)) src = removeBlockAndComments(src, head, 'datagen', F);
      else if (!!cfg.datagen.client !== !!seed.datagen.client) {
        const b = findBlock(src, head), indent = (b ? /^[ \t]*/.exec(src.slice(b.start))[0] : '') + '    ';
        const old = src.slice(b.bodyStart, b.bodyEnd).split('\n');
        const body = [old[0]].concat(old.slice(1).filter((l) => !/^[ \t]*client\s*=\s*(true|false)\b/.test(l)));
        if (cfg.datagen.client) {
          const at = body.findIndex((l, i) => i > 0 && /^[ \t]*enabled\s*=/.test(l));
          body.splice(at < 0 ? 1 : at + 1, 0, indent + 'client = true');
        }
        src = src.slice(0, b.bodyStart) + body.join('\n') + src.slice(b.bodyEnd);
      }
    }

    if (seed.hasMixinExtras && !mixinExtrasActive(cfg, seed)) src = removeBlockAndComments(src, /^mixinExtras\s*\{/m, 'mixinExtras', F);

    if (!cfg.mixins.on && seed.hasMixins) {
      src = removeBlock(src, /^mixins\.configure\(/m, 'mixins.configure', F);
      src = src.replace(/^import de\.rhm176\.build\.mixin\.MixinEnvironment\.Type\.\w+\n/gm, '').replace(/^\n+/, '');
    }
    if (!cfg.classTweaker && seed.hasClassTweaker) src = removeBlock(src, /^classTweakers\.configure\(/m, 'classTweakers.configure', F);
    if (!cfg.vanillaRelocations && seed.hasVanilla) {
      const re = /^[ \t]*vanilla\(\)[^\n]*\n(\n)?/m;
      if (!re.test(src)) throw new Error(F + ': no vanilla() line to remove');
      src = src.replace(re, '');
    }
    return src.replace(/\n{3,}/g, '\n\n');
  }

  function patchMainClass(src, cfg) {
    const F = 'the main class';
    if (/\bLogger\b/.test(src)) return src;
    const kinds = new Set(cfg.versions.map((v) => loggerKind(v.mc)));
    const imports = { slf4j: ['import org.slf4j.Logger;', 'import org.slf4j.LoggerFactory;'], log4j: ['import org.apache.logging.log4j.LogManager;', 'import org.apache.logging.log4j.Logger;'] };
    const fields = { slf4j: 'public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);', log4j: 'public static final Logger LOGGER = LogManager.getLogger(MOD_ID);' };
    const both = (indent, pick) => {
      const live = loggerKind(cfg.vcs), cond = indent + '//? if >=' + LOGGER_FROM + ' {';
      const body = (kind, off) => {
        const lines = pick(kind).map((l) => indent + l);
        if (!off) return lines;
        lines[0] = indent + '/*' + lines[0].slice(indent.length);
        return lines;
      };
      return [cond, ...body('slf4j', live !== 'slf4j'), indent + (live !== 'slf4j' ? '*///?} else {' : '//?} else {'),
        ...body('log4j', live !== 'log4j'), indent + (live !== 'log4j' ? '*///?}' : '//?}')];
    };
    let importLines, fieldLines;
    if (kinds.size === 1) {
      const k = [...kinds][0];
      importLines = imports[k]; fieldLines = [fields[k]];
    } else {
      importLines = both('', (k) => imports[k]);
      fieldLines = null;
    }
    const idLine = /^([ \t]*)public static final String MOD_ID\s*=.*;[ \t]*\n/m.exec(src);
    if (!idLine) throw new Error(F + ': no `public static final String MOD_ID = ...;` line to put the logger after');
    if (!/^package [^;]+;/m.test(src)) throw new Error(F + ': no package line');
    const indent = idLine[1];
    const fieldBlock = (fieldLines || both(indent, (k) => [fields[k]])).map((l) => (fieldLines ? indent + l : l)).join('\n') + '\n';
    src = src.slice(0, idLine.index + idLine[0].length) + fieldBlock + src.slice(idLine.index + idLine[0].length);
    const firstImport = /^import /m.exec(src);
    if (firstImport) return src.slice(0, firstImport.index) + importLines.join('\n') + '\n' + src.slice(firstImport.index);
    return src.replace(/^(package [^;]+;\n)\n*/m, (m, pkg) => pkg + '\n' + importLines.join('\n') + '\n\n');
  }

  function patchIssueTemplate(src, cfg, usedLoaders, seed) {
    LOADERS.forEach((l) => {
      if (seed.loaders.includes(l.id) && !usedLoaders.has(l.id)) src = src.replace(new RegExp('^[ \\t]+- ' + l.name + '[ \\t]*\\n', 'gm'), '');
    });
    const run = /^([ \t]+)- \d+(?:\.\d+)+[ \t]*\n(?:\1- \d+(?:\.\d+)+[ \t]*\n)*/m.exec(src);
    if (run) {
      const mcs = cfg.versions.map((v) => v.mc).sort((a, b) => cmpNums(b, a));
      src = src.slice(0, run.index) + mcs.map((m) => run[1] + '- ' + m + '\n').join('') + src.slice(run.index + run[0].length);
    }
    return src;
  }

  function jdkList(javas, base) {
    return [...new Set(javas.map(Number))].filter((j) => j !== base).sort((a, b) => a - b).concat(base);
  }

  function patchWorkflow(src, file, javas) {
    const lines = src.split('\n'), steps = [];
    for (let i = 0; i < lines.length; i++) {
      const m = /^(\s*)(- )?uses:\s*actions\/setup-java@\S+/.exec(lines[i]);
      if (!m) continue;
      let start = i, indent = m[1].length;
      if (!m[2]) {
        const prev = /^(\s*)- (?!uses:)/.exec(lines[i - 1] || '');
        if (!prev || prev[1].length !== indent - 2) throw new Error(file + ': could not tell where the actions/setup-java step starts');
        start = i - 1; indent -= 2;
      }
      let end = i + 1;
      while (end < lines.length && (lines[end].trim() === '' ? false : lines[end].length - lines[end].trimStart().length > indent)) end++;
      steps.push({ start, end, indent });
      i = end - 1;
    }
    if (!steps.length) return src;
    const versionRe = /^(\s*java-version:\s*)(['"]?)(\d+)\2[ \t]*(#.*)?$/;
    const had = steps.map((st) => { const l = lines.slice(st.start, st.end).map((x) => versionRe.exec(x)).find(Boolean); return l ? +l[3] : NaN; });
    if (had.some((n) => !n)) throw new Error(file + ': the actions/setup-java step has no plain `java-version: <number>` line');
    const base = had[had.length - 1], want = jdkList(javas, base);
    if (JSON.stringify(had) === JSON.stringify(want)) return src;
    if (steps.length !== 1) throw new Error(file + ': has ' + steps.length + ' actions/setup-java steps; the generator can only rewrite a single one');
    const st = steps[0], pad = ' '.repeat(st.indent), block = lines.slice(st.start, st.end);
    const make = (n) => {
      const out = block.map((l) => { const m = versionRe.exec(l); return m ? m[1] + n : l; });
      if (want.length > 1) {
        if (/^\s*- uses:/.test(out[0])) out.splice(0, 1, pad + '- name: Set up JDK ' + n, pad + '  ' + out[0].trim().replace(/^- /, ''));
        else out[0] = out[0].replace(/(- name:).*/, '$1 Set up JDK ' + n);
      }
      return out;
    };
    const rewritten = [].concat(...want.map(make));
    return lines.slice(0, st.start).concat(rewritten, lines.slice(st.end)).join('\n');
  }

  function removeWorkflowStep(src, name, commentRe, file) {
    const lines = src.split('\n');
    const i = lines.findIndex((l) => l.trim() === '- name: ' + name);
    if (i < 0) return src;
    const indent = lines[i].length - lines[i].trimStart().length;
    let end = i + 1;
    while (end < lines.length && (lines[end].trim() === '' || lines[end].length - lines[end].trimStart().length > indent)) end++;
    if (end === lines.length && lines[end - 1] === '') end--;
    lines.splice(i, end - i);
    return lines.filter((l) => !(commentRe && commentRe.test(l))).join('\n');
  }
  function patchServices(src, usedLoaders, file) {
    for (const id of ['fabric', 'forge', 'neoforge']) {
      if (usedLoaders.has(id)) continue;
      const re = new RegExp('^[ \\t]*"[\\w.]+\\.' + id + '\\.platform\\.\\w+",\\n', 'm');
      if (!re.test(src)) throw new Error(file + ': no "...' + id + '.platform...." entry to remove');
      src = src.replace(re, '');
    }
    return src;
  }
  function removeFormatting(src, file) {
    const line = /^[ \t]*id\("de\.rhm176\.build\.formatting"\)[ \t]*\n/m;
    if (!line.test(src)) throw new Error(file + ': no id("de.rhm176.build.formatting") line to remove');
    src = src.replace(line, '');
    const note = /^\/\/ Formatting \(Spotless\)[^\n]*\n(?:\/\/[^\n]*\n)*\n?/m;
    if (!note.test(src)) throw new Error(file + ': no "// Formatting (Spotless)" comment to remove');
    return src.replace(note, '');
  }

  function patchUpdateWorkflow(src, active, path) {
    if (!/- name:\s*Commit update\.json\b/.test(src)) return src;
    if (active) {
      if (path === 'update.json') return src;
      return src.replace(/^(\s*UPDATE_JSON:\s*)update\.json\s*$/m, (m, a) => a + path)
        .replace(/^([ \t]+)update\.json[ \t]*$/m, (m, a) => a + path)
        .replace(/(adds the release to )update\.json( \(the update notices)/, (m, a, b) => a + path + b);
    }
    const lines = src.split('\n');
    const i = lines.findIndex((l) => /^\s*- name:\s*Commit update\.json\s*$/.test(l));
    const indent = lines[i].length - lines[i].trimStart().length;
    let end = i + 1;
    while (end < lines.length && (lines[end].trim() === '' || lines[end].length - lines[end].trimStart().length > indent)) end++;
    if (end === lines.length && lines[end - 1] === '') end--;
    lines.splice(i, end - i);
    return lines.join('\n')
      .replace(/^# {3}\d+\. adds the release to update\.json[^\n]*\n/m, '')
      .replace(/^[ \t]+update\.json[ \t]*\n/m, '');
  }
  function fixReleaseHeader(src) {
    const lines = src.split('\n');
    const at = lines.map((l, i) => (/^# {3}\d+\. /.test(l) ? i : -1)).filter((i) => i >= 0);
    at.forEach((i, n) => { lines[i] = lines[i].replace(/^# {3}\d+\. /, '#   ' + (n + 1) + '. ').replace(/[,.]$/, n === at.length - 1 ? '.' : ','); });
    return lines.join('\n');
  }
  function patchDescriptionWorkflow(src, active) {
    return active ? src : src.replace(/^# {3}\d+\. updates the Modrinth project description[^\n]*\n/m, '');
  }

  const textOf = async (get, url) => { const r = await get(url); if (!r.ok) throw new Error('HTTP ' + r.status); return r.json(); };
  async function firstWorking(tries) {
    let last;
    for (const t of tries) { try { return await t(); } catch (e) { last = e; } }
    throw last || new Error('no source');
  }

  async function fetchLicenseList(fetchImpl) {
    const get = fetchImpl || fetch;
    const j = await firstWorking(SPDX_SOURCES.map((s) => () => textOf(get, s.list)));
    if (!j || !Array.isArray(j.licenses)) throw new Error('unexpected list format');
    return { version: j.licenseListVersion || '', licenses: j.licenses.map((l) => ({ id: l.licenseId, name: l.name, osi: !!l.isOsiApproved, deprecated: !!l.isDeprecatedLicenseId })) };
  }

  async function fetchLicense(id, fetchImpl) {
    const get = fetchImpl || fetch;
    const j = await firstWorking(SPDX_SOURCES.map((s) => () => textOf(get, s.detail(id))));
    if (!j || typeof j.licenseText !== 'string' || !j.licenseText.trim()) throw new Error('that license has no text on the SPDX list');
    return { id: j.licenseId || id, name: j.name || id, text: j.licenseText, osi: !!j.isOsiApproved, deprecated: !!j.isDeprecatedLicenseId };
  }

  const isSingleLicense = (s) => /^[A-Za-z0-9][A-Za-z0-9.+-]*$/.test(String(s).trim());
  const resolveLicenseId = (input, licenses) => { const w = String(input).trim().toLowerCase(); const m = licenses.find((l) => l.id.toLowerCase() === w); return m ? m.id : ''; };

  const YEAR_SLOT = /[<\[{]\s*(?:year|yyyy|years)\s*[>\]}]|\b4-digit year\b|(?<=\bCopyright(?:\s+\([cC]\)|\s+\u00a9)?\s+)YEAR\b/g;
  const HOLDER_SLOT = /[<\[{]\s*(?:(?:full )?name of )?(?:the )?(?:copyright |software )?(?:holders?|owners?|authors?|organi[sz]ation|company|fullname|full name)\s*[>\]}]|\bCompany or Person'?s Name\b|\bAUTHOR EMAIL\b/gi;
  function fillLicense(raw, o) {
    let text = String(raw).replace(/\r\n?/g, '\n').replace(/^\s*\n/, '').replace(/\s+$/, '') + '\n';
    const end = /END OF TERMS AND CONDITIONS/i.exec(text), cut = end ? end.index : text.length;
    let head = text.slice(0, cut), filled = { year: false, holder: false };
    const year = String(o.year || '').trim(), holder = String(o.holder || '').trim();
    if (year) head = head.replace(YEAR_SLOT, () => { filled.year = true; return year; });
    if (holder) head = head.replace(HOLDER_SLOT, () => { filled.holder = true; return holder; });
    return { text: head + text.slice(cut), filled };
  }

  function licenseOutput(cfg) {
    if (!cfg.licenseFile) return { file: null };
    const id = cfg.license.trim(), holder = (cfg.copyright || '').trim() || cfg.authors.join(', ');
    if (id.toLowerCase() === ALL_RIGHTS_RESERVED.toLowerCase()) {
      if (!holder) return { file: null, warning: 'No LICENSE file: "All Rights Reserved" needs a copyright holder, and there are no authors.' };
      return { file: 'Copyright (c) ' + cfg.year.trim() + ' ' + holder + '. All rights reserved.\n' };
    }
    if (!isSingleLicense(id)) return { file: null, warning: 'No LICENSE file: "' + id + '" is more than one license, so add the texts yourself (for example LICENSE-MIT and LICENSE-APACHE).' };
    if (!cfg.licenseText) return { file: null, warning: 'No LICENSE file: the text of ' + id + ' couldn\'t be loaded from the SPDX list. You might be offline, or it isn\'t a license id the list knows.' };
    const r = fillLicense(cfg.licenseText, { year: cfg.year, holder });
    const missing = [];
    if (/[<\[{]\s*(?:year|yyyy)\s*[>\]}]/i.test(r.text.slice(0, (/END OF TERMS AND CONDITIONS/i.exec(r.text) || { index: r.text.length }).index))) missing.push('year');
    if (!holder && /[<\[{][^>\]}\n]*(?:holder|owner|author)[^>\]}\n]*[>\]}]/i.test(r.text.slice(0, 600))) missing.push('copyright holder');
    return { file: r.text, warning: missing.length ? 'The LICENSE still has ' + missing.map((m) => 'a ' + m + ' slot').join(' and ') + ' to fill in, since there are no authors.' : '' };
  }

  function b64ToBytes(b64) {
    if (typeof Buffer !== 'undefined') return new Uint8Array(Buffer.from(b64, 'base64'));
    const bin = atob(b64), out = new Uint8Array(bin.length);
    for (let i = 0; i < bin.length; i++) out[i] = bin.charCodeAt(i);
    return out;
  }

  function versionsToml(cfg, v, seed) {
    const known = seed.versions.find((s) => s.mc === v.mc);
    const mmOn = modMenuActive(cfg, seed) && v.fabric.on && !!(v.modMenu || '').trim();
    if (known && known.key === versionKey(v) && (cfg.useFabricApi || !known.fabricApi) && (mmOn || !known.modMenu)) return known.orig;
    const lines = ['[versions]'];
    lines.push('minecraft = ' + (v.bound && v.bound !== v.mc ? '["' + v.mc + '", "' + v.bound + '"]' : '"' + v.mc + '"'));
    lines.push('java = "' + parseInt(v.java, 10) + '"', '');
    const has = (id) => seed.loaders.includes(id) && v[id].on;
    if (has('fabric')) {
      lines.push('fabric-loader = "' + v.fabric.ver.trim() + '"');
      if (cfg.useFabricApi && (v.fabricApi || '').trim()) lines.push('fabric-api = "' + v.fabricApi.trim() + '"');
      if (mmOn) lines.push('modmenu = "' + v.modMenu.trim() + '"');
      lines.push('');
    }
    if (has('forge')) lines.push('forge-loader = "' + v.forge.ver.trim() + '"');
    if (has('neoforge')) lines.push('neoforge-loader = "' + v.neoforge.ver.trim() + '"');
    while (lines[lines.length - 1] === '') lines.pop();
    return lines.join('\n') + '\n';
  }

  const isVerbatim = (p) => p.startsWith('buildSrc/') || p.startsWith('gradle/wrapper/') || p === 'gradlew' || p === 'gradlew.bat' || p === 'gradle/tools.properties';

  function generate(inputCfg, data, seed) {
    const cfg = clone(Object.assign({}, inputCfg, { icon: null }));
    cfg.icon = inputCfg.icon || null;
    seed = seed || analyze(data);
    const check = validate(cfg, seed);
    if (check.errors.length) return { files: [], errors: check.errors, warnings: check.warnings };

    const newPkg = cfg.group + '.' + cfg.modId;
    const javas = [...new Set(cfg.versions.map((v) => +v.java))];
    const usedLoaders = new Set();
    cfg.versions.forEach((v) => enabled(v, seed).forEach((l) => usedLoaders.add(l.id)));
    const mcSet = new Set(cfg.versions.map((v) => v.mc));
    const modMenuOn = modMenuActive(cfg, seed);
    const updateJsonOn = updateJsonActive(cfg, seed), datagenOn = datagenActive(cfg, seed), descriptionOn = !!(seed.hasDescriptionSync && cfg.descriptionSync);
    const oldIconPath = 'src/common/main/resources/assets/' + seed.modId + '/icon.png';

    const drop = (p) => {
      let m;
      if (/^gradle\/minecraft\/[^/]+\.versions\.toml$/.test(p) || p === 'gradle/minecraft/vcs.txt') return true;
      if ((m = /^src\/([^/]+)\//.exec(p)) && seed.loaders.includes(m[1]) && !usedLoaders.has(m[1])) return true;
      if ((m = /^src\/versions\/([^/]+)\/([^/]+)\//.exec(p)) && (!mcSet.has(m[1]) || (seed.loaders.includes(m[2]) && !usedLoaders.has(m[2])))) return true;
      if (!cfg.mixins.on && seed.hasMixins && (p.startsWith('src/common/main/java/' + seed.pkgPath + '/mixin/') || /\.mixins\.json$/.test(p))) return true;
      if (cfg.mixins.on && p in cfg.mixins.files && !cfg.mixins.files[p]) return true;
      if (!cfg.classTweaker && seed.hasClassTweaker && /^src\/[^/]+\/main\/resources\/.+\.ct$/.test(p)) return true;
      if (!cfg.ci && seed.hasCi && p === '.github/workflows/build.yml') return true;
      if (!cfg.platform && seed.hasPlatform && (p.startsWith('src/common/main/java/' + seed.pkgPath + '/platform/')
        || /^src\/(?:versions\/[^/]+\/)?[^/]+\/main\/java\/(?:.+\/)?(?:fabric|forge|neoforge)\/platform\//.test(p)
        )) return true;
      if (!modMenuOn && /^src\/(?:versions\/[^/]+\/)?fabric\/main\/java\/(?:.+\/)?fabric\/modmenu\//.test(p)) return true;
      if (!datagenOn && (/^src\/(?:versions\/[^/]+\/)?fabric\/main\/java\/(?:.+\/)?fabric\/datagen\//.test(p) || /^src\/(?:common|versions\/[^/]+)\/generated\//.test(p))) return true;
      return false;
    };
    let kept = data.files.filter((f) => !drop(f.p));

    const pairs = [[seed.pkgPath, newPkg.replace(/\./g, '/')], [seed.pkg, newPkg], [seed.cls, cfg.cls], [seed.modId, cfg.modId], [seed.name, cfg.name.trim()]]
      .filter(([from, to]) => from && from !== to);
    const rename = pairs.length ? (() => {
      const map = new Map(pairs);
      const re = new RegExp(pairs.map(([f]) => f).sort((a, b) => b.length - a.length).map(reEsc).join('|'), 'g');
      return (s) => s.replace(re, (m) => map.get(m));
    })() : (s) => s;

    const out = [];
    for (const f of kept) {
      const e = { path: isVerbatim(f.p) ? f.p : rename(f.p), exec: !!f.x };
      if (f.b !== undefined) e.bytes = b64ToBytes(f.b);
      else e.text = isVerbatim(f.p) ? f.t : rename(f.t);
      e.orig = f.p;
      out.push(e);
    }

    for (const e of out) {
      if (e.text === undefined) continue;
      if (e.orig === 'build.gradle.kts') e.text = patchBuildScript(e.text, cfg, seed);
      else if (e.orig === 'gradle.properties') {
        e.text = e.text.replace(/^group=.*$/m, () => 'group=' + cfg.group).replace(/^version=.*$/m, () => 'version=' + cfg.version);
      } else if (e.orig === 'settings.gradle.kts') {
        if (!/rootProject\.name\s*=\s*"/.test(e.text)) throw new Error('settings.gradle.kts: no rootProject.name');
        e.text = e.text.replace(/(rootProject\.name\s*=\s*")[^"]*(")/, (m, a, b) => a + cfg.project + b);
      } else if (e.orig === 'stonecutter.gradle.kts') {
        if (!/stonecutter active "/.test(e.text)) throw new Error('stonecutter.gradle.kts: no `stonecutter active "..."` line');
        e.text = e.text.replace(/(stonecutter active ")[^"]*(")/, (m, a, b) => a + cfg.vcs + b);
        if (seed.hasFormatting && !cfg.formatting) e.text = removeFormatting(e.text, e.orig);
      } else if (e.orig === seed.mainPath && cfg.logger) {
        e.text = patchMainClass(e.text, cfg);
      } else if (/^\.github\/ISSUE_TEMPLATE\/[^/]+\.ya?ml$/.test(e.orig)) {
        e.text = patchIssueTemplate(e.text, cfg, usedLoaders, seed);
      } else if (/^\.github\/workflows\/[^/]+\.ya?ml$/.test(e.orig)) {
        if (/actions\/setup-java@/.test(e.text)) e.text = patchWorkflow(e.text, e.orig, javas);
        if (e.orig === '.github/workflows/build.yml') {
          if (!cfg.formatting) e.text = removeWorkflowStep(e.text, 'Check formatting', /^#\s+- \.\/gradlew spotlessCheck\b/, e.orig);
          if (!datagenOn) e.text = removeWorkflowStep(e.text, 'Check generated data', /^#\s+- \.\/gradlew runDatagen\b/, e.orig);
        }
        if (seed.hasUpdateJson) e.text = patchUpdateWorkflow(e.text, updateJsonOn, (cfg.updateJson.path || 'update.json').trim());
        if (seed.hasDescriptionSync) e.text = patchDescriptionWorkflow(e.text, descriptionOn);
        if (e.orig === '.github/workflows/release.yml') e.text = fixReleaseHeader(e.text);
      } else if (cfg.platform && seed.hasPlatform && e.orig === 'src/common/main/java/' + seed.pkgPath + '/platform/Services.java') {
        e.text = patchServices(e.text, usedLoaders, e.orig);
      } else if (e.orig === 'README.md' && seed.hasDescriptionSync && !descriptionOn) {
        e.text = e.text.replace(/^## The project description\n[^]*?(?=^<!-- sync:on -->$)/m, '');
      } else if (e.orig === '.gitignore' && seed.hasDatagen && !datagenOn) {
        e.text = e.text.replace(/# the data generators' own bookkeeping[^\n]*\n\/src\/\*\*\/generated\/\*\*\/\.cache\/\n\n?/, '');
      } else if (e.orig === '.gitattributes' && seed.hasDatagen && !datagenOn) {
        e.text = e.text.replace(/\n# Written by `\.\/gradlew runDatagen`\n\/src\/\*\*\/generated\/\*\* linguist-generated\n?/, '\n').replace(/\n+$/, '\n');
      } else if (/\.mixins\.json$/.test(e.orig)) {
        const wanted = mixinCompat(javas), had = mixinCompat(seed.versions.map((v) => +v.java));
        if (wanted !== had) e.text = e.text.replace(/("compatibilityLevel"\s*:\s*")JAVA_\d+(")/, (m, a, b) => a + wanted + b);
      }
    }

    cfg.versions.forEach((v) => out.push({ path: 'gradle/minecraft/' + v.mc + '.versions.toml', text: versionsToml(cfg, v, seed) }));
    out.push({ path: 'gradle/minecraft/vcs.txt', text: cfg.vcs + '\n' });
    if (cfg.icon && cfg.icon.length) {
      const iconPath = rename(oldIconPath);
      const i = out.findIndex((e) => e.path === iconPath);
      if (i >= 0) out[i] = { path: iconPath, bytes: cfg.icon };
      else out.push({ path: iconPath, bytes: cfg.icon });
    }

    const extraWarnings = [];
    const lic = licenseOutput(cfg);
    if (lic.file) {
      const at = out.findIndex((e) => e.orig === seed.licenseFile && seed.licenseFile);
      if (at >= 0) out[at] = { path: out[at].path, text: lic.file };
      else out.push({ path: 'LICENSE', text: lic.file });
    }
    if (lic.warning) extraWarnings.push({ field: 'license', msg: lic.warning });
    if (!lic.file && seed.licenseFile && cfg.license.trim() !== seed.license) extraWarnings.push({ field: 'license', msg: 'The ' + seed.licenseFile + ' of the template is for ' + seed.license + ', and the mod says ' + cfg.license.trim() + '. Replace that file.' });

    const dupes = out.map((f) => f.path).filter((p, i, a) => a.indexOf(p) !== i);
    if (dupes.length) throw new Error('rename produced duplicate paths: ' + dupes.join(', '));
    out.forEach((e) => { delete e.orig; });
    out.sort((a, b) => (a.path < b.path ? -1 : a.path > b.path ? 1 : 0));
    return { files: out, errors: [], warnings: check.warnings.concat(extraWarnings) };
  }

  const CRC = (() => { const t = new Uint32Array(256); for (let n = 0; n < 256; n++) { let c = n; for (let k = 0; k < 8; k++) c = c & 1 ? 0xedb88320 ^ (c >>> 1) : c >>> 1; t[n] = c >>> 0; } return t; })();
  function crc32(b) { let c = 0xffffffff; for (let i = 0; i < b.length; i++) c = CRC[(c ^ b[i]) & 0xff] ^ (c >>> 8); return (c ^ 0xffffffff) >>> 0; }

  function zip(files, rootDir) {
    const enc = new TextEncoder(), now = new Date();
    const dosTime = (now.getHours() << 11) | (now.getMinutes() << 5) | (now.getSeconds() >> 1);
    const dosDate = ((now.getFullYear() - 1980) << 9) | ((now.getMonth() + 1) << 5) | now.getDate();
    const chunks = [], central = [];
    let offset = 0;
    const push = (u8) => { chunks.push(u8); offset += u8.length; };
    for (const f of files) {
      const name = enc.encode((rootDir ? rootDir + '/' : '') + f.path), data = f.bytes || enc.encode(f.text), crc = crc32(data);
      const l = new DataView(new ArrayBuffer(30));
      l.setUint32(0, 0x04034b50, true); l.setUint16(4, 20, true); l.setUint16(6, 0x0800, true);
      l.setUint16(10, dosTime, true); l.setUint16(12, dosDate, true);
      l.setUint32(14, crc, true); l.setUint32(18, data.length, true); l.setUint32(22, data.length, true); l.setUint16(26, name.length, true);
      const at = offset; push(new Uint8Array(l.buffer)); push(name); push(data);
      const c = new DataView(new ArrayBuffer(46));
      c.setUint32(0, 0x02014b50, true); c.setUint16(4, (3 << 8) | 20, true); c.setUint16(6, 20, true); c.setUint16(8, 0x0800, true);
      c.setUint16(12, dosTime, true); c.setUint16(14, dosDate, true);
      c.setUint32(16, crc, true); c.setUint32(20, data.length, true); c.setUint32(24, data.length, true); c.setUint16(28, name.length, true);
      c.setUint32(38, ((f.exec ? 0o100755 : 0o100644) << 16) >>> 0, true); c.setUint32(42, at, true);
      central.push(new Uint8Array(c.buffer), name);
    }
    const start = offset; central.forEach(push);
    const e = new DataView(new ArrayBuffer(22));
    e.setUint32(0, 0x06054b50, true); e.setUint16(8, files.length, true); e.setUint16(10, files.length, true);
    e.setUint32(12, offset - start, true); e.setUint32(16, start, true);
    push(new Uint8Array(e.buffer));
    const res = new Uint8Array(offset); let p = 0;
    chunks.forEach((c) => { res.set(c, p); p += c.length; });
    return res;
  }

  const MC_MANIFESTS = [
    'https://piston-meta.mojang.com/mc/game/version_manifest_v2.json',
    'https://launchermeta.mojang.com/mc/game/version_manifest_v2.json',
  ];
  const MC_MINIMUM = '1.14.4';

  async function fetchMcVersions(fetchImpl) {
    const get = fetchImpl || fetch;
    const j = await firstWorking(MC_MANIFESTS.map((u) => () => textOf(get, u)));
    if (!j || !Array.isArray(j.versions)) throw new Error('unexpected manifest format');
    const releases = j.versions.filter((v) => v && v.type === 'release' && isMc(v.id) && cmpNums(v.id, MC_MINIMUM) >= 0).map((v) => v.id)
      .filter((id, i, a) => a.indexOf(id) === i).sort((a, b) => cmpNums(b, a));
    if (!releases.length) throw new Error('the manifest lists no release versions');
    return { latest: (j.latest && j.latest.release) || releases[0], releases };
  }

  const xmlVersions = (xml) => [...xml.matchAll(/<version>([^<]+)<\/version>/g)].map((m) => m[1]);
  const maxOf = (arr) => arr.reduce((b, x) => (b === undefined || cmpNums(x, b) > 0 ? x : b), undefined);

  async function fetchLatest(mc, fetchImpl) {
    const get = fetchImpl || fetch, res = { fabricLoader: '', fabricApi: '', modMenu: '', forge: '', neoforge: '', errors: [] };
    const text = async (u) => { const r = await get(u); if (!r.ok) throw new Error('HTTP ' + r.status); return r.text(); };
    const jobs = {
      'fabric loader': async () => { const l = JSON.parse(await text('https://meta.fabricmc.net/v2/versions/loader')); const s = l.find((x) => x.stable) || l[0]; if (s) res.fabricLoader = s.version; },
      'fabric api': async () => { res.fabricApi = maxOf(xmlVersions(await text('https://maven.fabricmc.net/net/fabricmc/fabric-api/fabric-api/maven-metadata.xml')).filter((v) => v.endsWith('+' + mc))) || ''; },
      'mod menu': async () => {
        const q = 'https://api.modrinth.com/v2/project/modmenu/version?loaders=' + encodeURIComponent('["fabric"]') + '&game_versions=' + encodeURIComponent('["' + mc + '"]');
        const l = JSON.parse(await text(q)), r = l.find((x) => x.version_type === 'release') || l[0];
        if (r && r.version_number) res.modMenu = String(r.version_number).replace(/^v(?=\d)/, '');
      },
      'forge': async () => { res.forge = maxOf(xmlVersions(await text('https://maven.minecraftforge.net/net/minecraftforge/forge/maven-metadata.xml')).filter((v) => v.startsWith(mc + '-')).map((v) => v.slice(mc.length + 1))) || ''; },
      'neoforge': async () => {
        const all = xmlVersions(await text('https://maven.neoforged.net/releases/net/neoforged/neoforge/maven-metadata.xml'));
        const parts = mc.split('.'), pre = parts[0] === '1' ? parts.slice(1) : parts; while (pre.length < 2) pre.push('0');
        const prefix = pre.slice(0, 2).join('.') + '.', m = all.filter((v) => v.startsWith(prefix)), stable = m.filter((v) => !/[a-z]/i.test(v));
        res.neoforge = maxOf(stable.length ? stable : m) || '';
      },
    };
    await Promise.all(Object.entries(jobs).map(async ([label, run]) => { try { await run(); } catch (e) { res.errors.push(label + ': ' + (e && e.message ? e.message : e)); } }));
    return res;
  }

  return { LOADERS, LICENSES, MODMENU_LINKS, modMenuDefaults, updateJsonDefaults, datagenDefaults, ALL_RIGHTS_RESERVED, LOGGER_FROM, DEP_TYPES, loggerKind, jdkList, fetchLicenseList, fetchLicense, fetchMcVersions, fillLicense, resolveLicenseId, isSingleLicense, analyze, defaultConfig, identityConfig, validate, generate, zip, fetchLatest, javaForMc, mixinCompat, isMc, cmpNums, deriveModId, deriveClass, kt };
});
