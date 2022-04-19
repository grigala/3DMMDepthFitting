package ch.unibas.cs.gravis.thriftservice.sampling.evaluators

import scalismo.sampling.DistributionEvaluator

class PartsModelEvaluator[A, PART](evaluator: DistributionEvaluator[PART], extract: A => PART)
        extends DistributionEvaluator[A] {

    override def logValue(sample: A): Double = {
        evaluator.logValue(extract(sample))
    }
}
