// `react-native-worklets` resolves to `.native.ts` entry points that reach for a JSI binding at
// *import* time, so anything pulling in `react-native-reanimated` — which for us means any screen
// containing a ReorderableList — dies before a test body runs, with a
// "Cannot read properties of undefined (reading 'loadUnpackers')" that names none of that.
//
// The library ships its own jest resolver for exactly this: it drops `.native` from the extension
// list so the plain-JS build wins. But jest allows only one `resolver`, and jest-expo already
// installs React Native's (which strips `exports` from the react-native package so subpaths stay
// mockable). Setting one would silently discard the other, so this composes them instead of picking.
const reactNativeResolver = require('@react-native/jest-preset/jest/resolver');

module.exports = (request, options) => {
  const isWorklets = options.basedir.includes('react-native-worklets') || request.includes('react-native-worklets');
  const resolveOptions = isWorklets
    ? { ...options, extensions: options.extensions?.filter((extension) => !extension.includes('native')) }
    : options;
  return reactNativeResolver(request, resolveOptions);
};
