package com.casco.kettle.shell.runner

/**
 * How a sheet sits: inside the runner's column, as RN's pad and pickers are, or across the width, as
 * Coming up does. [Scrolling] is for a sheet of plain rows: at 200% text Sets ran past the screen with
 * its footer cut off (#469). Not for one holding a lazy list, which can't sit in another vertical scroll.
 */
internal enum class SheetLayout { Inset, Scrolling, FullWidth }
