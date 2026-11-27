package com.anotherzone.azcinema

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.widget.Toast

class UpdateStatusReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)
        val message = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE) ?: ""
        val text = when (status) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> "Confirm the AZ CINEMA update on your TV."
            PackageInstaller.STATUS_SUCCESS -> "AZ CINEMA updated successfully."
            else -> "Update installation did not complete: $message"
        }
        Toast.makeText(context, text, Toast.LENGTH_LONG).show()
        if (status == PackageInstaller.STATUS_PENDING_USER_ACTION) {
            val confirmation = intent.getParcelableExtra(Intent.EXTRA_INTENT) as? Intent
            confirmation?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            if (confirmation != null) context.startActivity(confirmation)
        }
    }
}
