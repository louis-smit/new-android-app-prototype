package no.solver.solverapp.features.auth.services

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import no.solver.solverapp.core.config.AuthEnvironment
import no.solver.solverapp.core.config.AuthProvider
import no.solver.solverapp.features.auth.models.AuthTokens
import no.solver.solverapp.features.auth.models.Session
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SessionManager @Inject constructor(
    private val dataStore: DataStore<Preferences>,
    private val json: Json
) {
    companion object {
        private val KEY_CURRENT_SESSION_ID = stringPreferencesKey("current_session_id")
        private val KEY_SESSIONS = stringPreferencesKey("sessions")
        private val KEY_AUTH_ENVIRONMENT = stringPreferencesKey("auth_environment")
    }

    val currentSessionFlow: Flow<Session?> = dataStore.data.map { prefs ->
        val sessionId = prefs[KEY_CURRENT_SESSION_ID] ?: return@map null
        val sessions = getSessions(prefs)
        sessions.find { it.id == sessionId }
    }

    val authEnvironmentFlow: Flow<AuthEnvironment> = dataStore.data.map { prefs ->
        val envName = prefs[KEY_AUTH_ENVIRONMENT] ?: AuthEnvironment.SOLVER.name
        AuthEnvironment.valueOf(envName)
    }

    suspend fun getCurrentSession(): Session? {
        return currentSessionFlow.first()
    }

    suspend fun getAuthEnvironment(): AuthEnvironment {
        return authEnvironmentFlow.first()
    }

    suspend fun setAuthEnvironment(environment: AuthEnvironment) {
        dataStore.edit { prefs ->
            prefs[KEY_AUTH_ENVIRONMENT] = environment.name
        }
    }

    suspend fun createSession(
        provider: AuthProvider,
        environment: AuthEnvironment,
        tokens: AuthTokens
    ): Session {
        val session = Session(
            id = UUID.randomUUID().toString(),
            provider = provider,
            environment = environment,
            tokens = tokens,
            isActive = true
        )

        dataStore.edit { prefs ->
            val sessions = getSessions(prefs).toMutableList()
            sessions.add(session)
            prefs[KEY_SESSIONS] = json.encodeToString(sessions.map { it.toSerializable() })
            prefs[KEY_CURRENT_SESSION_ID] = session.id
        }

        return session
    }

    suspend fun updateSession(session: Session) {
        dataStore.edit { prefs ->
            val sessions = getSessions(prefs).toMutableList()
            val index = sessions.indexOfFirst { it.id == session.id }
            if (index >= 0) {
                sessions[index] = session
                prefs[KEY_SESSIONS] = json.encodeToString(sessions.map { it.toSerializable() })
            }
        }
    }

    suspend fun removeSession(sessionId: String) {
        dataStore.edit { prefs ->
            val sessions = getSessions(prefs).toMutableList()
            sessions.removeAll { it.id == sessionId }
            prefs[KEY_SESSIONS] = json.encodeToString(sessions.map { it.toSerializable() })

            if (prefs[KEY_CURRENT_SESSION_ID] == sessionId) {
                prefs[KEY_CURRENT_SESSION_ID] = sessions.firstOrNull()?.id ?: ""
            }
        }
    }

    suspend fun switchToSession(sessionId: String) {
        dataStore.edit { prefs ->
            prefs[KEY_CURRENT_SESSION_ID] = sessionId
        }
    }

    suspend fun getAllSessions(): List<Session> {
        return dataStore.data.first().let { getSessions(it) }
    }

    suspend fun clearAllSessions() {
        dataStore.edit { prefs ->
            prefs.remove(KEY_SESSIONS)
            prefs.remove(KEY_CURRENT_SESSION_ID)
        }
    }

    private fun getSessions(prefs: Preferences): List<Session> {
        val sessionsJson = prefs[KEY_SESSIONS] ?: return emptyList()
        return try {
            json.decodeFromString<List<SerializableSession>>(sessionsJson)
                .map { it.toSession() }
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun Session.toSerializable() = SerializableSession(
        id = id,
        provider = provider.name,
        environment = environment.name,
        tokens = tokens,
        isActive = isActive
    )

    private fun SerializableSession.toSession() = Session(
        id = id,
        provider = AuthProvider.valueOf(provider),
        environment = AuthEnvironment.valueOf(environment),
        tokens = tokens,
        isActive = isActive
    )
}

@kotlinx.serialization.Serializable
private data class SerializableSession(
    val id: String,
    val provider: String,
    val environment: String,
    val tokens: AuthTokens,
    val isActive: Boolean
)
