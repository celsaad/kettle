/**
 * Regenerates every Play Console graphic asset into `store/out/`.
 *
 * Nothing here is committed: every output is derived from something already in the repo, so storing
 * the results as well would be a second copy to keep in step. What is committed is this script and
 * `feature-graphic.html`, which is the part that was genuinely at risk of being lost — the assets
 * were first produced by hand in a scratch directory, and without this file the next release would
 * have meant re-deriving the crops, the safe-zone maths and the font wiring from scratch.
 *
 * Sources, all versioned:
 *   app icon        assets/images/icon.png
 *   feature graphic store/feature-graphic.html + site/fonts/*.woff2
 *   screenshots     site/assets/img/*.jpg   (the same captures the landing page uses)
 *
 * `sharp` and `playwright` are deliberately NOT project dependencies — the same rule Playwright
 * already follows for browser checks. They are needed once per release and would otherwise sit in
 * every `npm ci` on CI. Install them outside the repo and point NODE_PATH at them:
 *
 *   mkdir ../kettle-store-tools && cd ../kettle-store-tools && npm i sharp playwright
 *   npx playwright install chromium-headless-shell
 *   cd -
 *   NODE_PATH=../kettle-store-tools/node_modules node store/build-assets.js
 */

let sharp;
let chromium;
try {
  sharp = require('sharp');
  ({ chromium } = require('playwright'));
} catch {
  console.error(
    'Missing sharp/playwright. They are not project dependencies on purpose — see the header of\n' +
      'this file for the four commands that install them outside the repo and run this script.',
  );
  process.exit(1);
}

const fs = require('node:fs');
const path = require('node:path');

const ROOT = path.join(__dirname, '..');
const OUT = path.join(__dirname, 'out');

/**
 * Play: "Maximum dimension cannot exceed twice the minimum dimension." The captures are 1080x2242,
 * and 2 * 1080 is 2160, so they are 82px over and get rejected. Trimmed from the bottom, which is
 * the gesture-pill band and the only strip with nothing worth keeping — letterboxing to fit would
 * put visible bars down both sides instead.
 */
const SHOT_W = 1080;
const SHOT_H = 2160;

/** Order matters: this is the order they appear in the listing. Runner first, after the hook. */
const SHOTS = ['today', 'session-reps', 'session-hiit', 'workouts', 'programs', 'history', 'import'];

async function icon() {
  // 512x512 32-bit PNG, sRGB, under 1024 KB. The source is a full square with no transparency and
  // no baked corner radius, which is what Play requires — it applies its own 30% radius and shadow,
  // so pre-rounding here would double up. Verified below rather than assumed.
  const out = path.join(OUT, 'icon-512.png');
  await sharp(path.join(ROOT, 'assets/images/icon.png')).resize(512, 512).png().toFile(out);

  const { data } = await sharp(out).ensureAlpha().raw().toBuffer({ resolveWithObject: true });
  let nonOpaque = 0;
  for (let i = 3; i < data.length; i += 4) if (data[i] < 255) nonOpaque++;
  if (nonOpaque > 0) throw new Error(`icon-512.png has ${nonOpaque} non-opaque pixels; Play wants a full square`);

  const kb = fs.statSync(out).size / 1024;
  if (kb > 1024) throw new Error(`icon-512.png is ${kb.toFixed(0)} KB, over Play's 1024 KB limit`);
  console.log(`icon-512.png                 512x512   ${kb.toFixed(0)} KB`);
}

async function featureGraphic() {
  // Rendered in a browser rather than composed in sharp, purely so the wordmark uses the real
  // Space Grotesk from site/fonts rather than whatever fontconfig happens to resolve.
  const browser = await chromium.launch();
  const page = await browser.newPage({ viewport: { width: 1024, height: 500 }, deviceScaleFactor: 1 });
  await page.goto('file:///' + path.join(__dirname, 'feature-graphic.html').replace(/\\/g, '/'));
  await page.evaluate(async () => {
    await document.fonts.ready;
  });
  const unloaded = await page.evaluate(async () => {
    await document.fonts.ready;
    return Array.from(document.fonts)
      .filter((f) => f.status !== 'loaded')
      .map((f) => `${f.family} ${f.weight}`);
  });
  const raw = path.join(OUT, '_feature-raw.png');
  await page.screenshot({ path: raw });
  await browser.close();

  // A silently-unloaded brand font is the failure this catches: the page still renders, just in the
  // system fallback, and nothing about the output looks wrong until it is next to the app.
  if (unloaded.length) throw new Error(`brand fonts did not load: ${unloaded.join(', ')}`);

  // Play wants JPEG or 24-bit PNG with no alpha for this one.
  const out = path.join(OUT, 'feature-graphic-1024x500.png');
  await sharp(raw).flatten({ background: '#c05c2b' }).removeAlpha().png().toFile(out);
  fs.unlinkSync(raw);

  const m = await sharp(out).metadata();
  if (m.hasAlpha) throw new Error('feature graphic still has an alpha channel');
  console.log(`feature-graphic-1024x500.png 1024x500  ${(fs.statSync(out).size / 1024).toFixed(0)} KB`);
}

async function screenshots() {
  let n = 1;
  for (const name of SHOTS) {
    const out = path.join(OUT, `screen-${String(n).padStart(2, '0')}-${name}.png`);
    await sharp(path.join(ROOT, 'site/assets/img', `${name}.jpg`))
      .extract({ left: 0, top: 0, width: SHOT_W, height: SHOT_H })
      .removeAlpha()
      .png()
      .toFile(out);

    const m = await sharp(out).metadata();
    const ratio = Math.max(m.width, m.height) / Math.min(m.width, m.height);
    if (ratio > 2) throw new Error(`${out} is ${ratio.toFixed(3)}:1, over Play's 2:1 ceiling`);
    console.log(`${path.basename(out).padEnd(29)}${m.width}x${m.height} ratio ${ratio.toFixed(2)}`);
    n++;
  }
}

(async () => {
  fs.mkdirSync(OUT, { recursive: true });
  await icon();
  await featureGraphic();
  await screenshots();
  console.log(`\nWrote ${fs.readdirSync(OUT).length} files to store/out/`);
})();
