import { progressChartLayout } from '@/components/progress-chart-layout';
import type { ProgressSession } from '@/state/selectors/exercise-progress';

/**
 * Where `ProgressChart` puts its dots and labels. Pure arithmetic, so it is pinned here rather than by
 * mounting an SVG; how the result looks is checked in the running app.
 */

// An eight-week window, and a drawing about as wide as the chart card on a phone.
const FROM = new Date('2026-07-17T00:00:00.000Z');
const TO = new Date('2026-09-11T00:00:00.000Z');
const WIDTH = 320;

function session(day: number, value: number, newBest = false): ProgressSession {
  return { at: new Date(FROM.getTime() + day * 86_400_000).toISOString(), value, newBest };
}

function layout(sessions: ProgressSession[], labelWidths = { first: 40, last: 40 }) {
  return progressChartLayout({
    sessions,
    from: FROM,
    to: TO,
    width: WIDTH,
    firstLabelWidth: labelWidths.first,
    lastLabelWidth: labelWidths.last,
  });
}

describe('dots', () => {
  /**
   * The latest session always gets a mark, and it used to be the same filled dot the key under the
   * chart calls "New best" — so 10 kg, 12 kg, 11 kg put a "New best" dot on the 11, while the session
   * list correctly marked only the 12.
   */
  it('marks a latest session that fell back as the latest, not as a new best', () => {
    const marks = layout([session(0, 10), session(20, 12, true), session(40, 11)]).points.map((point) => point.mark);

    expect(marks).toEqual([null, 'newBest', 'latest']);
  });

  it('marks a latest session that did set a new best as a new best', () => {
    const marks = layout([session(0, 10), session(40, 12, true)]).points.map((point) => point.mark);

    expect(marks).toEqual([null, 'newBest']);
  });
});

describe('labels stay inside the drawing', () => {
  // An exercise trained twice early in the window and not since still has a row, and its latest point
  // sits a few pixels from the left edge — where a label right-aligned to the point runs off the chart.
  it('keeps the latest label inside the left edge', () => {
    const { lastLabel } = layout([session(1, 10), session(2, 12, true)], { first: 40, last: 50 });

    expect(lastLabel!.x).toBeGreaterThanOrEqual(0);
  });

  it('keeps the first label inside the right edge', () => {
    // Far apart in value, so the two labels don't collide and the first one is drawn at all.
    const { firstLabel } = layout([session(54, 1), session(55, 10, true)], { first: 40, last: 40 });

    expect(firstLabel!.x + 40).toBeLessThanOrEqual(WIDTH);
  });
});

describe('labels never print over each other', () => {
  /**
   * The case this chart shows most: a small change. 137.5 → 142.5 lb two weeks apart puts the points
   * about 77px apart, which cleared the old fixed 72px gap — but the two labels need about 88px, and
   * scaled from zero the two values sit at almost the same height. The first gives way.
   */
  it('drops the first label when two close values would collide', () => {
    const { firstLabel, lastLabel } = layout([session(0, 137.5), session(14, 142.5, true)], { first: 44, last: 44 });

    expect(firstLabel).toBeNull();
    expect(lastLabel).not.toBeNull();
  });

  // Close together side by side but far apart in height: both fit, one above the other.
  it('keeps both when the values sit far apart vertically', () => {
    const { firstLabel } = layout([session(0, 5), session(7, 20, true)]);

    expect(firstLabel).not.toBeNull();
  });

  it('keeps both when the ends are far apart', () => {
    const { firstLabel } = layout([session(0, 137.5), session(50, 142.5, true)], { first: 44, last: 44 });

    expect(firstLabel).not.toBeNull();
  });

  // Until the labels are measured there is no way to know whether they fit, so neither is placed.
  it('places no label before it has been measured', () => {
    const result = layout([session(0, 10), session(40, 12, true)], { first: 0, last: 0 });

    expect(result.firstLabel).toBeNull();
    expect(result.lastLabel).toBeNull();
  });
});
