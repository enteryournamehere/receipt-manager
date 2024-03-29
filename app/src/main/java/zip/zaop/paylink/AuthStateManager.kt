/*
 * Copyright 2017 The AppAuth for Android Authors. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the
 * License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
 * express or implied. See the License for the specific language governing permissions and
 * limitations under the License.
 */
package zip.zaop.paylink

import android.content.Context
import android.util.Log
import androidx.annotation.AnyThread
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.runBlocking
import net.openid.appauth.AuthState
import net.openid.appauth.AuthorizationException
import net.openid.appauth.AuthorizationResponse
import net.openid.appauth.RegistrationResponse
import net.openid.appauth.TokenResponse
import org.json.JSONException
import zip.zaop.paylink.database.LinkablePlatform
import zip.zaop.paylink.repository.ReceiptRepository
import java.util.concurrent.atomic.AtomicReference

/**
 * An example persistence mechanism for an [AuthState] instance.
 * This stores the instance in a shared preferences file, and provides thread-safe access and
 * mutation.
 */
class AuthStateManager private constructor(
    receiptRepository: ReceiptRepository,
    platform: LinkablePlatform
) {
    private val mCurrentAuthState: AtomicReference<AuthState> = AtomicReference()
    private val lidlRepository = receiptRepository
    private val mPlatform = platform

    @get:AnyThread
    val current: AuthState
        get() {
            if (mCurrentAuthState.get() != null) {
                Log.i(TAG, "Returning existing auth state.")
                return mCurrentAuthState.get()
            }
            Log.i(TAG, "Going to read state from DB.")
            val state = runBlocking(Dispatchers.IO) {
                val state = readState()
                Log.i(TAG, "Done reading.")
                state
            }
            return if (mCurrentAuthState.compareAndSet(null, state)) {
                Log.i(TAG, "First branch.")
                state
            } else {
                Log.i(TAG, "Second branch.")
                mCurrentAuthState.get()
            }
        }

    @AnyThread
    suspend fun replace(state: AuthState): AuthState {
        writeState(state)
        mCurrentAuthState.set(state)
        return state
    }

    @AnyThread
    suspend fun updateAfterAuthorization(
        response: AuthorizationResponse?,
        ex: AuthorizationException?
    ): AuthState {
        val current = current
        current.update(response, ex)
        return replace(current)
    }

    @AnyThread
    suspend fun updateAfterTokenResponse(
        response: TokenResponse?,
        ex: AuthorizationException?
    ): AuthState {
        val current = current
        current.update(response, ex)
        return replace(current)
    }

    @AnyThread
    suspend fun updateAfterRegistration(
        response: RegistrationResponse?,
        ex: AuthorizationException?
    ): AuthState {
        val current = current
        if (ex != null) {
            return current
        }
        current.update(response)
        return replace(current)
    }

    private val auths: Flow<Map<LinkablePlatform, String>> = receiptRepository.auth

    @AnyThread
    private suspend fun readState(): AuthState {
        Log.i(TAG, "readstate start")
        var authStates: Map<LinkablePlatform, String> = emptyMap()

        auths.take(1).collect { authMap ->
            authStates = authMap
        }
        Log.i(TAG, "readstate read")

        if (authStates.containsKey(mPlatform)) {
            val currentState = authStates[mPlatform]!!
            return try {
                val mep = AuthState.jsonDeserialize(currentState)
                Log.i(TAG, "Read auth state from database.")
                mep
            } catch (ex: JSONException) {
                Log.w(TAG, "Failed to deserialize stored auth state - discarding")
                AuthState()
            }
        }
        else {
            Log.w(TAG, "No saved auth state found.")
            return AuthState()
        }
    }

    @AnyThread
    private suspend fun writeState(state: AuthState?) {
        if (state == null) return
        val str = state.jsonSerializeString()
        Log.i(TAG, str)

        lidlRepository.updateAuthState(mPlatform, str)
    }

    companion object {
        private const val TAG = "AuthStateManager"
        @Volatile private var INSTANCE_MAP: MutableMap<LinkablePlatform, AuthStateManager> = mutableMapOf()

        fun getInstance(context: Context, repo: ReceiptRepository, platform: LinkablePlatform): AuthStateManager =
            INSTANCE_MAP[platform] ?: synchronized(this) {
                INSTANCE_MAP[platform] ?: AuthStateManager(repo, platform).also { INSTANCE_MAP[platform] = it }
            }
    }
}