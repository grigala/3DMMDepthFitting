package ch.unibas.cs.gravis.thriftservice.logging

import ch.unibas.cs.gravis.thriftservice.utils.Helpers.Sample

import scala.collection.mutable

/**
 * Shape fitting sampling logger
 *
 * @param logToConsole logging into the console
 */
class ShapeSamplingLogger(logToConsole: Boolean = false) extends AcceptRejectLogger[Sample] {
    private val numAccepted: mutable.Map[String, Int] = collection.mutable.Map[String, Int]()
    private val numRejected: mutable.Map[String, Int] = collection.mutable.Map[String, Int]()
    private val verbosePrintLogger = new VerbosePrintLogger[Sample](Console.out, "")

    override def accept(current: Sample,
                        sample: Sample,
                        generator: ProposalGenerator[Sample],
                        evaluator: DistributionEvaluator[Sample]
                       ): Unit = {
        val numAcceptedSoFar = numAccepted.getOrElseUpdate(sample.generatedBy, 0)
        numAccepted.update(sample.generatedBy, numAcceptedSoFar + 1)
        if (logToConsole) {
            verbosePrintLogger.accept(current, sample, generator, evaluator)
        }
    }

    override def reject(current: Sample,
                        sample: Sample,
                        generator: ProposalGenerator[Sample],
                        evaluator: DistributionEvaluator[Sample]
                       ): Unit = {
        val numRejectedSoFar = numRejected.getOrElseUpdate(sample.generatedBy, 0)
        numRejected.update(sample.generatedBy, numRejectedSoFar + 1)
        if (logToConsole) {
            verbosePrintLogger.reject(current, sample, generator, evaluator)
        }
    }


    def acceptanceRatios(): Map[String, Double] = {
        val generatorNames: Set[String] = numRejected.keys.toSet.union(numAccepted.keys.toSet)
        val acceptanceRatios: Set[(String, Double)] = for (generatorName <- generatorNames) yield {
            val total = (numAccepted.getOrElse(generatorName, 0)
                + numRejected.getOrElse(generatorName, 0)).toDouble
            (generatorName, numAccepted.getOrElse(generatorName, 0) / total)
        }
        acceptanceRatios.toMap
    }
}

object ShapeSamplingLogger {
    def apply(logToConsole: Boolean): ShapeSamplingLogger = new ShapeSamplingLogger(logToConsole)
}
