package ch.unibas.cs.gravis.thriftservice.sampling.evaluators

import scalismo.sampling.DistributionEvaluator
import scalismo.utils.Memoize

case class CachedEvaluator[A](evaluator: DistributionEvaluator[A]) extends DistributionEvaluator[A] {
    val memoizedLogValue = Memoize(evaluator.logValue, 10)

    override def logValue(sample: A): Double = {
        memoizedLogValue(sample)
    }
}
