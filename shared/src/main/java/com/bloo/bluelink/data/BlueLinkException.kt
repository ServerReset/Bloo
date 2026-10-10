package com.bloo.bluelink.data

/**
 * The one exception type every [BlueLinkApi] public method can throw (see [BlueLinkApi.execute]).
 * [code] carries the HTTP status when the failure came from a server response (null for a pure
 * network/parse failure), which callers use to distinguish e.g. an expired-session 401 from a
 * generic error.
 */
class BlueLinkException(
    message: String,
    cause: Throwable? = null,
    val code: Int? = null,
) : Exception(message, cause)
