package app.cash.backfila.client.misk

import java.time.Instant
import javax.persistence.Column
import javax.persistence.Entity
import javax.persistence.GeneratedValue
import javax.persistence.Table
import misk.hibernate.Constraint
import misk.hibernate.DbUnsharded
import misk.hibernate.Id
import misk.hibernate.Operator
import misk.hibernate.Query
import org.hibernate.annotations.Where

@Entity
@Table(name = "coupons")
@Where(clause = "expired_at IS NULL")
class DbActiveCoupon() : DbUnsharded<DbActiveCoupon> {
  @javax.persistence.Id
  @GeneratedValue
  override lateinit var id: Id<DbActiveCoupon>

  @Column
  lateinit var expired_at: Instant

  fun coupon() = Coupon(id)
}

interface CouponQuery : Query<DbActiveCoupon> {
  @Constraint(path = "id", operator = Operator.EQ)
  fun id(id: Id<DbMenu>): MenuQuery
}

data class Coupon(
  val id: Id<DbActiveCoupon>,
)
