const { getDefaultConfig } = require('expo/metro-config');

const config = getDefaultConfig(__dirname);

// `.yaml` moves from asset to source, so metro.transformer.js can inline it as text.
config.transformer.babelTransformerPath = require.resolve('./metro.transformer.js');
config.resolver.assetExts = config.resolver.assetExts.filter((ext) => ext !== 'yaml' && ext !== 'yml');
config.resolver.sourceExts = [...config.resolver.sourceExts, 'yaml'];

module.exports = config;
