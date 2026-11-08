package com.example.data.remote.trakt

internal class TraktIncompleteSnapshotException : java.io.IOException("Trakt returned incomplete episode history. Your saved progress is retained; retry sync.")

/** Capacity, permissions and invalid requests require user action, rather than endless background retries. */
internal object TraktFailurePolicy {
    fun retryable(failure: Throwable?) = when (failure) {
        is retrofit2.HttpException -> failure.code() in listOf(408, 425, 429) || failure.code() >= 500
        is java.io.IOException -> true
        else -> false
    }
    fun message(failure: Throwable) = when ((failure as? retrofit2.HttpException)?.code()) {
        420 -> "Your Trakt account has reached a capacity limit. Free space or check your account plan, then retry. Your changes are still saved locally."
        401 -> "Trakt authorization has expired. Reconnect your account to continue syncing."
        403 -> "Trakt did not allow this request. Check your account permissions, then retry."
        429 -> "Trakt is limiting requests. Your changes are saved and sync will retry later."
        400, 404, 409, 422 -> "Trakt could not accept this request. Your changes are saved; check the title or account configuration before retrying."
        else -> if (failure is TraktIncompleteSnapshotException) failure.message!!
            else if (failure is java.io.IOException) "Couldn’t reach Trakt. Your changes are saved and sync will retry when the connection returns."
            else failure.localizedMessage ?: "Sync could not finish. Your local data is saved."
    }
}
