package com.mattzilla30.mbooru.data.settings

import android.content.Context
import android.content.SharedPreferences
import android.os.Handler
import android.os.Looper
import androidx.datastore.preferences.SharedPreferencesMigration
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStoreFile
import com.mattzilla30.mbooru.data.credentials.CredentialRepository
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import java.util.concurrent.CopyOnWriteArraySet

/** Transitional Android adapter; durable state belongs to DataStore, not an XML preferences file. */
class DataStorePreferences(context: Context, private val vault: CredentialRepository, private val scope: CoroutineScope) : SharedPreferences {
    private val dataStore = PreferenceDataStoreFactory.create(
        migrations = listOf(SharedPreferencesMigration(context, "${context.packageName}_preferences")),
        scope = scope, produceFile = { context.preferencesDataStoreFile("mbooru") })
    @Volatile private var ready = CompletableDeferred<Unit>()
    private val lock = Any()
    private val values = MutableStateFlow<Map<String, Any>>(emptyMap())
    val state = values.asStateFlow()
    private val failed = MutableStateFlow(false)
    val persistenceFailed = failed.asStateFlow()
    private val listeners = CopyOnWriteArraySet<SharedPreferences.OnSharedPreferenceChangeListener>()
    private val main = Handler(Looper.getMainLooper())
    private data class Write(val snapshot: Map<String, Any>, val completion: CompletableDeferred<Boolean>?)
    private val writes = Channel<Write>(Channel.UNLIMITED)
    private val secretKey = "sauce_nao_api_key"
    private var secretReference: String? = null
    private var secretValue: String? = null

    private var processor: Job? = null
    init { processor = launchProcessor() }
    @Synchronized fun retryInitialization() {
        if (processor?.isCompleted == true && ready.isCancelled) {
            ready = CompletableDeferred()
            processor = launchProcessor()
        }
    }
    fun retryPersistence() {
        if (ready.isCompleted && !ready.isCancelled) writes.trySend(Write(values.value, null))
        else retryInitialization()
    }
    private fun launchProcessor(): Job = scope.launch {
        try {
            val initial = dataStore.data.first().asMap().mapKeys { it.key.name }.toMutableMap()
            val stored = initial[secretKey] as? String
            if (!stored.isNullOrEmpty()) {
                secretReference = if (vault.isReference(stored)) stored else vault.store(stored)
                secretValue = if (vault.isReference(stored)) vault.read(stored) else stored
                initial[secretKey] = secretValue.orEmpty()
                if (stored != secretReference) dataStore.edit { it[stringPreferencesKey(secretKey)] = secretReference!! }
            }
            values.value = initial
            failed.value = false
            ready.complete(Unit)
            for (write in writes) {
                try {
                    val nextSecret = write.snapshot[secretKey] as? String
                    val nextReference = when {
                        nextSecret.isNullOrEmpty() -> null
                        nextSecret == secretValue -> secretReference
                        else -> vault.store(nextSecret)
                    }
                    dataStore.edit { target ->
                        target.clear()
                        write.snapshot.forEach { (key, value) ->
                            if (key != secretKey) target.putValue(key, value)
                        }
                        if (nextReference != null) target[stringPreferencesKey(secretKey)] = nextReference
                    }
                    if (secretReference != null && secretReference != nextReference) vault.remove(secretReference!!)
                    secretReference = nextReference
                    secretValue = nextSecret
                    failed.value = false
                    write.completion?.complete(true)
                } catch (cancelled: CancellationException) { throw cancelled }
                catch (_: Exception) { failed.value = true; write.completion?.complete(false) }
            }
        } catch (cancelled: CancellationException) { ready.cancel(cancelled); throw cancelled }
        catch (failure: Exception) { failed.value = true; ready.completeExceptionally(failure) }
    }

    suspend fun awaitReady() = ready.await()
    override fun getAll(): MutableMap<String, *> = values.value.toMutableMap()
    override fun contains(key: String?): Boolean = values.value.containsKey(key)
    override fun getString(key: String?, defValue: String?): String? = values.value[key] as? String ?: defValue
    @Suppress("UNCHECKED_CAST")
    override fun getStringSet(key: String?, defValues: MutableSet<String>?): MutableSet<String>? =
        (values.value[key] as? Set<String>)?.toMutableSet() ?: defValues?.toMutableSet()
    override fun getInt(key: String?, defValue: Int): Int = values.value[key] as? Int ?: defValue
    override fun getLong(key: String?, defValue: Long): Long = values.value[key] as? Long ?: defValue
    override fun getFloat(key: String?, defValue: Float): Float = values.value[key] as? Float ?: defValue
    override fun getBoolean(key: String?, defValue: Boolean): Boolean = values.value[key] as? Boolean ?: defValue
    override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) { if (listener != null) listeners.add(listener) }
    override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) { listeners.remove(listener) }
    override fun edit(): SharedPreferences.Editor = Editor()

    private inner class Editor : SharedPreferences.Editor {
        private val changed = mutableMapOf<String, Any?>()
        private var clear = false
        private fun put(key: String?, value: Any?): SharedPreferences.Editor { requireNotNull(key); changed[key] = value; return this }
        override fun putString(key: String?, value: String?) = put(key, value)
        override fun putStringSet(key: String?, values: MutableSet<String>?) = put(key, values?.toSet())
        override fun putInt(key: String?, value: Int) = put(key, value)
        override fun putLong(key: String?, value: Long) = put(key, value)
        override fun putFloat(key: String?, value: Float) = put(key, value)
        override fun putBoolean(key: String?, value: Boolean) = put(key, value)
        override fun remove(key: String?) = put(key, null)
        override fun clear(): SharedPreferences.Editor { clear = true; return this }
        private fun enqueue(completion: CompletableDeferred<Boolean>?) {
            check(ready.isCompleted && !ready.isCancelled) { "Preferences are not initialized" }
            val keys: Set<String>
            synchronized(lock) {
                val next = if (clear) mutableMapOf() else values.value.toMutableMap()
                changed.forEach { (key, value) -> if (value == null) next.remove(key) else next[key] = value }
                keys = (values.value.keys + next.keys).filter { values.value[it] != next[it] }.toSet()
                values.value = next.toMap()
                check(writes.trySend(Write(next.toMap(), completion)).isSuccess)
            }
            main.post { keys.forEach { key -> listeners.forEach { it.onSharedPreferenceChanged(this@DataStorePreferences, key) } } }
        }
        override fun apply() { enqueue(null) }
        override fun commit(): Boolean {
            check(Looper.myLooper() != Looper.getMainLooper()) { "Use asynchronous preference writes on the main thread" }
            val completion = CompletableDeferred<Boolean>()
            enqueue(completion)
            return runBlocking { completion.await() }
        }
    }
    private fun MutablePreferences.putValue(key: String, value: Any) {
        when (value) {
            is String -> this[stringPreferencesKey(key)] = value
            is Boolean -> this[booleanPreferencesKey(key)] = value
            is Int -> this[intPreferencesKey(key)] = value
            is Long -> this[longPreferencesKey(key)] = value
            is Float -> this[floatPreferencesKey(key)] = value
            is Set<*> -> this[stringSetPreferencesKey(key)] = value.filterIsInstance<String>().toSet()
            else -> error("Unsupported preference type")
        }
    }
}
