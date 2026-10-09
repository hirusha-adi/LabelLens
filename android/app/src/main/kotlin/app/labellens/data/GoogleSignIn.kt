package app.labellens.data

import android.content.Context
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import app.labellens.BuildConfig
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenParsingException
import java.security.MessageDigest
import java.security.SecureRandom

sealed interface GoogleResult {
    /** [nonce] is the unhashed value. The server needs it to check the token was made for this request. */
    data class Token(val idToken: String, val nonce: String) : GoogleResult
    data object Cancelled : GoogleResult
    data class Failed(val message: String) : GoogleResult
}

/** Shows Google's account picker and returns an ID token for the account chosen. */
object GoogleSignIn {
    /** @param context must be the Activity, so the picker has a window to show in. */
    suspend fun request(context: Context): GoogleResult {
        // Google puts the hash of the nonce in the token; the server is sent the original.
        val nonce = ByteArray(32).also { SecureRandom().nextBytes(it) }.joinToString("") { "%02x".format(it) }
        val hashed = MessageDigest.getInstance("SHA-256").digest(nonce.toByteArray()).joinToString("") { "%02x".format(it) }
        val option = GetSignInWithGoogleOption.Builder(BuildConfig.GOOGLE_WEB_CLIENT_ID).setNonce(hashed).build()
        val request = GetCredentialRequest.Builder().addCredentialOption(option).build()
        return try {
            val credential = CredentialManager.create(context).getCredential(context, request).credential
            if (credential is CustomCredential && credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                GoogleResult.Token(GoogleIdTokenCredential.createFrom(credential.data).idToken, nonce)
            } else {
                GoogleResult.Failed("Google sign-in didn't complete. Try again.")
            }
        } catch (e: GetCredentialCancellationException) {
            GoogleResult.Cancelled
        } catch (e: NoCredentialException) {
            GoogleResult.Failed("No Google account found on this phone. Add one in Settings, or use your email and password.")
        } catch (e: GetCredentialException) {
            GoogleResult.Failed("Google sign-in isn't available right now. Use your email and password.")
        } catch (e: GoogleIdTokenParsingException) {
            GoogleResult.Failed("Google sign-in didn't complete. Try again.")
        }
    }
}
