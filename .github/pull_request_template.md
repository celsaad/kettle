<!-- For runner changes (native/android/.../shell/runner: the screens, RunnerService, cues, alerts): run
     scripts/session-check.sh on the Medium_Phone_API_37.0 AVD, then tick what it can't check.
     Delete this list for anything else. -->

### Runner changes: checked by hand

- [ ] `scripts/session-check.sh` passes on the AVD
- [ ] A real Recents swipe mid-session leaves it running, and reopening reattaches
- [ ] Tapping the shade notification reattaches to the running session
- [ ] Music playing in another app keeps playing under a cue, without a pause, by ear
- [ ] A real call on a phone: no cue plays over it
- [ ] A long session (30 min or more) on a phone, screen off, survives the OEM's background killer
