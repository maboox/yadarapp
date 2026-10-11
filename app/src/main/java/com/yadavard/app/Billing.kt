package com.yadavard.app

import android.content.Context
import android.content.ContextWrapper
import androidx.activity.ComponentActivity
import ir.cafebazaar.poolakey.Connection
import ir.cafebazaar.poolakey.Payment
import ir.cafebazaar.poolakey.config.PaymentConfiguration
import ir.cafebazaar.poolakey.config.SecurityCheck
import ir.cafebazaar.poolakey.entity.PurchaseInfo
import ir.cafebazaar.poolakey.exception.BazaarNotFoundException
import ir.cafebazaar.poolakey.request.PurchaseRequest

/**
 * Token packs through Cafe Bazaar (Poolakey). The local signature check is off on purpose: the Yadar server
 * verifies every purchase with Bazaar's API before adding tokens, and consumes it there.
 */
object Billing {
    sealed interface Result {
        data class Bought(val info: PurchaseInfo) : Result
        data object Canceled : Result
        data class Failed(val message: String) : Result
    }

    private fun payment(context: Context) = Payment(context, PaymentConfiguration(localSecurityCheck = SecurityCheck.Disable))

    private fun reason(e: Throwable): String = when (e) {
        is BazaarNotFoundException -> t("برای خرید، برنامهٔ «بازار» باید روی گوشی نصب باشد.", "Cafe Bazaar must be installed to buy.")
        else -> t("ارتباط با بازار برقرار نشد. ", "Could not connect to Bazaar. ") + (e.message ?: e.javaClass.simpleName)
    }

    /** Starts Bazaar's payment screen for [sku]; [payload] is the Yadar user id, checked again by the server. */
    fun buy(activity: ComponentActivity, sku: String, payload: String, onResult: (Result) -> Unit) {
        val payment = payment(activity)
        var connection: Connection? = null
        var done = false
        fun finish(result: Result) {
            if (done) return
            done = true
            runCatching { connection?.disconnect() }
            onResult(result)
        }
        connection = payment.connect {
            connectionSucceed {
                payment.purchaseProduct(activity.activityResultRegistry, PurchaseRequest(productId = sku, payload = payload)) {
                    purchaseSucceed { finish(Result.Bought(it)) }
                    purchaseCanceled { finish(Result.Canceled) }
                    purchaseFailed { finish(Result.Failed(reason(it))) }
                    failedToBeginFlow { finish(Result.Failed(reason(it))) }
                }
            }
            connectionFailed { finish(Result.Failed(reason(it))) }
        }
    }

    /**
     * Purchases Bazaar still holds as unconsumed belong to a payment whose tokens never reached the server
     * (the app closed, the network dropped). Hands each one of [userId]'s to [redeem].
     */
    fun recover(context: Context, userId: String, redeem: (PurchaseInfo) -> Unit) {
        if (userId.isBlank()) return
        val payment = payment(context.applicationContext)
        var connection: Connection? = null
        connection = payment.connect {
            connectionSucceed {
                payment.getPurchasedProducts {
                    querySucceed { list ->
                        list.filter { it.payload == userId }.forEach(redeem)
                        runCatching { connection?.disconnect() }
                    }
                    queryFailed { runCatching { connection?.disconnect() } }
                }
            }
            connectionFailed { }
        }
    }
}

/** The activity behind a Compose context, needed to open Bazaar's payment screen. */
fun Context.findActivity(): ComponentActivity? {
    var c: Context? = this
    while (c is ContextWrapper) {
        if (c is ComponentActivity) return c
        c = c.baseContext
    }
    return null
}
