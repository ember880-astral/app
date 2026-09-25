package com.astralofthesun.app.data

import androidx.compose.runtime.mutableStateListOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/* ============================================================
   Purchase-request process (native port of Astral.topup).
   Every purchase (manual Solars/Gems top-up, Premium, package,
   server-offer claim) creates a request. With no backend wired
   the request parks at AwaitingBackend; assign `onRequest` to
   hand it to your payment server and drive it to Fulfilled/Failed.
   Nothing is charged on-device.
   ============================================================ */

enum class ReqStatus { Created, Processing, AwaitingBackend, Fulfilled, Failed }

data class PurchaseRequest(
    val id: String,
    val kind: String,              // "solars" | "gems" | "premium" | "offer"
    val amount: Long? = null,
    val packageId: String? = null,
    var status: ReqStatus = ReqStatus.Created,
)

class TopUp {
    val packages = Packages()
    val offers = mutableStateListOf<ServerOffer>()
    val premium = mutableStateListOf<TopUpPackage>()

    val requests = mutableStateListOf<PurchaseRequest>()

    /** Backend hook: return true to fulfil the request, false to fail it. */
    var onRequest: (suspend (PurchaseRequest) -> Boolean)? = null

    private var seq = 0
    private val scope = CoroutineScope(Dispatchers.Default)

    class Packages {
        val solars = mutableStateListOf<TopUpPackage>()
        val gems = mutableStateListOf<TopUpPackage>()
    }

    fun create(kind: String, amount: Long? = null, packageId: String? = null): PurchaseRequest {
        seq += 1
        val req = PurchaseRequest(
            id = "req-$seq-" + System.currentTimeMillis().toString(36),
            kind = kind,
            amount = amount,
            packageId = packageId,
        )
        requests.add(0, req)

        val hook = onRequest
        if (hook == null) {
            req.status = ReqStatus.AwaitingBackend
            refresh(req)
            return req
        }

        req.status = ReqStatus.Processing
        refresh(req)
        scope.launch {
            val ok = runCatching { hook(req) }.getOrDefault(false)
            req.status = if (ok) ReqStatus.Fulfilled else ReqStatus.Failed
            refresh(req)
        }
        return req
    }

    // Re-place the item so Compose snapshots notice the mutation.
    private fun refresh(req: PurchaseRequest) {
        val i = requests.indexOfFirst { it.id == req.id }
        if (i >= 0) {
            requests[i] = req.copy(status = req.status)
        }
    }
}
