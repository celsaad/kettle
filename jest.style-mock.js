// `constants/theme.ts` imports `global.css` for the web build's body styling, so anything that reads
// a theme token pulls CSS into the module graph. Jest can't parse CSS, and the failure surfaces as a
// bare "Unexpected token ':'" pointing at `:root`, nowhere near the test that triggered it.
module.exports = {};
