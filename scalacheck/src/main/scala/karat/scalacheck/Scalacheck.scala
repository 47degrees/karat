package karat.scalacheck

import cats._
import cats.syntax.all._
import karat.concrete.progression.regular.{CheckKt, RegularStepResultManager}
import karat.concrete.progression.{Info, Step}
import kotlin.jvm.functions
import org.scalacheck.Prop

import scala.jdk.CollectionConverters._

object Scalacheck {
  type Atomic[A] = karat.concrete.Atomic[A, Prop.Result]
  type Formula[A] = karat.concrete.Formula[A, Prop.Result]

  class ScalacheckStepResultManager[A] extends RegularStepResultManager[A, Prop.Result, Prop.Result] {
    override def getEverythingOk: Prop.Result = Prop.Result(Prop.True)
    override def getFalseFormula: Prop.Result = Prop.Result(Prop.False)
    override def getUnknown: Prop.Result = Prop.Result(Prop.Undecided)
    override def isOk(result: Prop.Result): Boolean = result == null || result.success
    override def andResults(results: java.util.List[_ <: Prop.Result]): Prop.Result =
      results.asScala.fold(Prop.Result(Prop.True))((x: Prop.Result, y: Prop.Result) => x && y)
    override def orResults(results: java.util.List[_ <: Prop.Result]): Prop.Result =
      results.asScala.fold(Prop.Result(Prop.False))((x: Prop.Result, y: Prop.Result) => x || y)
    override def negationWasTrue(formula: karat.concrete.Formula[_ >: A, _ <: Prop.Result]): Prop.Result =
      Prop.Result(Prop.False).label("negation was true")
    override def shouldHoldEventually(formula: karat.concrete.Formula[_ >: A, _ <: Prop.Result]): Prop.Result =
      Prop.Result(Prop.False).label("should hold eventually")
    override def predicate(test: functions.Function1[_ >: A, _ <: Prop.Result], value: A): Prop.Result =
      test.invoke(value)
  }

  def checkFormula[Action, State, Response](actions: List[Action], initial: State, step: (Action, State) => Step[State, Response])(
    formula: Formula[Info[Action, State, Response]]
  ): Prop = {
    val problem = CheckKt.check[Action, State, Response, Prop.Result, Prop.Result](
      new ScalacheckStepResultManager(),
      formula.asInstanceOf[Formula[Info[_ <: Action, _ <: State, _ <: Response]]],
      actions.asJava,
      initial,
      (action, current) => { step(action, current) },
      new java.util.ArrayList()
    )
    if (problem == null) Prop.passed else Prop(problem.getError)
  }

  def checkFormula[F[_] : Monad, Action, State, Response](actions: List[Action], initial: F[State], step: (Action, State) => F[Step[State, Response]])(
    formula: Formula[Info[Action, State, Response]]
  ): F[Prop] = initial.flatMap(checkFormula(new ScalacheckStepResultManager[Info[Action, State, Response]](), step, actions, _, formula))

  def checkFormula[F[_] : Monad, Action, State, Response](
    resultManager: ScalacheckStepResultManager[Info[Action, State, Response]],
    step: (Action, State) => F[Step[State, Response]],
    actions: List[Action],
    current: State,
    formula: Formula[Info[Action, State, Response]]
  ): F[Prop] = Monad[F].tailRecM((actions, current, formula)) {
    case (Nil, _, formula) =>
      resultToProp(resultManager, CheckKt.leftToProve(resultManager, formula)).asRight.pure
    case (action :: rest, current, formula) => step(action, current).flatMap {
        case null =>
          resultToProp(resultManager, CheckKt.leftToProve(resultManager, formula)).asRight.pure
        case oneStepFurther@_ =>
          val progress = CheckKt.check(resultManager, formula, new Info(action, current, oneStepFurther.getState, oneStepFurther.getResponse))
          if (!resultManager.isOk(progress.getResult))
            Prop(progress.getResult).asRight.pure
          else
            (rest, oneStepFurther.getState, progress.getNext).asLeft.pure
      }
    }

  private def resultToProp[Action, State, Response](
    resultManager: ScalacheckStepResultManager[Info[Action, State, Response]],
    result: Prop.Result
  ): Prop = if (resultManager.isOk(result)) Prop.passed else Prop(result)

}
