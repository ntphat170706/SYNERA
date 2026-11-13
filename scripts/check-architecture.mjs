import fs from 'node:fs';
import path from 'node:path';
import ts from 'typescript';
const failures = [];
const required = [
  'ARCHITECTURE.md',
  'AGENTS.md',
  'docs/DESIGN-SYSTEM.md',
  'src/styles/tokens.css',
  'src/styles/admin.css',
  'src/styles/auth.css',
  'src/styles/landing.css',
  'src/app/navigation.ts',
  'src/shared/Brand.tsx',
  'public/assets/brand/synera-logo.png',
];
for (const name of required)
  if (!fs.existsSync(name)) failures.push('Missing canonical file: ' + name);
const files = (directory) =>
  fs
    .readdirSync(directory, { withFileTypes: true })
    .flatMap((entry) =>
      entry.isDirectory() ? files(directory + '/' + entry.name) : [directory + '/' + entry.name],
    );
for (const file of files('src').filter((f) => /\.tsx?$/.test(f) && !f.endsWith('.test.ts'))) {
  const source = fs.readFileSync(file, 'utf8');
  if (/^src\/(features|app)\//.test(file) && /#[0-9a-f]{3,8}\b/i.test(source))
    failures.push(file + ': theme color must come from tokens');
  if (file !== 'src/main.tsx' && /createRoot\s*\(|<HashRouter\b|<BrowserRouter\b/.test(source))
    failures.push(file + ': duplicate application entry/router');
  const ast = ts.createSourceFile(
    file,
    source,
    ts.ScriptTarget.Latest,
    true,
    file.endsWith('.tsx') ? ts.ScriptKind.TSX : ts.ScriptKind.TS,
  );
  for (const node of ast.statements) {
    if (!ts.isImportDeclaration(node) || !ts.isStringLiteral(node.moduleSpecifier)) continue;
    const specifier = node.moduleSpecifier.text;
    if (!specifier.startsWith('.')) continue;
    const target = path.posix.normalize(path.posix.join(path.posix.dirname(file), specifier));
    if (file.startsWith('src/shared/') && /^src\/(features|app)\//.test(target))
      failures.push(file + ': shared cannot depend on ' + target);
    if (file.startsWith('src/features/') && target.startsWith('src/app/'))
      failures.push(file + ': feature cannot depend on the application shell');
    if (specifier.endsWith('.css') && file !== 'src/main.tsx')
      failures.push(file + ': global styles belong in the canonical main.tsx order');
  }
}
const admin = fs.readFileSync('src/styles/admin.css', 'utf8');
// User-approved 2026-11-13: only the NPP navigation gets a token-based blue ramp.
// Tables, forms, other roles and all other gradients/glass remain disallowed.
const flatAdmin = admin.replace(/\.npp-portal \.sidebar\s*\{[^}]*\}/g, (rule) =>
  rule.replace(
    /linear-gradient\(\s*165deg,\s*var\(--nav-gradient-light\) 0%,\s*var\(--nav-gradient-mid\) 42%,\s*var\(--nav-gradient-deep\) 100%\s*\)/g,
    'none',
  ),
);
if (/linear-gradient|radial-gradient|backdrop-filter:\s*blur/i.test(flatAdmin))
  failures.push('Admin must use flat surfaces; gradients/glass belong only to landing/artwork');
for (const name of ['src/styles.css', 'src/readability.css', 'src/styles/admin-components.css'])
  if (fs.existsSync(name)) failures.push('Duplicate legacy theme file: ' + name);
const pkg = JSON.parse(fs.readFileSync('package.json', 'utf8'));
for (const [name, major] of [
  ['react', 18],
  ['vite', 6],
  ['typescript', 5],
]) {
  const version = pkg.dependencies?.[name] || pkg.devDependencies?.[name] || '';
  if (Number(version.match(/\d+/)?.[0]) !== major)
    failures.push(
      'Stack changed: ' + name + '; update the architecture decision and check deliberately',
    );
}
if (failures.length) {
  console.error(failures.join('\n'));
  process.exit(1);
}
console.log(
  'Architecture checks passed: canonical files, stack, import boundaries, single entry, token usage and flat data surfaces with the approved NPP navigation ramp.',
);
