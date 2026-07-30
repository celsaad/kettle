# Fonts

The site self-hosts its typefaces so it makes **no third-party requests** — a privacy-first app
shouldn't pull fonts from the Google Fonts CDN, and `privacy.html`'s claim that this site loads no
third-party fonts has to stay true.

The six `.woff2` files here are converted from the **exact TTFs the app itself ships**, in
`node_modules/@expo-google-fonts/{space-grotesk,hanken-grotesk}`. Converting from the app's own
copies rather than re-downloading from Google keeps the site's type identical to the app's and pins
both to the same version.

    space-grotesk-500.woff2      Space Grotesk 500  (display / headings)
    space-grotesk-600.woff2      Space Grotesk 600  (display / headings)
    space-grotesk-700.woff2      Space Grotesk 700  (wordmark / hero)
    hanken-grotesk-400.woff2     Hanken Grotesk 400 (body)
    hanken-grotesk-500.woff2     Hanken Grotesk 500 (body medium)
    hanken-grotesk-600.woff2     Hanken Grotesk 600 (body semibold)

The `@font-face` rules in `../styles.css` reference them by these exact names, over a
`ui-sans-serif` system fallback stack — so a missing file degrades to system type rather than
breaking the page.

## Regenerating

Only needed if the app's font packages are upgraded. `wawoff2` is a WASM port of Google's own woff2
encoder; install it **outside the repo** — same rule as Playwright, it isn't a project dependency
and shouldn't become one.

```js
const wawoff = require('wawoff2');
fs.writeFileSync(dest, Buffer.from(await wawoff.compress(fs.readFileSync(ttf))));
```

Both families are SIL Open Font License; `LICENSE_FONT` in each package is the copy that governs
redistributing them here.

Deliberately **not** subsetted. The full latin sets are 24–31 KB each, the pages are text-light, and
a subset would need regenerating every time copy changes — including the Portuguese pages whenever
those land. Revisit only if the font payload starts mattering.
