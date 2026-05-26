package com.example.reelang.ui.common

import androidx.compose.runtime.compositionLocalOf
import com.example.reelang.data.local.LocalDataSource as DbDataSource

val LocalDbSource = compositionLocalOf<DbDataSource?> { null }
