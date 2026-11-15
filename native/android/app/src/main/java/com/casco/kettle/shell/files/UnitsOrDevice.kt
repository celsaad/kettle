package com.casco.kettle.shell.files

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration
import com.casco.kettle.domain.UnitSystem
import com.casco.kettle.shell.i18n.deviceUnitSystem

/** The reader's units: their setting, or their device's until they've chosen. */
@Composable
internal fun LatestFiles.unitsOrDevice(): UnitSystem =
    preferences?.unitSystem ?: deviceUnitSystem(LocalConfiguration.current.locales[0])
