package ch.unibas.cs.gravis.thriftservice.sampling.evaluators

class IndependentLandmarksEvaluator[D <: Dim](val pointEvaluator: PairEvaluator[Point[D]])
    extends PairEvaluator[Map[String, Landmark[D]]] {

    override def logValue(first: Map[String, Landmark[D]], second: Map[String, Landmark[D]]): Double = {

        val pairValues =
            for {
                (id, lm1) <- first
                lm2 <- second.get(id)
            } yield pointEvaluator.logValue(lm1.point, lm2.point)
        pairValues.sum
    }

    override def toString = "IndependentLandmarksEvaluator(" + pointEvaluator + ")"


}

object IndependentLandmarksEvaluator {
    def apply[D <: Dim](pointEvaluator: PairEvaluator[Point[D]]) = new IndependentLandmarksEvaluator[D](pointEvaluator)
}
