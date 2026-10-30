# scala-guard

条件を肯定形で一行ずつ列挙し、満たさなければ囲んでいるメソッドから抜けるための、Scala 3 のマクロです。

early return は「満たさない場合」を書く構文なので、要件を書くと条件が否定形になります。`assert` と同じ見た目で、失敗したら例外ではなく `return` する構文があれば、要件の文面と条件式を対応させられる、という発想から作りました。

## 使い方

```scala
import guard.*

def register(user: User): Either[Error, Account] =
  guard[Left[Error, Nothing]](Left(Error.Unknown)) {
    ensure(user.age >= 18) orReturn Left(Error.Minor)         // 成人であること
    ensure(user.country == "JP") orReturn Left(Error.Foreign) // 国内ユーザーであること
    ensure(!user.banned)                                      // 停止中でないこと
  }
  Right(createAccount(user))
```

各 `ensure` は、コンパイル時に `if !cond then return value` へ展開されます。

- `orReturn` を書いた条件は、その値で抜けます。
- `orReturn` を省略した条件は、`guard` に渡した default で抜けます。
- 条件は上から順に評価されます。

## 型の規則

- `R` を囲んでいるメソッドの返り値の型、`RR` を `guard` の型引数とすると、`RR <: R` である必要があります。
- default と各 `orReturn` の値は、`RR` に収まる必要があります。
- `RR` を `Left[E, Nothing]` にすると、成功値で早期に抜ける書き間違いを型エラーにできます。
- `guard(None)` のように型引数を省略すると、`RR` は default から推論されます。この場合、default 以外の型の値では抜けられません。

## 制限

- 匿名関数の中では使えません(メソッド全体から抜ける非ローカル return になるため)。
- `guard` の中には `ensure` だけを書けます。
- `return` は予約語なので、`ensure 条件 return 値` ではなく `ensure(条件) orReturn 値` と書きます。
- 返り値の型を省略したメソッドでも動きますが、型は最後の式だけから推論されます。返り値の型の明示を推奨します。

## 開発

```
sbt test
```
