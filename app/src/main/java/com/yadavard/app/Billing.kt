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
 * Token packs through Cafe Bazaar (Poolakey). The local signature check is off on purpose: the Yadimo server
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

    /** Starts Bazaar's payment screen for [sku]; [payload] is the Yadimo user id, checked again by the server. */
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
     * Hands a finished purchase to the Yadimo server, then consumes it on Bazaar when the server asks the app to
     * (so the pack can be bought again). Returns the tokens added.
     */
    suspend fun redeem(context: Context, info: PurchaseInfo): Double {
        val result = Account.redeem(context, info.packageName, info.productId, info.purchaseToken, info.orderId,
            info.originalJson, info.dataSignature)
        if (result.consume) {
            val ok = kotlinx.coroutines.suspendCancellableCoroutine { cont ->
                consume(context, info.purchaseToken) { if (cont.isActive) cont.resumeWith(kotlin.Result.success(it)) }
            }
            if (ok) Account.consumed(context, info.purchaseToken)
        }
        return result.added
    }

    /** Consumes a purchase on Bazaar (after the server credited it) so the same pack can be bought again. */
    fun consume(context: Context, purchaseToken: String, onDone: (Boolean) -> Unit) {
        val payment = payment(context.applicationContext)
        var connection: Connection? = null
        var done = false
        fun finish(ok: Boolean) {
            if (done) return
            done = true
            runCatching { connection?.disconnect() }
            onDone(ok)
        }
        connection = payment.connect {
            connectionSucceed {
                payment.consumeProduct(purchaseToken) {
                    consumeSucceed { finish(true) }
                    consumeFailed { finish(false) }
                }
            }
            connectionFailed { finish(false) }
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

/**
 * Bazaar's in-app login, the same intent the CafeBazaarAuth library sends: the installed Bazaar app asks the
 * user once and answers with a stable account id for this app.
 */
object BazaarLogin {
    private const val BAZAAR = "com.farsitel.bazaar"

    fun intent(context: Context): android.content.Intent =
        android.content.Intent("ir.cafebazaar.intent.action.IAL", android.net.Uri.parse("bazaar://inapplogin")).apply {
            setPackage(BAZAAR)
            putExtra("packageName", context.packageName)
            putExtra("permissionScope", intArrayOf(0)) // ACCOUNT_ID
        }

    fun installed(context: Context): Boolean =
        runCatching { context.packageManager.getPackageInfo(BAZAAR, 0); true }.getOrDefault(false)

    /** The account id from Bazaar's answer, or null when the user declined. */
    fun accountId(data: android.content.Intent?): String? {
        val response = data?.extras?.getString("response") ?: return null
        return runCatching { org.json.JSONObject(response).getString("accountId") }.getOrNull()?.takeIf { it.isNotBlank() }
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
