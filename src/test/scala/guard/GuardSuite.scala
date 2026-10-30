package guard

import scala.compiletime.testing.typeCheckErrors

enum Error:
  case Unknown, Minor, Foreign, Banned

case class User(age: Int, country: String, banned: Boolean)
case class Account(user: User)

object Fixtures:
  def register(user: User): Either[Error, Account] =
    guard[Left[Error, Nothing]](Left(Error.Unknown)) {
      ensure(user.age >= 18) orReturn Left(Error.Minor)         // 成人であること
      ensure(user.country == "JP") orReturn Left(Error.Foreign) // 国内ユーザーであること
      ensure(!user.banned)                                      // 停止中でないこと
    }
    Right(Account(user))

  // RR を default から推論させる
  def inferredNone(x: Int): Option[Int] =
    guard(None) {
      ensure(x > 0)
      ensure(x < 100)
    }
    Some(x)

  def inferredLeft(x: Int): Either[Error, Int] =
    guard(Left(Error.Unknown)) {
      ensure(x >= 18) orReturn Left(Error.Minor)
      ensure(x < 100)
    }
    Right(x)

  // 返り値の型を省略しても、推論された型に収まれば動く
  def omittedReturnType(x: Int) =
    guard[Option[Int]](None) { ensure(x > 0) }
    Option(x)

class GuardSuite extends munit.FunSuite:
  import Fixtures.*

  test("すべての条件を満たすと本処理に進む") {
    assertEquals(register(User(20, "JP", false)), Right(Account(User(20, "JP", false))))
  }

  test("orReturn を書いた条件は、その値で抜ける") {
    assertEquals(register(User(17, "JP", false)), Left(Error.Minor))
    assertEquals(register(User(20, "US", false)), Left(Error.Foreign))
  }

  test("orReturn を省略した条件は、default で抜ける") {
    assertEquals(register(User(20, "JP", true)), Left(Error.Unknown))
  }

  test("先に書いた条件から順に評価する") {
    assertEquals(register(User(17, "US", true)), Left(Error.Minor))
  }

  test("RR を default から推論できる") {
    assertEquals(inferredNone(1), Some(1))
    assertEquals(inferredNone(-1), None)
    assertEquals(inferredNone(200), None)
    assertEquals(inferredLeft(20), Right(20))
    assertEquals(inferredLeft(10), Left(Error.Minor))
    assertEquals(inferredLeft(200), Left(Error.Unknown))
  }

  test("返り値の型を省略したメソッドでも、型が収まれば動く") {
    assertEquals(omittedReturnType(1), Some(1))
    assertEquals(omittedReturnType(-1), None)
  }

  private def errorsOf(errors: List[scala.compiletime.testing.Error]): List[String] =
    errors.map(_.message)

  test("RR が返り値の型に収まらないとコンパイルエラー") {
    val errors = errorsOf(typeCheckErrors("""
      def f(x: Int): Option[Int] =
        guard[Either[String, Nothing]](Left("x")) { ensure(x > 0) }
        Some(x)
    """))
    assert(errors.exists(_.contains("返り値の型")), errors)
  }

  test("orReturn の値が RR に収まらないとコンパイルエラー") {
    val errors = errorsOf(typeCheckErrors("""
      def f(x: Int): Either[String, Int] =
        guard[Left[String, Nothing]](Left("x")) {
          ensure(x > 0) orReturn Right(0)
        }
        Right(x)
    """))
    assert(errors.exists(_.contains("収まらない")), errors)
  }

  test("RR を推論させると、default 以外の型では抜けられない") {
    val errors = errorsOf(typeCheckErrors("""
      def f(x: Int): Option[Int] =
        guard(None) { ensure(x > 0) orReturn Some(0) }
        Some(x)
    """))
    assert(errors.exists(_.contains("収まらない")), errors)
  }

  test("匿名関数の中では使えない") {
    val errors = errorsOf(typeCheckErrors("""
      def f(xs: List[Int]): Option[Int] =
        xs.foreach { x =>
          guard[Option[Int]](None) { ensure(x > 0) }
        }
        Some(1)
    """))
    assert(errors.exists(_.contains("匿名関数")), errors)
  }

  test("guard の中には ensure 以外を書けない") {
    val errors = errorsOf(typeCheckErrors("""
      def f(x: Int): Option[Int] =
        guard(None) { println(x) }
        Some(x)
    """))
    assert(errors.exists(_.contains("ensure だけ")), errors)
  }
