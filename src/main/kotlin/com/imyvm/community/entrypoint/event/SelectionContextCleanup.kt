package com.imyvm.community.entrypoint.event

import com.imyvm.community.util.SelectionReturnContext
import com.imyvm.iwg.infra.LazyTicker

fun registerSelectionContextCleanup() {
    LazyTicker.registerTask { server ->
        SelectionReturnContext.cleanupStaleContexts(server)
    }
}
