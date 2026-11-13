// PROTOTYPE — throwaway (#399), never merged. Not Kettle's iOS app.
//
// Answers the background-runner device checks P1–P14 from the research on #398: no keep-alive,
// the clock caught up from CLOCK_MONOTONIC by a *replaying* catch-up, foreground cues on a serial
// queue over `.playback` + `.mixWithOthers`, pre-scheduled time-sensitive alerts while away, and a
// runner-state file with a Resume/Finish prompt after process death. Swift rather than Kotlin
// `iosMain` on purpose: every check here is OS behaviour, and Swift skips the framework wiring.
//
// Everything is logged (wall time + CLOCK_MONOTONIC) to the screen, to os_log, and to
// Documents/PROTOTYPE-log.txt, which the Files app shows and "Share log" exports.

import AVFoundation
import HealthKit
import os
import SwiftUI
import UIKit
import UserNotifications

// MARK: - Workouts (the research's test workouts)

struct Step: Codable { let name: String; let seconds: Double? } // nil = only the user ends it

let workouts: [String: [Step]] = [
    "basic": [
        Step(name: "Count-in", seconds: 3), Step(name: "Rest A", seconds: 20),
        Step(name: "Timed hold", seconds: 15), Step(name: "Rest B", seconds: 20),
        Step(name: "Reps (tap Done)", seconds: nil), Step(name: "Rest C", seconds: 30),
    ],
    "hiit": [Step(name: "Count-in", seconds: 3)]
        + (1 ... 8).flatMap { [Step(name: "Work \($0)", seconds: 20), Step(name: "Rest \($0)", seconds: 10)] },
]

// MARK: - Clocks

func clockSec(_ id: clockid_t) -> Double {
    var ts = timespec()
    clock_gettime(id, &ts)
    return Double(ts.tv_sec) + Double(ts.tv_nsec) / 1e9
}

func mono() -> Double { clockSec(CLOCK_MONOTONIC) }

func f(_ x: Double, _ digits: Int = 3) -> String { String(format: "%.\(digits)f", x) }

/// P2/P14: every candidate clock, so the log shows which ones count through sleep.
func clockReport() -> String {
    var tb = mach_timebase_info_data_t()
    mach_timebase_info(&tb)
    let scale = Double(tb.numer) / Double(tb.denom) / 1e9
    return "wall=\(f(Date().timeIntervalSince1970)) MONOTONIC=\(f(clockSec(CLOCK_MONOTONIC)))"
        + " MONOTONIC_RAW=\(f(clockSec(CLOCK_MONOTONIC_RAW))) UPTIME_RAW=\(f(clockSec(CLOCK_UPTIME_RAW)))"
        + " mach_abs=\(f(Double(mach_absolute_time()) * scale)) mach_cont=\(f(Double(mach_continuous_time()) * scale))"
        + " systemUptime=\(f(ProcessInfo.processInfo.systemUptime)) tz=\(TimeZone.current.identifier)"
}

// MARK: - Log

final class Log: ObservableObject {
    static let shared = Log()
    @Published var lines: [String] = []
    let url = FileManager.default.urls(for: .documentDirectory, in: .userDomainMask)[0]
        .appendingPathComponent("PROTOTYPE-log.txt")
    private let q = DispatchQueue(label: "log")
    private let os = Logger(subsystem: "com.casco.kettle.prototype.runner", category: "proto")
    private let iso: ISO8601DateFormatter = {
        let d = ISO8601DateFormatter()
        d.formatOptions = [.withInternetDateTime, .withFractionalSeconds]
        d.timeZone = .current
        return d
    }()

    init() {
        if let s = try? String(contentsOf: url, encoding: .utf8) {
            lines = s.split(separator: "\n").suffix(300).map(String.init)
        }
    }

    func callAsFunction(_ m: String) {
        let line = "\(iso.string(from: Date())) m=\(f(mono())) \(m)"
        os.log("\(line, privacy: .public)")
        q.sync { // sync so the last line before a kill reaches the file
            let data = Data((line + "\n").utf8)
            if let h = try? FileHandle(forWritingTo: url) {
                h.seekToEndOfFile()
                h.write(data)
                try? h.close()
            } else {
                try? data.write(to: url)
            }
        }
        DispatchQueue.main.async {
            self.lines.append(line)
            if self.lines.count > 300 { self.lines.removeFirst(self.lines.count - 300) }
        }
    }

    func clear() {
        q.sync { try? FileManager.default.removeItem(at: url) }
        DispatchQueue.main.async { self.lines = [] }
    }
}

let log = Log.shared

// MARK: - Runner

struct Committed: Codable { let name: String; let start: Double; let end: Double; let planned: Double? }

struct RunState: Codable {
    var workout: String
    var index: Int
    var stepStart: Double // CLOCK_MONOTONIC seconds
    var pausedAt: Double?
    var startWall: Double
    var committed: [Committed]
}

final class Runner: ObservableObject {
    static let shared = Runner()

    @Published var snap: RunState?
    @Published var resumePrompt = false

    /// Every engine read and write happens on this one serial queue, like Android's HandlerLoop.
    let q = DispatchQueue(label: "runner")
    private var st: RunState?
    private var awaitingDecision = false
    private var soundsOn = true
    private var keepAliveOn = false
    private var armed: [DispatchWorkItem] = []
    private var players: [String: AVAudioPlayer] = [:]
    private var interrupted = false
    private var heartbeat: DispatchSourceTimer?
    private var hk: AnyObject?
    var appActive = true
    private let center = UNUserNotificationCenter.current()
    private let health = HKHealthStore()
    private let stateURL: URL

    init() {
        var dir = FileManager.default.urls(for: .applicationSupportDirectory, in: .userDomainMask)[0]
            .appendingPathComponent("runner")
        try? FileManager.default.createDirectory(at: dir, withIntermediateDirectories: true)
        var rv = URLResourceValues()
        rv.isExcludedFromBackup = true
        try? dir.setResourceValues(rv)
        stateURL = dir.appendingPathComponent("PROTOTYPE-state.json")
        for n in ["tick", "exercise-change", "milestone"] {
            if let u = Bundle.main.url(forResource: n, withExtension: "wav") {
                players[n] = try? AVAudioPlayer(contentsOf: u)
                players[n]?.prepareToPlay()
            }
        }
        // P10: observe every AVAudioSession notification by prefix, so iOS 27's renamed life-cycle
        // notifications are caught without guessing their exact names.
        NotificationCenter.default.addObserver(forName: nil, object: nil, queue: nil) { [weak self] n in
            guard n.name.rawValue.hasPrefix("AVAudioSession") else { return }
            self?.q.async { self?.audioEvent(n) }
        }
    }

    // MARK: lifecycle

    func launch() {
        q.async {
            guard let d = try? Data(contentsOf: self.stateURL),
                  let s = try? JSONDecoder().decode(RunState.self, from: d) else { return }
            self.awaitingDecision = true
            log("LAUNCH found a state file: '\(s.workout)' step \(s.index), paused=\(s.pausedAt != nil)")
            self.logDelivered("while dead")
            self.removeRunnerNotifications {
                self.q.async {
                    self.st = s
                    self.publish()
                    DispatchQueue.main.async { self.resumePrompt = true }
                }
            }
        }
    }

    func resumeFromDeath() {
        q.async {
            guard self.awaitingDecision else { return }
            self.awaitingDecision = false
            DispatchQueue.main.async { self.resumePrompt = false }
            self.activateAudio()
            self.catchUp(mono(), "REPLAY-after-death")
            guard var s = self.st else { return }
            if s.pausedAt == nil { s.pausedAt = mono() }
            self.st = s
            log("RESUMED after death, landed paused on '\(self.steps(s)[s.index].name)'")
            self.save()
            self.publish()
            self.startHeartbeat()
            DispatchQueue.main.async { UIApplication.shared.isIdleTimerDisabled = true }
        }
    }

    func finishFromDeath() {
        q.async {
            self.awaitingDecision = false
            guard let s = self.st else { return }
            self.finish(at: s.stepStart, "finished after death, stamped at the last transition")
        }
    }

    /// On entering the background, not on resigning active: a permission dialog, Control Center
    /// (where P8 turns Silent on) or Notification Center also resign the app while the runner is
    /// still on screen, and disarming its cues there would silence a visible runner. Found by the
    /// first simulator run.
    func didEnterBackground() {
        q.async {
            log("BACKGROUND " + clockReport())
            guard self.st != nil, !self.awaitingDecision else { return }
            let task = DispatchQueue.main.sync {
                UIApplication.shared.beginBackgroundTask(withName: "schedule") {}
            }
            self.save()
            if !self.keepAliveOn {
                self.disarm() // no in-app cue may double an alert
                log("live cues disarmed (keep-alive off)")
            }
            self.scheduleAhead {
                DispatchQueue.main.async { UIApplication.shared.endBackgroundTask(task) }
            }
        }
    }

    func becameActive() {
        q.async {
            log("ACTIVE " + clockReport())
            self.logSettings()
            guard !self.awaitingDecision else { return }
            self.logDelivered("while away")
            // Entering the background deactivates the session and posts "interruption began", but
            // no "ended" ever follows on return: without this, every foreground cue after a trip
            // to Home is skipped as interrupted. Found on the iPad (P8).
            if self.st != nil { self.activateAudio() }
            self.removeRunnerNotifications {
                self.q.async {
                    self.catchUp(mono(), "REPLAY")
                    self.arm()
                }
            }
        }
    }

    // MARK: session

    func start(_ w: String, askPermission: Bool = true) {
        if askPermission {
            center.getNotificationSettings { s in
                if s.authorizationStatus == .notDetermined { self.requestPermission() }
            }
        }
        q.async {
            guard self.st == nil else { return }
            self.activateAudio()
            self.st = RunState(workout: w, index: 0, stepStart: mono(), pausedAt: nil,
                               startWall: Date().timeIntervalSince1970, committed: [])
            log("START '\(w)'")
            self.save()
            self.arm()
            self.publish()
            self.startHeartbeat()
        }
        DispatchQueue.main.async { UIApplication.shared.isIdleTimerDisabled = true }
    }

    func doneReps() {
        q.async {
            guard var s = self.st, s.pausedAt == nil, self.steps(s)[s.index].seconds == nil else { return }
            let now = mono()
            s.committed.append(Committed(name: self.steps(s)[s.index].name, start: s.stepStart, end: now, planned: nil))
            log("USER ended '\(self.steps(s)[s.index].name)' after \(f(now - s.stepStart, 1))s")
            s.stepStart = now
            s.index += 1
            self.st = s
            self.afterTransition()
        }
    }

    func togglePause() {
        q.async {
            guard var s = self.st else { return }
            let now = mono()
            if let p = s.pausedAt {
                s.stepStart += now - p
                s.pausedAt = nil
                self.st = s
                log("RESUME")
                self.arm()
            } else {
                self.catchUp(now, "LIVE")
                guard var s2 = self.st else { return }
                s2.pausedAt = now
                self.st = s2
                self.disarm()
                log("PAUSE")
            }
            self.save()
            self.publish()
        }
    }

    func finishNow() {
        q.async {
            guard var s = self.st else { return }
            let now = mono()
            if s.index < self.steps(s).count {
                s.committed.append(Committed(name: self.steps(s)[s.index].name, start: s.stepStart,
                                             end: s.pausedAt ?? now, planned: self.steps(s)[s.index].seconds))
            }
            self.st = s
            self.finish(at: s.pausedAt ?? now, "finished by the user")
        }
    }

    func setSounds(_ on: Bool) { q.async { self.soundsOn = on; log("session sounds \(on ? "on" : "off")") } }

    // MARK: engine (the replaying catch-up)

    private func steps(_ s: RunState) -> [Step] { workouts[s.workout]! }

    /// Each self-ending step ends at its own moment and the next starts there, up to the first
    /// user-ended step, a pause or the end. This is the rule #398 says `:shared` must adopt.
    @discardableResult
    private func catchUp(_ now: Double, _ how: String) -> Int {
        guard var s = st, s.pausedAt == nil else { return 0 }
        let steps = steps(s)
        var n = 0
        while s.index < steps.count, let sec = steps[s.index].seconds, s.stepStart + sec <= now + 0.0005 {
            let end = s.stepStart + sec
            s.committed.append(Committed(name: steps[s.index].name, start: s.stepStart, end: end, planned: sec))
            log("\(how) commit '\(steps[s.index].name)' \(f(sec, 0))s, ended \(f(now - end, 3))s ago")
            s.stepStart = end
            s.index += 1
            n += 1
        }
        st = s
        if n > 0 { afterTransition() }
        return n
    }

    private func afterTransition() {
        guard let s = st else { return }
        if s.index >= steps(s).count {
            finish(at: s.stepStart, "workout complete")
            return
        }
        log("now on '\(steps(s)[s.index].name)'")
        save()
        arm()
        publish()
    }

    // MARK: foreground cues (P1, P3)

    private func disarm() {
        armed.forEach { $0.cancel() }
        armed = []
    }

    private func arm() {
        disarm()
        guard let s = st, s.pausedAt == nil, s.index < steps(s).count, let sec = steps(s)[s.index].seconds else { return }
        let end = s.stepStart + sec
        let now = mono()
        for k in [3.0, 2.0, 1.0] where end - k > now - 0.05 {
            at(end - k) { self.cue("tick", planned: end - k) }
        }
        at(end) {
            self.cue("exercise-change", planned: end)
            self.catchUp(mono(), "LIVE")
        }
    }

    private func at(_ t: Double, _ work: @escaping () -> Void) {
        let item = DispatchWorkItem(block: work)
        armed.append(item)
        // DispatchTime runs on the uptime clock, which stops in sleep; harmless, since every
        // foreground re-arms from a fresh CLOCK_MONOTONIC reading.
        q.asyncAfter(deadline: .now() + max(0, t - mono()), execute: item)
    }

    private func cue(_ name: String, planned: Double) {
        let late = (mono() - planned) * 1000
        guard soundsOn else { log("cue \(name) skipped: sounds off"); return }
        if interrupted { log("cue \(name) SKIPPED: audio interrupted, late=\(f(late, 0))ms"); return }
        let p = players[name]
        p?.currentTime = 0
        let ok = p?.play() ?? false
        log("cue \(name) played=\(ok) main=\(Thread.isMainThread) appActive=\(appActive)"
            + " otherAudio=\(AVAudioSession.sharedInstance().isOtherAudioPlaying) late=\(f(late, 1))ms")
    }

    // MARK: audio session (P3, P8, P10)

    private func activateAudio() {
        let s = AVAudioSession.sharedInstance()
        do {
            try s.setCategory(.playback, options: [.mixWithOthers])
            try s.setActive(true)
            interrupted = false
            log("audio session active (.playback + .mixWithOthers) otherAudio=\(s.isOtherAudioPlaying)")
        } catch {
            log("audio activate FAILED: \(error)")
        }
    }

    private func audioEvent(_ n: Notification) {
        let name = n.name.rawValue
        log("AUDIO \(name) \(n.userInfo.map { "\($0)" } ?? "")")
        if n.name == AVAudioSession.interruptionNotification,
           let raw = n.userInfo?[AVAudioSessionInterruptionTypeKey] as? UInt,
           let type = AVAudioSession.InterruptionType(rawValue: raw) {
            interrupted = type == .began
            if type == .ended { activateAudio() }
        } else if name.contains("Inactive") {
            interrupted = true
        } else if name.contains("Resumption") {
            activateAudio()
        }
    }

    // MARK: background alerts (P4–P8, P12)

    private func scheduleAhead(_ done: @escaping () -> Void) {
        guard let s = st, s.pausedAt == nil else { log("schedule: nothing (no session, or paused)"); done(); return }
        let steps = steps(s)
        let now = mono()
        var t = s.stepStart
        var i = s.index
        var reqs: [UNNotificationRequest] = []
        while i < steps.count, let sec = steps[i].seconds, reqs.count < 60 {
            t += sec
            let next = i + 1 < steps.count ? steps[i + 1].name : "workout done"
            let c = UNMutableNotificationContent()
            c.title = "\(steps[i].name) over"
            c.body = "Next: \(next)"
            c.interruptionLevel = .timeSensitive
            if soundsOn { c.sound = UNNotificationSound(named: UNNotificationSoundName("exercise-change.wav")) }
            c.userInfo = ["plannedWall": Date().timeIntervalSince1970 + (t - now)]
            let trigger = UNTimeIntervalNotificationTrigger(timeInterval: max(t - now, 0.5), repeats: false)
            reqs.append(UNNotificationRequest(identifier: "runner-\(i)", content: c, trigger: trigger))
            i += 1
        }
        let g = DispatchGroup()
        for r in reqs {
            g.enter()
            center.add(r) { e in
                if let e { log("schedule error \(r.identifier): \(e)") }
                g.leave()
            }
        }
        g.notify(queue: q) {
            log("scheduled \(reqs.count) step-end alerts, sound=\(self.soundsOn)")
            self.center.getPendingNotificationRequests { p in
                log("pending total=\(p.count): \(p.map(\.identifier).joined(separator: ","))")
                done()
            }
        }
    }

    private func logDelivered(_ when: String) {
        center.getDeliveredNotifications { d in
            for n in d where n.request.identifier.hasPrefix("runner-") {
                let planned = n.request.content.userInfo["plannedWall"] as? Double ?? 0
                log("delivered \(when) \(n.request.identifier) late=\(f((n.date.timeIntervalSince1970 - planned) * 1000, 0))ms")
            }
        }
    }

    private func removeRunnerNotifications(_ done: @escaping () -> Void) {
        center.getPendingNotificationRequests { p in
            let ids = p.map(\.identifier).filter { $0.hasPrefix("runner-") }
            self.center.removePendingNotificationRequests(withIdentifiers: ids)
            self.center.getDeliveredNotifications { d in
                self.center.removeDeliveredNotifications(
                    withIdentifiers: d.map(\.request.identifier).filter { $0.hasPrefix("runner-") })
                if !ids.isEmpty { log("removed \(ids.count) pending runner alerts") }
                done()
            }
        }
    }

    func requestPermission() {
        center.requestAuthorization(options: [.alert, .sound]) { ok, e in
            log("notification permission granted=\(ok) error=\(String(describing: e))")
            self.logSettings()
        }
    }

    func logSettings() {
        center.getNotificationSettings { s in
            log("notif settings auth=\(s.authorizationStatus.rawValue) timeSensitive=\(s.timeSensitiveSetting.rawValue)"
                + " sound=\(s.soundSetting.rawValue) alert=\(s.alertSetting.rawValue) (0 n/a, 1 off, 2 on)")
        }
    }

    /// P6: a stand-in for the rest-day reminder, to check the runner's alerts don't evict it.
    func scheduleRestDayStandIn() {
        var c = DateComponents()
        c.hour = 9
        let content = UNMutableNotificationContent()
        content.title = "PROTOTYPE rest-day stand-in"
        center.add(UNNotificationRequest(identifier: "restday-PROTOTYPE", content: content,
                                         trigger: UNCalendarNotificationTrigger(dateMatching: c, repeats: true))) { e in
            log("rest-day stand-in scheduled error=\(String(describing: e))")
        }
    }

    // MARK: P9 — HKWorkoutSession keep-alive experiment

    func setKeepAlive(_ on: Bool) {
        q.async { self.keepAliveOn = on }
        guard #available(iOS 26.0, *) else { log("HK: needs iOS 26"); return }
        if on {
            guard HKHealthStore.isHealthDataAvailable() else { log("HK: health data unavailable on this device"); return }
            health.requestAuthorization(toShare: [HKObjectType.workoutType()], read: []) { ok, e in
                log("HK auth ok=\(ok) error=\(String(describing: e))")
                let cfg = HKWorkoutConfiguration()
                cfg.activityType = .highIntensityIntervalTraining
                cfg.locationType = .indoor
                do {
                    let s = try HKWorkoutSession(healthStore: self.health, configuration: cfg)
                    s.startActivity(with: Date())
                    self.hk = s
                    log("HK workout session started, state=\(s.state.rawValue)")
                } catch {
                    log("HK workout session FAILED: \(error)")
                }
            }
        } else if let s = hk as? HKWorkoutSession {
            s.end()
            hk = nil
            log("HK workout session ended (no builder, nothing saved)")
        }
    }

    private func startHeartbeat() {
        heartbeat?.cancel()
        let t = DispatchSource.makeTimerSource(queue: DispatchQueue(label: "heartbeat"))
        t.schedule(deadline: .now() + 1, repeating: 1)
        t.setEventHandler { [weak self] in
            if self?.appActive == false { log("heartbeat (background) " + clockReport()) }
        }
        t.resume()
        heartbeat = t
    }

    // MARK: finish and persistence

    private func finish(at stamp: Double, _ reason: String) {
        guard let s = st else { return }
        var out = "PROTOTYPE session '\(s.workout)' started \(Date(timeIntervalSince1970: s.startWall)), \(reason)\n"
        for c in s.committed {
            let actual = c.end - c.start
            out += "  \(c.name): actual \(f(actual, 2))s" + (c.planned.map { " planned \(f($0, 0))s drift \(f(actual - $0, 3))s" } ?? "") + "\n"
        }
        out += "  finish stamped \(f(stamp - s.stepStart, 3))s after the last transition\n"
        out.split(separator: "\n").forEach { log(String($0)) }
        let docs = FileManager.default.urls(for: .documentDirectory, in: .userDomainMask)[0]
        try? out.write(to: docs.appendingPathComponent("PROTOTYPE-session-\(Int(s.startWall)).txt"), atomically: true, encoding: .utf8)
        st = nil
        disarm()
        try? FileManager.default.removeItem(at: stateURL)
        removeRunnerNotifications {}
        try? AVAudioSession.sharedInstance().setActive(false, options: .notifyOthersOnDeactivation)
        heartbeat?.cancel()
        heartbeat = nil
        publish()
        DispatchQueue.main.async { UIApplication.shared.isIdleTimerDisabled = false }
    }

    private func save() {
        guard let s = st, let d = try? JSONEncoder().encode(s) else { return }
        try? d.write(to: stateURL, options: .atomic)
    }

    private func publish() {
        let s = st
        DispatchQueue.main.async { self.snap = s }
    }
}

// MARK: - App

@main
struct RunnerPrototypeApp: App {
    @StateObject private var runner = Runner.shared
    @StateObject private var logStore = Log.shared

    init() {
        let nc = NotificationCenter.default
        let r = Runner.shared
        nc.addObserver(forName: UIApplication.willResignActiveNotification, object: nil, queue: .main) { _ in
            log("RESIGN ACTIVE (not background: cues stay armed)")
        }
        nc.addObserver(forName: UIApplication.didEnterBackgroundNotification, object: nil, queue: .main) { _ in
            r.appActive = false
            r.didEnterBackground()
        }
        nc.addObserver(forName: UIApplication.didBecomeActiveNotification, object: nil, queue: .main) { _ in
            r.appActive = true
            r.becameActive()
        }
        nc.addObserver(forName: UIApplication.significantTimeChangeNotification, object: nil, queue: .main) { _ in
            log("SIGNIFICANT TIME CHANGE " + clockReport())
        }
        nc.addObserver(forName: .NSSystemTimeZoneDidChange, object: nil, queue: .main) { _ in
            log("TIME ZONE CHANGED " + clockReport())
        }
        log("LAUNCH " + clockReport())
        r.launch()
        // Simulator smoke test: `simctl launch … -autostart basic`.
        if let i = CommandLine.arguments.firstIndex(of: "-autostart"), i + 1 < CommandLine.arguments.count {
            r.start(CommandLine.arguments[i + 1], askPermission: false)
        }
        if CommandLine.arguments.contains("-autoresume") {
            DispatchQueue.main.asyncAfter(deadline: .now() + 1) { r.resumeFromDeath() }
        }
    }

    var body: some Scene {
        WindowGroup { ContentView().environmentObject(runner).environmentObject(logStore) }
    }
}

struct ContentView: View {
    @EnvironmentObject var r: Runner
    @EnvironmentObject var l: Log
    @State private var sounds = true
    @State private var keepAlive = false

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text("PROTOTYPE — background runner (#399)").font(.caption).foregroundStyle(.red)
            TimelineView(.periodic(from: .now, by: 0.25)) { _ in status }
            HStack {
                Button("Start basic") { r.start("basic") }
                Button("Start HIIT") { r.start("hiit") }
            }.disabled(r.snap != nil)
            HStack {
                Button("Done (reps)") { r.doneReps() }
                Button(r.snap?.pausedAt == nil ? "Pause" : "Resume") { r.togglePause() }
                Button("Finish") { r.finishNow() }
            }.disabled(r.snap == nil)
            Toggle("Session sounds", isOn: $sounds).onChange(of: sounds) { _, v in r.setSounds(v) }
            Toggle("HKWorkoutSession keep-alive (P9)", isOn: $keepAlive).onChange(of: keepAlive) { _, v in r.setKeepAlive(v) }
            HStack {
                Button("Ask notifications") { r.requestPermission() }
                Button("Rest-day stand-in") { r.scheduleRestDayStandIn() }
            }
            HStack {
                ShareLink(item: l.url) { Text("Share log") }
                Button("Clear log") { l.clear() }
            }
            ScrollViewReader { proxy in
                ScrollView {
                    LazyVStack(alignment: .leading, spacing: 2) {
                        ForEach(Array(l.lines.enumerated()), id: \.offset) { i, line in
                            Text(line).font(.system(size: 9, design: .monospaced)).id(i)
                        }
                    }
                }
                .onChange(of: l.lines.count) { _, n in if n > 0 { proxy.scrollTo(n - 1, anchor: .bottom) } }
            }
        }
        .padding()
        .buttonStyle(.bordered)
        .controlSize(.small)
        .font(.footnote)
        .alert("Session found after process death", isPresented: $r.resumePrompt) {
            Button("Resume") { r.resumeFromDeath() }
            Button("Finish", role: .destructive) { r.finishFromDeath() }
        }
    }

    @ViewBuilder private var status: some View {
        if let s = r.snap, s.index < (workouts[s.workout]?.count ?? 0) {
            let step = workouts[s.workout]![s.index]
            let elapsed = (s.pausedAt ?? mono()) - s.stepStart
            let shown = step.seconds.map { "\(f(max(0, $0 - elapsed), 1))s left" } ?? "\(f(elapsed, 0))s, tap Done"
            Text("\(step.name) — \(shown)\(s.pausedAt != nil ? " (paused)" : "")").font(.title3.monospacedDigit())
        } else {
            Text("No session").font(.title3)
        }
    }
}
