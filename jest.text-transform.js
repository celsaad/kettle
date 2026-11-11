// A `.yaml` import is the file's text: the seed and the content packs import their YAML that way
// (`text-imports.d.ts` types it).
module.exports = {
  process: (src) => ({ code: `module.exports = ${JSON.stringify(src)};` }),
};
