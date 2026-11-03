import fs from 'node:fs';
import path from 'node:path';
import { execFileSync } from 'node:child_process';
import { fileURLToPath, pathToFileURL } from 'node:url';

const here = path.dirname(fileURLToPath(import.meta.url));
const BINARY = new Set(['.jar', '.png', '.jpg', '.jpeg', '.gif', '.ico', '.zip', '.ogg', '.wav']);

const SKIP_DIRS = [/^\.git$/, /^generator$/, /^\.gradle$/, /^\.kotlin$/, /^\.idea$/, /^\.vscode$/, /^out$/, /^versions$/,
  /^build$/, /^buildSrc\/build-logic\/\.gradle$/, /^buildSrc\/build-logic\/\.kotlin$/, /^buildSrc\/build-logic\/[^/]+\/build$/, /(^|\/)run$/];
const SKIP_FILES = [/\.iml$/, /(^|\/)\.DS_Store$/, /^local\.properties$/];

function listFiles(root) {
  try {
    const out = execFileSync('git', ['-C', root, 'ls-files', '-z', '--cached', '--others', '--exclude-standard'], { stdio: ['ignore', 'pipe', 'ignore'], maxBuffer: 1 << 28 });
    const top = execFileSync('git', ['-C', root, 'rev-parse', '--show-toplevel'], { stdio: ['ignore', 'pipe', 'ignore'] }).toString().trim();
    if (path.resolve(top) === path.resolve(root)) return { mode: 'git', files: out.toString('utf8').split('\0').filter(Boolean) };
  } catch (e) {}
  const files = [];
  (function walk(dir, rel) {
    for (const e of fs.readdirSync(dir, { withFileTypes: true })) {
      const r = rel ? rel + '/' + e.name : e.name;
      if (e.isDirectory()) { if (!SKIP_DIRS.some((re) => re.test(r))) walk(path.join(dir, e.name), r); }
      else if (e.isFile() && !SKIP_FILES.some((re) => re.test(r))) files.push(r);
    }
  })(root, '');
  return { mode: 'walk', files };
}

export function collect(root) {
  const { mode, files } = listFiles(root);
  const out = [];
  for (const rel of files.sort()) {
    if (rel.startsWith('generator/') || rel.startsWith('.git/')) continue;
    const abs = path.join(root, rel);
    let st;
    try { st = fs.lstatSync(abs); } catch (e) { continue; }
    if (!st.isFile()) continue;
    const buf = fs.readFileSync(abs);
    const entry = { p: rel };
    if (BINARY.has(path.extname(rel).toLowerCase()) || buf.includes(0)) entry.b = buf.toString('base64');
    else entry.t = buf.toString('utf8');
    if (st.mode & 0o111) entry.x = true;
    out.push(entry);
  }
  return { mode, files: out };
}

if (process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href) {
  const root = path.resolve(here, '..');
  const { mode, files } = collect(root);
  if (!files.some((f) => f.p === 'build.gradle.kts')) { console.error('no build.gradle.kts in ' + root + '; is generator/ inside the repo root?'); process.exit(1); }
  const json = JSON.stringify({ files }).replace(/<\/(script)/gi, '<\\/$1');
  fs.mkdirSync(path.join(here, 'dist'), { recursive: true });
  fs.writeFileSync(path.join(here, 'dist', 'data.js'), 'window.REPO_DATA = ' + json + ';\n');
  console.log(`dist/data.js: ${files.length} files from ${mode === 'git' ? 'git' : 'the folder (no git, built-in ignore list)'}, ${(json.length / 1024).toFixed(0)} KB`);
}
