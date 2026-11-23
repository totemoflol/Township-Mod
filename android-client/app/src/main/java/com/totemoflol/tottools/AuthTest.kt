package com.totemoflol.tottools

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

internal enum class TestAccess {
    FREE,
    PREMIUM,
    EXPIRED
}

internal data class TestAuthSession(
    val email: String,
    val access: TestAccess
) {
    val hasPremium: Boolean
        get() = access == TestAccess.PREMIUM
}

/**
 * Local-only authentication simulator for the auth-test-mode build.
 * This is intentionally not a production authentication or licensing system.
 */
internal object TestAuthStore {
    private const val PREFS_NAME = "tot_tools_test_auth"
    private const val EMAIL_KEY = "email"
    private const val ACCESS_KEY = "access"

    private val testAccounts = mapOf(
        "premium@tottools.test" to ("PremiumTest!2026" to TestAccess.PREMIUM),
        "free@tottools.test" to ("FreeTest!2026" to TestAccess.FREE),
        "expired@tottools.test" to ("ExpiredTest!2026" to TestAccess.EXPIRED)
    )

    fun signIn(context: Context, emailInput: String, password: String): TestAuthSession? {
        val email = emailInput.trim().lowercase()
        val account = testAccounts[email] ?: return null
        if (password != account.first) return null

        val session = TestAuthSession(email, account.second)
        preferences(context).edit()
            .putString(EMAIL_KEY, session.email)
            .putString(ACCESS_KEY, session.access.name)
            .apply()
        return session
    }

    fun restore(context: Context): TestAuthSession? {
        val prefs = preferences(context)
        val email = prefs.getString(EMAIL_KEY, null) ?: return null
        val accessName = prefs.getString(ACCESS_KEY, null) ?: return null
        val access = runCatching { TestAccess.valueOf(accessName) }.getOrNull() ?: return null
        if (testAccounts[email]?.second != access) return null
        return TestAuthSession(email, access)
    }

    fun signOut(context: Context) {
        preferences(context).edit().clear().apply()
    }

    private fun preferences(context: Context) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
}

@Composable
internal fun AuthTestScreen(onLogin: (String, String) -> Boolean) {
    var email by remember { mutableStateOf("premium@tottools.test") }
    var password by remember { mutableStateOf("PremiumTest!2026") }
    var error by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(22.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Card(
            modifier = Modifier.widthIn(max = 440.dp).fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        ) {
            Column(
                modifier = Modifier.padding(22.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    "ToT Tools",
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.ExtraBold
                )
                Text(
                    "AUTHENTICATION TEST",
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.6.sp,
                    fontSize = 11.sp
                )
                Text(
                    "Sign in with one of the temporary test accounts. No purchase is required.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium
                )
                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it; error = "" },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text("Email") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email)
                )
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it; error = "" },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text("Password") },
                    visualTransformation = PasswordVisualTransformation()
                )
                if (error.isNotBlank()) {
                    Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
                Button(
                    onClick = { if (!onLogin(email, password)) error = "Incorrect test email or password." },
                    enabled = email.isNotBlank() && password.isNotBlank(),
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Text("Sign in", fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.height(4.dp))
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Column(Modifier.padding(13.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("TEST ACCOUNTS", fontWeight = FontWeight.Bold, fontSize = 11.sp, color = MaterialTheme.colorScheme.secondary)
                        Text("Premium\npremium@tottools.test\nPremiumTest!2026", style = MaterialTheme.typography.bodySmall)
                        Text("Free\nfree@tottools.test\nFreeTest!2026", style = MaterialTheme.typography.bodySmall)
                        Text("Expired\nexpired@tottools.test\nExpiredTest!2026", style = MaterialTheme.typography.bodySmall)
                    }
                }
                Text(
                    "TEST BUILD ONLY · Local demo accounts are not secure and do not represent real purchases.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 10.sp,
                    lineHeight = 14.sp
                )
            }
        }
    }
}
