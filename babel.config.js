// jest's TypeScript transform, through the babel-jest it bundles: strip the types, and compile only
// what the running Node lacks. Type checking is `pnpm run typecheck`'s, not this. Babel rather than
// ts-jest or @swc/jest because jest itself only stays until the TS domain retires (#353).
module.exports = {
  presets: [['@babel/preset-env', { targets: { node: 'current' } }], '@babel/preset-typescript'],
};
