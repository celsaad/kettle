const { getDefaultConfig } = require('expo/metro-config');

const config = getDefaultConfig(__dirname);

// `.yaml` moves from asset to source, so metro.transformer.js can inline them as text.
config.transformer.babelTransformerPath = require.resolve('./metro.transformer.js');
const textExts = ['yaml', 'yml'];
config.resolver.assetExts = config.resolver.assetExts.filter((ext) => !textExts.includes(ext));
config.resolver.sourceExts = [...config.resolver.sourceExts, ...textExts];

module.exports = config;
