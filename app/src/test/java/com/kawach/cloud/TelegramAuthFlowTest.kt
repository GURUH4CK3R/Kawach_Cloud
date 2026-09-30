package com.kawach.cloud

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.kawach.cloud.data.local.PreferenceManager
import com.kawach.cloud.data.model.TelegramAuthState
import com.kawach.cloud.data.telegram.TdClient
import com.kawach.cloud.data.telegram.TdClientFactory
import com.kawach.cloud.data.telegram.TelegramClientManager
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.drinkless.tdlib.TdApi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.concurrent.CopyOnWriteArrayList

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class TelegramAuthFlowTest {

    private lateinit var context: Context
    private lateinit var preferenceManager: PreferenceManager

    class FakeTdClient(
        val updateHandler: (TdApi.Object) -> Unit,
        val updateExceptionHandler: (Throwable) -> Unit,
        val defaultExceptionHandler: (Throwable) -> Unit
    ) : TdClient {
        val sentFunctions = CopyOnWriteArrayList<TdApi.Function<*>>()
        var functionHandler: ((TdApi.Function<*>, (TdApi.Object) -> Unit) -> Unit)? = null

        override fun send(function: TdApi.Function<*>, resultHandler: (TdApi.Object) -> Unit) {
            sentFunctions.add(function)
            val handler = functionHandler
            if (handler != null) {
                handler(function, resultHandler)
            } else {
                resultHandler(TdApi.Ok())
            }
        }
    }

    class TestClientFactory : TdClientFactory {
        var createCount = 0
        var lastCreatedClient: FakeTdClient? = null
        var shouldThrowOnCreate: Throwable? = null

        override fun create(
            updateHandler: (TdApi.Object) -> Unit,
            updateExceptionHandler: (Throwable) -> Unit,
            defaultExceptionHandler: (Throwable) -> Unit
        ): TdClient {
            shouldThrowOnCreate?.let { throw it }
            createCount++
            val c = FakeTdClient(updateHandler, updateExceptionHandler, defaultExceptionHandler)
            lastCreatedClient = c
            return c
        }
    }

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext<Context>()
        preferenceManager = PreferenceManager(context)
    }

    @Test
    fun testClientInitialization_initializesOnce() {
        val factory = TestClientFactory()
        val manager = TelegramClientManager(context, preferenceManager, clientFactory = factory)

        // Initialized once on start
        assertEquals(1, factory.createCount)
        assertTrue(manager.authState.value is TelegramAuthState.Initializing)

        // Calling initClient again must reuse existing client
        manager.initClient()
        manager.initClient()
        assertEquals(1, factory.createCount)
    }

    @Test
    fun testWaitTdlibParameters_sendsSetTdlibParameters() {
        val factory = TestClientFactory()
        val manager = TelegramClientManager(context, preferenceManager, clientFactory = factory)
        val fakeClient = factory.lastCreatedClient
        assertNotNull(fakeClient)

        // Simulate TDLib requesting initialization parameters
        fakeClient!!.updateHandler(TdApi.UpdateAuthorizationState(TdApi.AuthorizationStateWaitTdlibParameters()))

        // Verify SetTdlibParameters was sent
        val setParams = fakeClient.sentFunctions.filterIsInstance<TdApi.SetTdlibParameters>().firstOrNull()
        assertNotNull("SetTdlibParameters must be sent to TDLib", setParams)
        assertEquals(manager.getEffectiveApiId(), setParams!!.apiId)
        assertEquals(manager.getEffectiveApiHash(), setParams.apiHash)
        assertFalse("AuthState should not be in error", manager.authState.value is TelegramAuthState.Error)
    }

    @Test
    fun testWaitPhoneNumber_sendsPhoneImmediately() = runBlocking {
        val factory = TestClientFactory()
        val manager = TelegramClientManager(context, preferenceManager, clientFactory = factory)
        val fakeClient = factory.lastCreatedClient!!

        // Simulate TDLib reaching WaitPhoneNumber
        fakeClient.updateHandler(TdApi.UpdateAuthorizationState(TdApi.AuthorizationStateWaitPhoneNumber()))
        assertTrue(manager.authState.value is TelegramAuthState.WaitingPhoneNumber)

        // Respond OK to SetAuthenticationPhoneNumber
        fakeClient.functionHandler = { func, handler ->
            if (func is TdApi.SetAuthenticationPhoneNumber) {
                handler(TdApi.Ok())
            } else {
                handler(TdApi.Ok())
            }
        }

        val result = manager.sendPhoneNumber("+1234567890")
        assertTrue(result.isSuccess)

        val phoneCall = fakeClient.sentFunctions.filterIsInstance<TdApi.SetAuthenticationPhoneNumber>().firstOrNull()
        assertNotNull("SetAuthenticationPhoneNumber must be sent", phoneCall)
        assertEquals("+1234567890", phoneCall!!.phoneNumber)
        assertTrue(manager.authState.value is TelegramAuthState.WaitingCode)
    }

    @Test
    fun testPhoneRequest_whileInitializationIsRunning_waitsForWaitPhoneNumber() = runBlocking {
        val factory = TestClientFactory()
        val manager = TelegramClientManager(context, preferenceManager, clientFactory = factory)
        val fakeClient = factory.lastCreatedClient!!

        // Client is still in Initializing state
        assertTrue(manager.authState.value is TelegramAuthState.Initializing)

        fakeClient.functionHandler = { func, handler ->
            if (func is TdApi.SetAuthenticationPhoneNumber) {
                handler(TdApi.Ok())
            } else {
                handler(TdApi.Ok())
            }
        }

        // Launch sendPhoneNumber asynchronously while client is still initializing
        val phoneDeferred = async(Dispatchers.IO) {
            manager.sendPhoneNumber("+9876543210")
        }

        // Ensure SetAuthenticationPhoneNumber is not yet called before TDLib reaches WaitPhoneNumber
        delay(50)
        var phoneCall = fakeClient.sentFunctions.filterIsInstance<TdApi.SetAuthenticationPhoneNumber>().firstOrNull()
        assertEquals(null, phoneCall)

        // Now TDLib signals WaitTdlibParameters -> manager sends parameters
        fakeClient.updateHandler(TdApi.UpdateAuthorizationState(TdApi.AuthorizationStateWaitTdlibParameters()))

        // Then TDLib signals WaitPhoneNumber
        fakeClient.updateHandler(TdApi.UpdateAuthorizationState(TdApi.AuthorizationStateWaitPhoneNumber()))

        // Now the pending phone request must resume and succeed
        val result = phoneDeferred.await()
        assertTrue(result.isSuccess)

        phoneCall = fakeClient.sentFunctions.filterIsInstance<TdApi.SetAuthenticationPhoneNumber>().firstOrNull()
        assertNotNull("SetAuthenticationPhoneNumber must now be sent", phoneCall)
        assertEquals("+9876543210", phoneCall!!.phoneNumber)
    }

    @Test
    fun testInitializationFailure_returnsFriendlyError_doesNotSendPhone() = runBlocking {
        val factory = TestClientFactory()
        factory.shouldThrowOnCreate = RuntimeException("TDLib native library failed to load")

        val manager = TelegramClientManager(context, preferenceManager, clientFactory = factory)
        assertTrue(manager.authState.value is TelegramAuthState.Error)

        val result = manager.sendPhoneNumber("+1234567890")
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message?.contains("TDLib native library failed to load") == true)
    }

    @Test
    fun testRepeatedSendOtpProtection_preventsConcurrentRace() = runBlocking {
        val factory = TestClientFactory()
        val manager = TelegramClientManager(context, preferenceManager, clientFactory = factory)
        val fakeClient = factory.lastCreatedClient!!

        fakeClient.updateHandler(TdApi.UpdateAuthorizationState(TdApi.AuthorizationStateWaitPhoneNumber()))

        val phoneSendStarted = CompletableDeferred<Unit>()
        val phoneSendRelease = CompletableDeferred<Unit>()

        fakeClient.functionHandler = { func, handler ->
            if (func is TdApi.SetAuthenticationPhoneNumber) {
                runBlocking {
                    phoneSendStarted.complete(Unit)
                    phoneSendRelease.await()
                }
                handler(TdApi.Ok())
            } else {
                handler(TdApi.Ok())
            }
        }

        // First call starts and acquires the mutex
        val job1 = async(Dispatchers.IO) {
            manager.sendPhoneNumber("+1234567890")
        }

        // Wait until first call has entered send
        phoneSendStarted.await()

        // Second call while first call is in flight should fail immediately due to concurrency guard
        val job2 = async(Dispatchers.IO) {
            manager.sendPhoneNumber("+1234567890")
        }

        val result2 = job2.await()
        assertTrue("Second concurrent request must fail immediately", result2.isFailure)
        assertTrue(result2.exceptionOrNull()?.message?.contains("already in progress") == true)

        // Release first call
        phoneSendRelease.complete(Unit)
        val result1 = job1.await()
        assertTrue("First request must succeed", result1.isSuccess)

        // Exactly one SetAuthenticationPhoneNumber was sent
        val phoneCalls = fakeClient.sentFunctions.filterIsInstance<TdApi.SetAuthenticationPhoneNumber>()
        assertEquals(1, phoneCalls.size)
    }
}
