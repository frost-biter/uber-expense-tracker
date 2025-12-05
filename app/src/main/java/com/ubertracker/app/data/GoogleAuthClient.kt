package com.ubertracker.app.data

import android.content.Context
import android.content.Intent
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.Scope
import com.google.api.services.gmail.GmailScopes

class GoogleAuthClient(private val context: Context) {

    // Define the scope: We need read-only access to Gmail
    private val gmailScope = Scope(GmailScopes.GMAIL_READONLY)

    fun getSignInClient(): GoogleSignInClient {
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .requestScopes(gmailScope) // Request permission to read emails
            // .requestIdToken("YOUR_CLIENT_ID_HERE") // Usually needed for backend, but for local API calls, standard auth works
            .build()

        return GoogleSignIn.getClient(context, gso)
    }

    // Check if user is already signed in
    fun isSignedIn(): Boolean {
        val account = GoogleSignIn.getLastSignedInAccount(context)
        return account != null && account.grantedScopes.contains(gmailScope)
    }
}