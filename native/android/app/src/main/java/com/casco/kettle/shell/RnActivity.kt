package com.casco.kettle.shell

import android.os.Bundle
import com.casco.kettle.brownfield.BrownfieldActivity

/**
 * Hosts the whole expo-router app as one React Native surface. Started with intent data
 * (`kettle://history`) to test whether expo-router opens at that route (spike Q3).
 */
class RnActivity : BrownfieldActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    showReactNativeFragment()
  }
}
