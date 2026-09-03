package com.chenxiaocai.todobar.inbox

import android.content.Context
import android.net.NetworkRequest
import android.net.wifi.WifiNetworkSpecifier
import android.util.Log
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class SyncWorker(context: Context, parameters: WorkerParameters) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val application = applicationContext as InboxApplication
        val state = application.secureStore.load() ?: return@withContext Result.success()
        val items = application.database.pending()
        if (items.isEmpty()) return@withContext Result.success()
        try {
            val endpoint = BonjourDiscovery(applicationContext).find() ?: error("ToDoBar Sync service was not found")
            val acknowledged = SyncClient(endpoint, network).sync(state, items)
            application.database.acknowledge(acknowledged)
            Log.i("ToDoBarInbox", "One-shot sync complete pendingIDs=${items.map { it.id }} acknowledgedIDs=$acknowledged")
            Result.success()
        } catch (error: Exception) {
            Log.e("ToDoBarInbox", "One-shot sync ended without retry pendingIDs=${items.map { it.id }} error=${error.stackTraceToString()}")
            Result.failure()
        }
    }
}

object SyncScheduler {
    private const val WORK_NAME = "home-wifi-single-sync"
    fun enqueue(context: Context) {
        val state = (context.applicationContext as InboxApplication).secureStore.load() ?: return
        val specifier = WifiNetworkSpecifier.Builder().setSsid(state.ssid).build()
        val request = NetworkRequest.Builder().addTransportType(android.net.NetworkCapabilities.TRANSPORT_WIFI).setNetworkSpecifier(specifier).build()
        val constraints = Constraints.Builder().setRequiredNetworkRequest(request, NetworkType.UNMETERED).build()
        val work = OneTimeWorkRequestBuilder<SyncWorker>().setConstraints(constraints).build()
        WorkManager.getInstance(context).enqueueUniqueWork(WORK_NAME, ExistingWorkPolicy.REPLACE, work)
        Log.i("ToDoBarInbox", "One-shot sync enqueued workID=${work.id} ssid=${state.ssid}")
    }
}
