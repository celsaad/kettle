package expo.modules.startupmarker

import expo.modules.kotlin.functions.Queues
import expo.modules.kotlin.modules.Module
import expo.modules.kotlin.modules.ModuleDefinition

/**
 * Android's "fully drawn" startup marker, called from JS once Home has rendered its content
 * (src/hooks/startup-marker.ts). It's what the startup Macrobenchmark reads as time to full display,
 * and the same module in today's app and in the shell is what makes the two comparable.
 *
 * `currentActivity` is the prebuilt app's MainActivity in one and the shell's in the other. Android
 * records only the first call per launch, so a stray second one costs nothing.
 */
class StartupMarkerModule : Module() {
  override fun definition() = ModuleDefinition {
    Name("StartupMarker")

    AsyncFunction("reportFullyDrawn") {
      appContext.currentActivity?.reportFullyDrawn()
    }.runOnQueue(Queues.MAIN)
  }
}
