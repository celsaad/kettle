// Inlines `.yaml` files as their text, so the shipped content in `src/content/` reaches the bundle as a
// string: `parseLibraryYaml` reads the seed like any user's file. Metro's default treats it as an asset
// (a numeric id on native), which would make seeding an async read of a file baked into the app. The
// `.svg` half went with RN's exercise art in #308; native draws the art from `:art`. The wrapper is the
// pattern Expo documents for SDK 57; jest.text-transform.js is its twin.
const upstream = require('@expo/metro-config/babel-transformer');

module.exports.transform = ({ src, filename, options }) =>
  upstream.transform({
    src: /\.ya?ml$/.test(filename) ? `module.exports = ${JSON.stringify(src)};` : src,
    filename,
    options,
  });

// Metro already hashes this file into its cache key, so editing it invalidates the cache.
module.exports.getCacheKey = upstream.getCacheKey;
