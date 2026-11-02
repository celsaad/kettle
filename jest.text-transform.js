// Jest's half of metro.transformer.js: a `.yaml` or `.svg` import is the file's text. jest-expo maps
// both to its asset stub (`module.exports = 1`), and a project `transform` key is matched before the
// preset's.
module.exports = {
  process: (src) => ({ code: `module.exports = ${JSON.stringify(src)};` }),
};
