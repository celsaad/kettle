# Exercise art

One line drawing per shipped exercise, as plain SVG that both UIs read: RN draws the markup with
`SvgXml`, and native's `:art` module turns each file into an Android VectorDrawable on every build.
`../art.json` says which exercise gets which drawing. The notes on individual strokes stay in the
files as XML comments; what follows is the set as a whole.

## The format, and why it is strict

Every file is the same shell:

```xml
<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 120 96" fill="none">
  <g stroke="currentColor" stroke-width="3" stroke-linecap="round" stroke-linejoin="round">
    …
  </g>
</svg>
```

Inside it, only `<path d>`, `<circle cx cy r>` and `<g transform>`, where the transform is
`translate(x y)` optionally followed by `rotate(deg)` and `scale(s)`. That is the subset `:art` can
turn into a VectorDrawable, and it **fails the Android build** on anything else, so a drawing exported
from an editor needs cleaning up before it lands.

- **One colour, set once.** The root `<g>`'s `currentColor` is the only colour in a file, and nothing
  else sets `stroke` or `fill` (`exercise-art.test.tsx` holds that). It is what lets one file serve the
  light theme, the dark theme and the always-dark runner: RN passes `color`, Android tints, and iOS
  uses template rendering. A fixed colour anywhere vanishes on one of the three.
- **One canvas.** The same viewBox for the whole set is what keeps separately drawn figures looking
  like one style: a figure drawn 1.4× larger than its neighbours is the drift you notice first when
  they're seen together. It is landscape because the movements are: a push-up, a row and a carry are
  all wider than they are tall, and a square canvas would shrink every one to fit its unused headroom.
- **One stroke weight**, deliberately. A single weight across the set is most of what makes it look
  drawn by one hand, and nothing here varies it for emphasis.
- **Ground level is y = 84**, drawn `M8,84 H112`. Figures that stand, lie or press against the floor
  share it.

## The shared parts

SVG has no portable include, so the parts are inlined as a `<g transform>` and labelled with a
comment. Each is authored around its own origin, so the transform places it where the hand is.

- **Ground.** Drawn rather than implied: without it a side-view figure has no orientation, and a
  push-up and a plank both read as a person floating at an angle.
- **Dumbbell.** Centred on the hand. Five strokes: the bar, and a near and far plate on each side.
- **Barbell.** The dumbbell's five strokes, four times as wide. Width is the only thing that tells
  the two apart at this size, so it is exaggerated rather than measured: a bar drawn to scale beside
  a figure would take up most of the canvas. Anything narrower read as a dumbbell held in one hand,
  which the barbell pack cannot afford.
- **Kettlebell.** Centred on the *body*, so the mass is placed and the handle follows. It spans
  roughly 13 above the origin to 9 below. **The bottom is flat, and that is not decoration**: the
  first version was a circle with an arc on top, every drawing using it then held two circles of
  nearly the same size, one of them a head, and on a contact sheet the swing read as a two-headed
  figure. A domed body on a flat base shares no silhouette with a head. The rotation is how it is
  carried: 180° for the upside-down goblet and rack holds, -90° for a swing, handle pointing back at
  the hands.
- **Wall.** Any vertical thing a hand can rest against: a door frame, a counter's edge, a post. One
  bare stroke, with no hatching to say which, because the drawing only has to say "something solid is
  here". It exists because three low-impact movements are *defined* by their support.
- **Chair.** Side-on, from the top of the seat, where a person meets it. Four strokes: seat, back,
  two legs. Furniture is otherwise banned (below), but here the chair is the equipment, and "sit to
  stand" and "seated march" have no subject without one.

## The sets

All side-on, facing right. One viewpoint is worth keeping: a figure drawn front-on among side-on
neighbours reads as a different illustrator, and side-on is the view that shows a hinge, a depth and
a straight line, which is what these drawings exist to show.

- **Dumbbell.** Side-on has a happy consequence: the far dumbbell sits directly behind the near one,
  so a two-handed press is honestly drawn with a single bell. No bench and no rack: the set is
  iconographic, the movement and the load, and furniture is the first thing that turns a small
  drawing to mud.
- **Barbell.** **The bench is the one piece of gym furniture in the set, and it took an argument.**
  The ban above is why the floor press is drawn on the floor. A bench press has no such escape:
  without the bench it is a floor press, a different exercise that already has a drawing. So the
  bench is here, held to three strokes, and the ban stands everywhere else: no rack, no plates on the
  floor, no gym. The pull-up has **no ground line**, for the same reason the AMRAP ring has none: the
  feet are off the floor, and a line under them would put them back on it.
- **Conditioning.** The only drawings that have to say something about *time* rather than about a
  shape, which is where the source sheet went wrong: its stopwatch read "60s" and its dial "12 min".
  Both are values the user can edit, so the drawing would go quietly wrong in the one place the app
  claims to show your own data. Nothing here carries a numeral: a stopwatch says "timed" and a loop
  says "repeated" without saying how long or how many.
- **Kettlebell.** Three are the dumbbell poses with a different implement (goblet squat, row, carry),
  which is why the bell has to be unmistakable at this size. The handle does it: swap the bell for a
  circle and each becomes a person holding a ball. So the handle's direction is drawn honestly: back
  at the hands on the swing, downward for the two upside-down holds.
- **Low impact** (Steady & Strong). The first drawings where the *support* is half the movement: a
  wall push-up without its wall is a figure toppling forward, and a sit-to-stand without a chair is a
  squat. The wall and chair are drawn thin, so the figure stays the darkest thing on the canvas by
  having the most strokes. Nothing here is drawn frail: same height, head and stroke as the barbell
  set, because the drawing describes a movement, not a person, and a hunched figure would be the app
  saying what it assumes about somebody.

## Which exercise gets which drawing

`../art.json` maps exercise **id** to drawing, across the seed and every content pack, in one map:
nothing downstream knows which library an id came from, and a pack merged into somebody's library
is their library.

The id is what makes this work in every language. The seed is one English structural definition, and
each `<lang>.json` replaces only `name` and `notes` against the same ids, so a Portuguese user's
*Flexões* is still `pushups` underneath. Two consequences, both accepted:

- The art follows a **rename**, which is right, and would follow a *repurposing* too: a `pushups`
  entry edited into something else keeps the push-up drawing. Low stakes.
- A user's own exercise can land on a seeded id: delete the seeded `plank`, create your own "Plank",
  and `slugify` gives it `plank` again, art included. That's a feature.

Two ids may share a drawing: `gym-cooldown` uses `easy-cardio`, since "ten easy minutes on whatever is
free" is the same picture with or without barbells around it.

`noArt` lists the shipped exercises that deliberately have no drawing. That is rest, one per library,
because there is no posture to draw and a figure sitting down would suggest one. It exists so the
coverage test can tell "we decided against art here" from "someone added an exercise and forgot the
drawing", which would otherwise be silent.

Adding a drawing is a file here, an entry in `art.json`, an import in
`src/components/exercise-art.tsx`, and an `exerciseArt.<id>` description in all three locale bundles.
The jest suite fails if any of the four is missing, and `:art` fails the Android build on the first
two.
