import { build } from 'esbuild';
import { fileURLToPath } from 'node:url';
import { resolve } from 'node:path';

const dir = fileURLToPath(new URL('.', import.meta.url));
const result = await build({
  absWorkingDir: dir, entryPoints: ['entry.cjs'], bundle: true, platform: 'browser',
  target: ['es2017'], minify: true, metafile: true,
  outfile: resolve(dir, '../../app/src/main/assets/forge.min.js'),
  banner: {js: '/* node-forge 1.4.0 AES-only bundle; see forge-LICENSE.txt. Rebuild: scripts/crypto. */'},
});
console.log('Bundled modules:\n' + Object.keys(result.metafile.inputs).join('\n'));
