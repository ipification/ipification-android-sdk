package com.ipification.mobile.sdk.ip

/**
 * Thread-safe holder for a singleton that requires one argument during initialization.
 *
 * The argument passed to the first successful [getInstance] call creates the singleton.
 * Arguments supplied by later calls are ignored.
 */
open class SingletonHolder<out T : Any, in A>(instanceFactory: (A) -> T) {

    private var instanceFactory: ((A) -> T)? = instanceFactory

    @Volatile
    private var instance: T? = null

    /** Returns the existing singleton or creates it using [argument]. */
    @Suppress("ReturnCount")
    fun getInstance(argument: A): T {
        instance?.let { return it }

        return synchronized(this) {
            instance?.let { return@synchronized it }

            val factory = checkNotNull(instanceFactory) {
                "Singleton factory is unavailable before instance initialization"
            }
            factory(argument).also { createdInstance ->
                instance = createdInstance
                instanceFactory = null
            }
        }
    }
}
