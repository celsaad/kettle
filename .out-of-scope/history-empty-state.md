# A first-run empty state on History

History doesn't get an empty state of its own. With nothing logged it shows its summary line at 0,
the search field and the rule, and nothing below them. There's no "Sessions you finish show up here"
line, and no link to start a session.

## Why this is out of scope

An empty log is simply what a fresh install looks like. It isn't a fault, and it isn't a dead end
the screen has to get the user out of. The summary line already tells them what's there: none. The
invitation to train belongs on Home, which is where a new user lands. Home already makes that offer:
the first-run card, the starter-pack link, and "Start an empty session", which shows even on a rest
day or with no workouts. If History made the same offer again, it would be a second copy of Home's
first-run block, and the two would drift apart.

The only empty body History handles is the one a search causes: a log with sessions in it where
nothing matched the query. That case gets `NoResults`, because that blank really is unexplained.
`HistoryScreen.kt` keeps this rule as a comment right above that check:

```kotlin
// No first-run half: an empty log is what a new install is, and the line above already
// reads 0. This covers only the blank body a search makes.
if (visible.isEmpty() && logged.isNotEmpty()) item(key = "no-results") { NoResults(query) }
```

Hiding the search field while the log is empty went with the rest of the request. It isn't a
separate cut: the field is part of the screen's fixed frame, and with nothing to match it costs
nothing.

Stats isn't a counterexample. It stays reachable from an empty History because arriving at zeros
there explains what Stats *is*. That's a link out of History, not an empty state on it.

## Prior requests

- #268: "History: an empty log shows a zero summary over a blank screen"
