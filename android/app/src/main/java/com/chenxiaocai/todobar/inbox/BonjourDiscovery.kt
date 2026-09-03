package com.chenxiaocai.todobar.inbox

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.net.wifi.WifiManager
import android.util.Log
import java.net.InetAddress
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

data class ServiceEndpoint(val host: InetAddress, val port: Int)

class BonjourDiscovery(private val context: Context) {
    fun find(timeoutSeconds: Long = 5): ServiceEndpoint? {
        val manager = context.getSystemService(NsdManager::class.java)
        val wifi = context.applicationContext.getSystemService(WifiManager::class.java)
        val lock = wifi.createMulticastLock("todobar-sync-discovery").apply { setReferenceCounted(false); acquire() }
        val latch = CountDownLatch(1)
        val resolving = AtomicBoolean(false)
        var endpoint: ServiceEndpoint? = null
        lateinit var listener: NsdManager.DiscoveryListener
        listener = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(type: String) = Unit
            override fun onDiscoveryStopped(type: String) = Unit
            override fun onStartDiscoveryFailed(type: String, code: Int) { Log.e("ToDoBarInbox", "Bonjour start failed code=$code"); latch.countDown() }
            override fun onStopDiscoveryFailed(type: String, code: Int) { Log.e("ToDoBarInbox", "Bonjour stop failed code=$code") }
            override fun onServiceLost(service: NsdServiceInfo) = Unit
            override fun onServiceFound(service: NsdServiceInfo) {
                if (!service.serviceType.contains("_todobar-sync._tcp") || !resolving.compareAndSet(false, true)) return
                manager.resolveService(service, object : NsdManager.ResolveListener {
                    override fun onResolveFailed(info: NsdServiceInfo, code: Int) { resolving.set(false); Log.e("ToDoBarInbox", "Bonjour resolve failed code=$code") }
                    override fun onServiceResolved(info: NsdServiceInfo) { endpoint = ServiceEndpoint(info.host, info.port); latch.countDown() }
                })
            }
        }
        return try {
            manager.discoverServices("_todobar-sync._tcp.", NsdManager.PROTOCOL_DNS_SD, listener)
            latch.await(timeoutSeconds, TimeUnit.SECONDS)
            endpoint
        } finally {
            runCatching { manager.stopServiceDiscovery(listener) }
            if (lock.isHeld) lock.release()
            Log.i("ToDoBarInbox", "Bonjour discovery complete found=${endpoint != null} timeoutSeconds=$timeoutSeconds")
        }
    }
}
