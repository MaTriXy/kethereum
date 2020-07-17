package org.kethereum.rpc.min3

import com.squareup.moshi.Moshi
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import org.kethereum.model.ChainId
import org.kethereum.rpc.BaseEthereumRPC
import org.kethereum.rpc.JSONMediaType
import org.kethereum.rpc.RPCTransport
import org.kethereum.rpc.min3.model.IN3NodeListResponse

val MAINNET_BOOTNODES = listOf(
        "https://in3-v2.slock.it/mainnet/nd-1",
        "https://in3-v2.slock.it/mainnet/nd-2",
        "https://in3-v2.slock.it/mainnet/nd-3"
)

val GOERLI_BOOTNODES = listOf(
        "https://in3-v2.slock.it/goerli/nd-1",
        "https://in3-v2.slock.it/goerli/nd-2"
)

fun getMin3BootnNdesByChainId(chainId: ChainId) = when (chainId.value.toLong()) {
    1L -> MAINNET_BOOTNODES
    5L -> GOERLI_BOOTNODES
    else -> null
}

internal val in3nodeListResponseAdapter = Moshi.Builder().build().adapter<IN3NodeListResponse>(IN3NodeListResponse::class.java)

fun getMin3RPC(bootNodes: List<String> = MAINNET_BOOTNODES,
               okHttpClient: OkHttpClient = OkHttpClient.Builder().build()
) = BaseEthereumRPC(MIN3Transport(bootNodes, okHttpClient))

fun getMin3RPC(chainId: ChainId,
               okHttpClient: OkHttpClient = OkHttpClient.Builder().build()
) = getMin3BootnNdesByChainId(chainId)?.let { nodes ->
    getMin3RPC(nodes, okHttpClient)
}

class MIN3Transport(private val bootNodes: List<String>,
                    private val okHttpClient: OkHttpClient = OkHttpClient.Builder().build(),
                    private val max_retries: Int = 42
) : RPCTransport {

    private val nodes = mutableSetOf<String>()

    init {
        nodes.addAll(bootNodes)
    }

    override fun call(payload: String): String? {
        maybeUpdateNodeList()
        return requestWithRepeat(payload)
    }

    private fun requestWithRepeat(payload: String): String? {

        repeat(max_retries) {
            val request = buildRequest(payload)
            val maybeResult = try {
                val execute = okHttpClient.newCall(request).execute()
                val responseString = execute.body()?.use { it.string() }
                if (execute.code() == 200 && responseString?.startsWith("{") == true) {
                    responseString
                } else {
                    null
                }
            } catch (e: Exception) {
                null
            }
            if (maybeResult != null) {
                return maybeResult
            }
        }
        return null // no result after 5 attempts
    }

    private fun maybeUpdateNodeList() {
        if (bootNodes.size == nodes.size) {
            val newNodeURLs = requestWithRepeat("""{"jsonrpc":"2.0","method":"in3_nodeList","params":[],"id":1}""")?.let { json ->
                in3nodeListResponseAdapter.fromJson(json)?.result?.nodes
            }?.map { it.url }

            if (newNodeURLs != null) {
                nodes.addAll(newNodeURLs)
            }
        }
    }

    private fun buildRequest(body: RequestBody) = Request.Builder().url(nodes.random())
            .method("POST", body)
            .build()

    private fun buildRequest(payload: String) = buildRequest(RequestBody.create(JSONMediaType, payload))
}