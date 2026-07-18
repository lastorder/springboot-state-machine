package com.example.statemachine.application.barrier

object CommonBarrier {
    const val SVS = "SVS"
    const val PRICE = "PRICE"
    const val FINANCE = "FINANCE"
    const val BODYBUILDER = "BODYBUILDER"
    const val CONTRACT_ROLES = "CONTRACT_ROLES"
    const val PRICING = "PRICING"
    const val PAYMENT_SPLIT = "PAYMENT_SPLIT"
    const val FINANCING_BLUEPRINT = "FINANCING_BLUEPRINT"

    val DE_MARKET_BARRIERS = setOf(SVS, PRICE, FINANCE)
    val IT_MARKET_BARRIERS =
        setOf(
            SVS,
            BODYBUILDER,
            CONTRACT_ROLES,
            PRICING,
            PAYMENT_SPLIT,
            FINANCING_BLUEPRINT,
        )
}
