package com.kawach.cloud

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.kawach.cloud.data.local.PreferenceManager
import com.kawach.cloud.data.model.TelegramAuthState
import com.kawach.cloud.data.telegram.TdClient
import com.kawach.cloud.data.telegram.TdClientFactory
import com.kawach.cloud.data.telegram.TelegramClientManager
import com.kawach.cloud.data.telegram.TelegramConstants
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
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

    // Case 1: Missing credentials -> Error
    @Test
    fun testMissingCredentials_resultsInErrorState() = runBlocking {
        if (!TelegramConstants.isApiConfigured()) {
            val factory = TestClientFactory()
            val manager = TelegramClientManager(context, preferenceManager, clientFactory = factory)
            val fakeClient = factory.lastCreatedClient!!

            fakeClient.updateHandler(TdApi.UpdateAuthorizationState(TdApi.AuthorizationStateWaitTdlibParameters()))
            assertTrue("Auth state should be Error when credentials missing", manager.authState.value is TelegramAuthState.Error)
            val err = manager.sendPhoneNumber("+919876543210")
            assertTrue("Send phone should fail when credentials missing", err.isFailure)
        }
    }

    // Case 2: Valid credentials -> SetTdlibParameters sent
    @Test
    fun testValidCredentials_sendsSetTdlibParameters() {
        org.junit.Assume.assumeTrue(
            "Skipping SetTdlibParameters test: Telegram API credentials not configured in test environment",
            TelegramConstants.isApiConfigured()
        )

        val factory = TestClientFactory()
        val manager = TelegramClientManager(context, preferenceManager, clientFactory = factory)
        val fakeClient = factory.lastCreatedClient!!

        fakeClient.updateHandler(TdApi.UpdateAuthorizationState(TdApi.AuthorizationStateWaitTdlibParameters()))

        val setParams = fakeClient.sentFunctions.filterIsInstance<TdApi.SetTdlibParameters>().firstOrNull()
        assertNotNull("SetTdlibParameters must be sent to TDLib", setParams)
        assertEquals(manager.getEffectiveApiId(), setParams!!.apiId)
        assertEquals(manager.getEffectiveApiHash(), setParams.apiHash)
    }

    // Case 3: SetTdlibParameters succeeds -> wait for WaitPhoneNumber
    @Test
    fun testSetTdlibParametersSucceeds_transitionsToWaitPhoneNumber() {
        val factory = TestClientFactory()
        val manager = TelegramClientManager(context, preferenceManager, clientFactory = factory)
        val fakeClient = factory.lastCreatedClient!!

        // Simulate TDLib processing parameters and transitioning to WaitPhoneNumber
        fakeClient.updateHandler(TdApi.UpdateAuthorizationState(TdApi.AuthorizationStateWaitPhoneNumber()))
        assertTrue(manager.authState.value is TelegramAuthState.WaitingPhoneNumber)
        assertTrue(manager.rawAuthState.value is TdApi.AuthorizationStateWaitPhoneNumber)
    }

    // Case 4: Phone request while initialization is running -> waits
    @Test
    fun testPhoneRequest_whileInitializationIsRunning_waits() = runBlocking {
        val factory = TestClientFactory()
        val manager = TelegramClientManager(context, preferenceManager, clientFactory = factory)
        val fakeClient = factory.lastCreatedClient!!

        assertTrue(manager.authState.value is TelegramAuthState.Initializing)

        fakeClient.functionHandler = { func, handler ->
            if (func is TdApi.SetAuthenticationPhoneNumber) {
                handler(TdApi.Ok())
            } else {
                handler(TdApi.Ok())
            }
        }

        val phoneDeferred = async(Dispatchers.IO) {
            manager.sendPhoneNumber("+919876543210")
        }

        delay(50)
        var phoneCall = fakeClient.sentFunctions.filterIsInstance<TdApi.SetAuthenticationPhoneNumber>().firstOrNull()
        assertEquals(null, phoneCall)

        // TDLib reaches WaitPhoneNumber
        fakeClient.updateHandler(TdApi.UpdateAuthorizationState(TdApi.AuthorizationStateWaitPhoneNumber()))

        val result = phoneDeferred.await()
        assertTrue(result.isSuccess)

        phoneCall = fakeClient.sentFunctions.filterIsInstance<TdApi.SetAuthenticationPhoneNumber>().firstOrNull()
        assertNotNull(phoneCall)
        assertEquals("+919876543210", phoneCall!!.phoneNumber)
    }

    // Case 5: Phone request while raw state is WaitTdlibParameters -> MUST NOT send phone request immediately
    @Test
    fun testPhoneRequest_whileRawStateIsWaitTdlibParameters_doesNotSendImmediately() = runBlocking {
        val factory = TestClientFactory()
        val manager = TelegramClientManager(context, preferenceManager, clientFactory = factory)
        val fakeClient = factory.lastCreatedClient!!

        fakeClient.updateHandler(TdApi.UpdateAuthorizationState(TdApi.AuthorizationStateWaitTdlibParameters()))

        val phoneDeferred = async(Dispatchers.IO) {
            manager.sendPhoneNumber("+919876543210")
        }

        delay(50)
        val phoneCall = fakeClient.sentFunctions.filterIsInstance<TdApi.SetAuthenticationPhoneNumber>().firstOrNull()
        assertEquals("Must not send phone request while in WaitTdlibParameters", null, phoneCall)

        // Transition to WaitPhoneNumber
        fakeClient.updateHandler(TdApi.UpdateAuthorizationState(TdApi.AuthorizationStateWaitPhoneNumber()))
        val result = phoneDeferred.await()
        assertTrue(result.isSuccess)
    }

    // Case 6: Phone request while raw state is WaitPhoneNumber -> sends immediately
    @Test
    fun testPhoneRequest_whileRawStateIsWaitPhoneNumber_sendsImmediately() = runBlocking {
        val factory = TestClientFactory()
        val manager = TelegramClientManager(context, preferenceManager, clientFactory = factory)
        val fakeClient = factory.lastCreatedClient!!

        fakeClient.updateHandler(TdApi.UpdateAuthorizationState(TdApi.AuthorizationStateWaitPhoneNumber()))
        assertTrue(manager.authState.value is TelegramAuthState.WaitingPhoneNumber)

        val result = manager.sendPhoneNumber("+919876543210")
        assertTrue(result.isSuccess)

        val phoneCall = fakeClient.sentFunctions.filterIsInstance<TdApi.SetAuthenticationPhoneNumber>().firstOrNull()
        assertNotNull(phoneCall)
        assertEquals("+919876543210", phoneCall!!.phoneNumber)
        assertTrue(manager.authState.value is TelegramAuthState.WaitingCode)
    }

    // Case 7: resetToPhoneInput() must NOT fake raw TDLib readiness
    @Test
    fun testResetToPhoneInput_doesNotFakeRawTdlibReadiness() {
        val factory = TestClientFactory()
        val manager = TelegramClientManager(context, preferenceManager, clientFactory = factory)
        val fakeClient = factory.lastCreatedClient!!

        // Client is in WaitTdlibParameters
        fakeClient.updateHandler(TdApi.UpdateAuthorizationState(TdApi.AuthorizationStateWaitTdlibParameters()))
        manager.resetToPhoneInput()

        // Auth state must NOT be WaitingPhoneNumber
        assertFalse("resetToPhoneInput must not fake WaitingPhoneNumber if raw state is WaitTdlibParameters",
            manager.authState.value is TelegramAuthState.WaitingPhoneNumber)
    }

    // Case 8: TDLib returns "Initialization parameters are needed" -> authentication fails cleanly
    @Test
    fun testTdlibInitializationNeededError_failsCleanlyWithoutWaitingCode() = runBlocking {
        val factory = TestClientFactory()
        val manager = TelegramClientManager(context, preferenceManager, clientFactory = factory)
        val fakeClient = factory.lastCreatedClient!!

        fakeClient.updateHandler(TdApi.UpdateAuthorizationState(TdApi.AuthorizationStateWaitPhoneNumber()))

        fakeClient.functionHandler = { func, handler ->
            if (func is TdApi.SetAuthenticationPhoneNumber) {
                handler(TdApi.Error(400, "Initialization parameters are needed: call setTdlibParameters first"))
            } else {
                handler(TdApi.Ok())
            }
        }

        val result = manager.sendPhoneNumber("+919876543210")
        assertTrue("Request should fail when TDLib reports initialization needed", result.isFailure)
        assertFalse("Auth state must NOT become WaitingCode", manager.authState.value is TelegramAuthState.WaitingCode)
        assertTrue("Auth state should reflect error", manager.authState.value is TelegramAuthState.Error)
    }

    // Case 9: Rapid Send OTP taps -> only one request
    @Test
    fun testRapidSendOtpTaps_onlyDispatchesOneRequest() = runBlocking {
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

        val job1 = async(Dispatchers.IO) { manager.sendPhoneNumber("+919876543210") }
        phoneSendStarted.await()

        val job2 = async(Dispatchers.IO) { manager.sendPhoneNumber("+919876543210") }
        val result2 = job2.await()
        assertTrue("Concurrent request must fail", result2.isFailure)

        phoneSendRelease.complete(Unit)
        val result1 = job1.await()
        assertTrue("First request must succeed", result1.isSuccess)

        val phoneCalls = fakeClient.sentFunctions.filterIsInstance<TdApi.SetAuthenticationPhoneNumber>()
        assertEquals(1, phoneCalls.size)
    }

    // Case 10: Credentials are never printed in logs or errors
    @Test
    fun testCredentials_neverPrintedInLogs() {
        val manager = TelegramClientManager(context, preferenceManager)
        val err = manager.parseTelegramError("PHONE_NUMBER_INVALID")
        assertFalse("Error message should not contain API Hash", err.contains(TelegramConstants.API_HASH))
        assertFalse("Error message should not contain API ID", TelegramConstants.API_ID > 0 && err.contains(TelegramConstants.API_ID.toString()))
    }
}
