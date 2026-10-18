import { readFileSync } from 'node:fs';
import { createRequire } from 'node:module';
import ts from 'typescript';

/* Load the real frontend modules in Node while substituting browser-only adapters. */
export function loadFrontendModule(name, overrides = {}) {
  const cache = new Map();
  function load(url) {
    if (cache.has(url.href)) return cache.get(url.href);
    const module = { exports: {} };
    cache.set(url.href, module.exports);
    const require = createRequire(import.meta.url);
    const source = readFileSync(url, 'utf8');
    const { outputText } = ts.transpileModule(source, {
      compilerOptions: {
        module: ts.ModuleKind.CommonJS,
        target: ts.ScriptTarget.ES2023,
        jsx: ts.JsxEmit.React,
        esModuleInterop: true,
      },
    });
    const frontendRequire = (specifier) => {
      if (Object.hasOwn(overrides, specifier)) return overrides[specifier];
      if (specifier.endsWith('.css')) return {};
      if (specifier.startsWith('.')) {
        const dependency = new URL(specifier, url);
        for (const suffix of ['.ts', '.tsx']) {
          try { return load(new URL(dependency.href + suffix)); }
          catch (error) { if (error.code !== 'ENOENT') throw error; }
        }
      }
      return require(specifier);
    };
    new Function('require', 'module', 'exports', outputText)(frontendRequire, module, module.exports);
    return module.exports;
  }
  return load(name instanceof URL ? name : new URL(`../../main/frontend/${name}`, import.meta.url));
}
