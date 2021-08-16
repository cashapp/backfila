package app.cash.backfila.client

class UnknownBackfillException(message: String = "", cause: Throwable? = null) :
  Exception(message, cause)
