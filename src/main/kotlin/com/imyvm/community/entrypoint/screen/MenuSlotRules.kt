package com.imyvm.community.entrypoint.screen

internal object MenuSlotRules {
    fun validate(slot: Int, size: Int, existingSlots: Collection<Int>) {
        require(slot in 0 until size) { "Menu button slot out of bounds: $slot" }
        require(slot !in existingSlots) { "Duplicate menu button slot: $slot" }
    }
}
