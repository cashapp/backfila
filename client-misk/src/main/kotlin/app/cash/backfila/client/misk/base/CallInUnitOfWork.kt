package app.cash.backfila.client.misk.base

import misk.hibernate.Session

@Deprecated("")
interface CallInUnitOfWork<T> {
  fun call(session: Session): T
}
