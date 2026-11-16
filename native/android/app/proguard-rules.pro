# R8's keep rules for the release and benchmark builds (#367), beyond the libraries' own consumer
# rules and AGP's default file. Short on purpose: a rule here is a claim that R8 can't see a use, so
# each one names the use.
#
# What needed none, checked rather than assumed:
# - kotlinx-serialization: the runner-state file goes through the JSON element API, with no compiler
#   plugin and no @Serializable type (shared/build.gradle.kts), so nothing is looked up by name.
# - Robolectric: tests run against the unminified debug build only (app/build.gradle.kts disables
#   them for release and benchmark), so nothing they reach needs keeping.
# - Components the manifest names (the activity, the runner's service, the reminder's receivers,
#   NavigateReceiver in the benchmark build): AAPT writes their rules itself.

# Play Vitals' stack traces: line numbers survive, and the mapping file (in the bundle, and kept by
# android.yml) turns the renamed frames back. The file name is folded into one, which is all a frame
# needs once the class is mapped.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# An I/O failure with no message is reported by its class's simple name (WriteRefusal.kt,
# ImportRoute.kt), which the user sees in a refusal. Renamed, that would read "a".
-keepnames class * extends java.lang.Throwable
