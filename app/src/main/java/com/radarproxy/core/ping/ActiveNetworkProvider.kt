package com.radarproxy.core.ping

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import java.net.InetAddress
import java.net.Socket
import javax.net.SocketFactory

/** Uses the phone's currently validated Wi-Fi or mobile-data route for every probe. */
class ActiveNetworkProvider(context: Context) {
    private val connectivity = context.getSystemService(ConnectivityManager::class.java)

    fun current(): Network? {
        val network = connectivity.activeNetwork ?: return null
        val capabilities = connectivity.getNetworkCapabilities(network) ?: return null
        return network.takeIf {
            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
        }
    }

    fun socketFactory(network: Network?): SocketFactory = network?.socketFactory ?: SocketFactory.getDefault()

    fun open(network: Network?): Socket = socketFactory(network).createSocket()

    fun addresses(network: Network?, host: String): Array<InetAddress> =
        network?.getAllByName(host) ?: InetAddress.getAllByName(host)
}
