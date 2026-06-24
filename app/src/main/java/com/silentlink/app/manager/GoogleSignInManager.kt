package com.silentlink.app.manager

import android.content.Context
import android.content.Intent
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException

class GoogleSignInManager(private val context: Context) {

    private val webClientId: String by lazy {
        try {
            val id = context.resources.getIdentifier("default_web_client_id", "string", context.packageName)
            if (id != 0) context.getString(id) else ""
        } catch (_: Exception) { "" }
    }

    private val client by lazy {
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(webClientId)
            .requestEmail()
            .build()
        GoogleSignIn.getClient(context, gso)
    }

    fun getSignInIntent(): Intent = client.signInIntent

    fun extractIdToken(data: Intent?): String? = try {
        GoogleSignIn.getSignedInAccountFromIntent(data)
            .getResult(ApiException::class.java)?.idToken
    } catch (_: Exception) { null }

    fun getSignedInEmail(): String? =
        GoogleSignIn.getLastSignedInAccount(context)?.email

    fun signOut() {
        client.signOut()
    }
}
