const { getDefaultConfig } = require('expo/metro-config');

const config = getDefaultConfig(__dirname);

// `.yaml` and `.svg` move from asset to source, so metro.transformer.js can inline them as text.
config.transformer.babelTransformerPath = require.resolve('./metro.transformer.js');
const textExts = ['yaml', 'yml', 'svg'];
config.resolver.assetExts = config.resolver.assetExts.filter((ext) => !textExts.includes(ext));
config.resolver.sourceExts = [...config.resolver.sourceExts, ...textExts];

module.exports = config;
