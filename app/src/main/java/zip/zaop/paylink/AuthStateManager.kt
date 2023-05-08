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
import android.content.SharedPreferences
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
import zip.zaop.paylink.database.LinkablePlatform
import zip.zaop.paylink.repository.LidlRepository
import org.json.JSONException
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.locks.ReentrantLock

/**
 * An example persistence mechanism for an [AuthState] instance.
 * This stores the instance in a shared preferences file, and provides thread-safe access and
 * mutation.
 */
class AuthStateManager private constructor(context: Context, lidlRepository: LidlRepository) {
    private val mPrefs: SharedPreferences
    private val mPrefsLock: ReentrantLock
    private val mCurrentAuthState: AtomicReference<AuthState>
    private val lidlRepository = lidlRepository

    init {
        mPrefs = context.getSharedPreferences(STORE_NAME, Context.MODE_PRIVATE)
        mPrefsLock = ReentrantLock()
        mCurrentAuthState = AtomicReference()
    }

    @get:AnyThread
    val current: AuthState
        get() {
            if (mCurrentAuthState.get() != null) {
                return mCurrentAuthState.get()
            }
            Log.i("AUTH", "Hiii")
            val state = runBlocking(Dispatchers.IO) {
                Log.i("AUTH", "im gaming")
                val state = readState()
                Log.i("AUTH", "haha yes")
                state
            }
            return if (mCurrentAuthState.compareAndSet(null, state)) {
                state
            } else {
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

    val auths: Flow<Map<LinkablePlatform, String>> = lidlRepository.auth

    @AnyThread
    private suspend fun readState(): AuthState {
        var authStates: Map<LinkablePlatform, String> = emptyMap()

        auths.take(1).collect { authMap ->
            authStates = authMap
        }

        if (authStates.containsKey(LinkablePlatform.LIDL)) {
            val currentState = authStates[LinkablePlatform.LIDL]!!
            return try {
                AuthState.jsonDeserialize(currentState)
            } catch (ex: JSONException) {
                Log.w(TAG, "Failed to deserialize stored auth state - discarding")
                AuthState()
            }
        }
        else {
            return AuthState()
        }
    }

    @AnyThread
    private suspend fun writeState(state: AuthState?) {
        if (state == null) return
        val str = state.jsonSerializeString()
        Log.i("JAVA", str)

        lidlRepository.updateAuthState(LinkablePlatform.LIDL, str)
    }

    companion object {
        private const val TAG = "AuthStateManager"
        private const val STORE_NAME = "AuthState"
        private const val KEY_STATE = "state"
        @Volatile private var INSTANCE: AuthStateManager? = null

        fun getInstance(context: Context, repo: LidlRepository): AuthStateManager =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: AuthStateManager(context, repo).also { INSTANCE = it }
            }

    }
}