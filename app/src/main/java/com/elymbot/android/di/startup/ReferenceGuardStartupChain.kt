package com.elymbot.android.di.startup

import javax.inject.Inject

internal class ReferenceGuardStartupChain @Inject constructor() : AppStartupChain {
    override fun run() = Unit
}
