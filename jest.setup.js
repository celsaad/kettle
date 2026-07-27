// Pins the timezone so date-dependent selectors (currentStreak, thisWeekStats, the date labels) are
// evaluated the same way everywhere, instead of varying between a developer's machine and CI's UTC.
// America/New_York is chosen because it observes DST, which the streak regression tests need in order
// to cross a 23- and a 25-hour day.
//
// Caveat, verified rather than assumed: **this has no effect on Windows.** Node reads the OS timezone
// there and ignores TZ, both from the environment and from process.env. So on a Windows machine in a
// DST-free zone (São Paulo, UTC) the DST tests still pass, but vacuously — both the correct and the
// buggy day-stepping behave identically without a transition to cross. They are meaningful on
// Linux/macOS and on CI, where the workflow also sets TZ directly.
process.env.TZ = 'America/New_York';
