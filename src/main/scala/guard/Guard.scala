package guard

import scala.quoted.*

/** guard ブロックの外では使えない目印。マクロが展開時に置き換える。 */
final class Ensure private[guard] ():
  infix def orReturn[A](value: => A): Unit =
    throw IllegalStateException("orReturn は guard の中でのみ使える")

def ensure(cond: Boolean): Ensure =
  throw IllegalStateException("ensure は guard の中でのみ使える")

/** 条件を肯定形で列挙し、満たさなければ囲んでいるメソッドから抜ける。
  *
  * {{{
  * def register(user: User): Either[Error, Account] =
  *   guard[Left[Error, Nothing]](Left(Error.Unknown)) {
  *     ensure(user.age >= 18) orReturn Left(Error.Minor) // 成人であること
  *     ensure(!user.banned)                              // 停止中でないこと
  *   }
  *   Right(createAccount(user))
  * }}}
  *
  * @tparam RR
  *   脱出時の値の型。囲んでいるメソッドの返り値の型 `R` に対して `RR <: R` である必要がある
  * @param default
  *   `orReturn` を省略した `ensure` が偽のときに返す値
  */
inline def guard[RR](inline default: RR)(inline body: Unit): Unit =
  ${ guardImpl[RR]('default, 'body) }

private def enclosingMethod(using Quotes): quotes.reflect.Symbol =
  import quotes.reflect.*
  var s = Symbol.spliceOwner
  // val を飛ばして、名前のあるメソッドまでたどる。
  // 匿名関数を越えると非ローカル return になるので、いまは禁止する。
  while !s.isNoSymbol && !(s.isDefDef && !s.isAnonymousFunction) do
    if s.isAnonymousFunction then
      report.errorAndAbort("guard は匿名関数の中では使えない(メソッド全体から抜けてしまうため)")
    s = s.owner
  if s.isNoSymbol then report.errorAndAbort("guard はメソッドの中でのみ使える")
  s

private def resultType(using Quotes)(t: quotes.reflect.TypeRepr): quotes.reflect.TypeRepr =
  import quotes.reflect.*
  t match
    case MethodType(_, _, res) => resultType(res)
    case PolyType(_, _, res)   => resultType(res)
    case ByNameType(res)       => res
    case other                 => other

private def guardImpl[RR: Type](default: Expr[RR], body: Expr[Unit])(using Quotes): Expr[Unit] =
  import quotes.reflect.*
  val method = enclosingMethod
  val r      = resultType(method.termRef.widen)
  val rr     = TypeRepr.of[RR]

  // RR <: R の検査
  if !(rr <:< r) then
    report.errorAndAbort(
      s"脱出値の型 ${rr.show} が、${method.name} の返り値の型 ${r.show} に収まらない(返り値の型を省略している場合は、明示すると解決することがある)",
      default
    )

  def strip(t: Term): Term = t match
    case Inlined(_, Nil, e) => strip(e)
    case Typed(e, _)        => strip(e)
    case Block(Nil, e)      => strip(e)
    case e                  => e

  def isEnsure(fn: Term) = fn.symbol.name == "ensure" && fn.symbol.owner.fullName.startsWith("guard")

  // 一つの文を `if !cond then return value` に変換する
  def translate(stmt: Term): Term = strip(stmt) match
    // ensure(cond) orReturn value
    case Apply(TypeApply(Select(Apply(fn, List(cond)), "orReturn"), _), List(value)) if isEnsure(fn) =>
      val v = strip(value)
      if !(v.tpe <:< rr) then
        report.errorAndAbort(s"脱出値の型 ${v.tpe.widen.show} が ${rr.show} に収まらない", value.asExpr)
      emit(cond, v)
    // ensure(cond)
    case Apply(fn, List(cond)) if isEnsure(fn) =>
      emit(cond, default.asTerm)
    case other =>
      report.errorAndAbort("guard の中には ensure だけを書ける", other.asExpr)

  def emit(cond: Term, value: Term): Term =
    If(Select.unique(cond, "unary_!"), Return(value, method), Literal(UnitConstant()))

  // 入れ子のブロック(値の破棄で挿入されるものを含む)を平らにし、() を除く
  def flatten(t: Term): List[Term] = strip(t) match
    case Block(ss, last)         => ss.collect { case s: Term => s }.flatMap(flatten) ++ flatten(last)
    case Literal(UnitConstant()) => Nil
    case other                   => List(other)
  val stmts = flatten(body.asTerm)

  Block(stmts.map(translate), Literal(UnitConstant())).asExprOf[Unit]
