package com.casco.kettle.shell.rn

import android.content.Intent

/**
 * This intent, as React Native needs to see it to route a link: RN reports `data` as the initial URL
 * only under ACTION_VIEW (an M0 build fact).
 */
fun Intent.withViewAction(): Intent = apply { if (data != null) action = Intent.ACTION_VIEW }
