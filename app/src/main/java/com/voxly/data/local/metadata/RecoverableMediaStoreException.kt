package com.voxly.data.local.metadata

import android.content.IntentSender

class RecoverableMediaStoreException(
    message: String,
    val intentSender: IntentSender,
    cause: Throwable? = null
) : IllegalStateException(message, cause)
