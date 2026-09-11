import type { ProgressSession } from '@/state/selectors/exercise-progress';

/**
 * Where everything in `ProgressChart` goes, as plain numbers.
 *
 * Split out of the component for the reason `session-steps.ts` is split out of the runner: the rules
 * worth pinning — which dot a session gets, where a label may sit, when one label gives way to the
 * other — are arithmetic, and a test should reach them without mounting an SVG or faking a layout
 * pass. The component measures the two labels and draws what this returns.
 */

/**
 * The drawing's height. Fixed, as decorative geometry is throughout this codebase: nothing in it is a
 * control, and every number it draws is also in the session list under it, which is text and scales.
 */
export const CHART_HEIGHT = 168;
/** Room above the plot for the value labels. */
const PAD_TOP = 20;
/** Room below the baseline for the dates. */
const PAD_BOTTOM = 24;
/** Keeps an end dot's ring inside the drawing rather than clipped at its edge. */
const PAD_X = 6;
/** How far above its point a value label's baseline sits. */
const LABEL_RISE = 10;
/** How far a value label keeps clear of its point, sideways. */
const LABEL_NUDGE = 4;
/** A label's height at the chart's 11px, with a little room: two labels closer than this vertically can collide. */
const LABEL_HEIGHT = 14;
/** The least clear space between two labels side by side. */
const LABEL_GAP = 6;

/**
 * `newBest` for a session that beat everything before it; `latest` for the most recent session when
 * it didn't. They are drawn differently — a filled dot against a hollow ring — because the chart's key
 * names the filled one "New best", and a latest session that fell back must not be read against it.
 */
export type ChartMark = 'newBest' | 'latest' | null;
export type ChartPoint = { x: number; y: number; at: string; mark: ChartMark };
/** A label's left edge and baseline. Always drawn start-anchored: the anchoring decision is made here. */
export type ChartLabel = { x: number; y: number };

export type ChartLayout = {
  baseline: number;
  points: ChartPoint[];
  line: string;
  area: string;
  firstLabel: ChartLabel | null;
  lastLabel: ChartLabel | null;
};

type Input = {
  /** Oldest first, as `exerciseProgress` returns them. */
  sessions: ProgressSession[];
  /** The window the x axis spans. Time, not session order: a two-week gap draws as a gap. */
  from: Date;
  to: Date;
  width: number;
  /** Measured widths of the two value labels; 0 until measured, and a label isn't placed before then. */
  firstLabelWidth: number;
  lastLabelWidth: number;
};

export function progressChartLayout({ sessions, from, to, width, firstLabelWidth, lastLabelWidth }: Input): ChartLayout {
  const baseline = CHART_HEIGHT - PAD_BOTTOM;
  if (sessions.length === 0) return { baseline, points: [], line: '', area: '', firstLabel: null, lastLabel: null };

  const span = Math.max(1, to.getTime() - from.getTime());
  const max = Math.max(...sessions.map((session) => session.value), 0);
  const xOf = (at: string) => PAD_X + ((new Date(at).getTime() - from.getTime()) / span) * (width - PAD_X * 2);
  // From zero, as the sparkline is: from the minimum, 10 kg to 10.5 kg would draw as a cliff.
  const yOf = (value: number) => (max <= 0 ? baseline : baseline - (value / max) * (baseline - PAD_TOP));

  const points: ChartPoint[] = sessions.map((session, index) => ({
    x: xOf(session.at),
    y: yOf(session.value),
    at: session.at,
    mark: session.newBest ? 'newBest' : index === sessions.length - 1 ? 'latest' : null,
  }));

  const first = points[0];
  const last = points.at(-1)!;
  const line = points
    .map((point, index) => `${index === 0 ? 'M' : 'L'}${point.x.toFixed(1)},${point.y.toFixed(1)}`)
    .join(' ');
  const area = `${line} L${last.x.toFixed(1)},${baseline} L${first.x.toFixed(1)},${baseline} Z`;

  const lastLabel = lastLabelWidth > 0 ? placeLabel(last, lastLabelWidth, 'end', width) : null;
  const firstCandidate =
    firstLabelWidth > 0 && points.length > 1 ? placeLabel(first, firstLabelWidth, 'start', width) : null;
  // The latest value is the one the screen is about, so when the two would print over each other the
  // first gives way. Judged on both labels' real boxes, both ways: a fixed gap between the points
  // missed two wide labels ("137.5 lb" and "142.5 lb" need about 88px), and close values — the change
  // this chart shows most often — also sit at almost the same height.
  const firstLabel =
    firstCandidate && lastLabel && !overlaps(firstCandidate, firstLabelWidth, lastLabel, lastLabelWidth)
      ? firstCandidate
      : null;

  return { baseline, points, line, area, firstLabel, lastLabel };
}

/**
 * A label beside its point — starting at the first, ending at the last — kept inside the drawing.
 *
 * The latest session can sit near the left edge (an exercise trained twice early in the window and
 * not since), where ending its label at the point would put the text off the chart. When the
 * preferred side has no room the label **flips** to the other side of its point, rather than sliding
 * just inside the edge: slid, it lands across the line climbing out of the point, which the browser
 * check showed. The final clamp only matters for a label wider than the space on either side.
 */
function placeLabel(point: ChartPoint, labelWidth: number, side: 'start' | 'end', width: number): ChartLabel {
  // Held off the point either side: the line through it is 2px wide, and a label ending exactly at
  // the point had its last letter under the line.
  const after = point.x + LABEL_NUDGE;
  const before = point.x - LABEL_NUDGE - labelWidth;
  let x = side === 'start' ? after : before;
  if (x < 0) x = after;
  else if (x + labelWidth > width) x = before;
  x = Math.min(Math.max(x, 0), Math.max(0, width - labelWidth));
  return { x, y: point.y - LABEL_RISE };
}

function overlaps(a: ChartLabel, aWidth: number, b: ChartLabel, bWidth: number): boolean {
  const apart = a.x + aWidth + LABEL_GAP <= b.x || b.x + bWidth + LABEL_GAP <= a.x;
  return !apart && Math.abs(a.y - b.y) < LABEL_HEIGHT;
}
