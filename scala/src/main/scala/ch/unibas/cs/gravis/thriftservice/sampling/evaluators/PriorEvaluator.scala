package ch.unibas.cs.gravis.thriftservice.sampling.evaluators

import ch.unibas.cs.gravis.thriftservice.utils.Helpers.Sample

case class PriorEvaluator(model: StatisticalMeshModel)
    extends DistributionEvaluator[Sample] {

    val translationPrior: Gaussian = breeze.stats.distributions.Gaussian(0.0, 5.0)
    val rotationPrior: Gaussian = breeze.stats.distributions.Gaussian(0, 0.1)

    override def logValue(sample: Sample): Double = {
        model.gp.logpdf(sample.parameters.modelCoefficients) +
            translationPrior.logPdf(sample.parameters.translationParameters.x) +
            translationPrior.logPdf(sample.parameters.translationParameters.y) +
            translationPrior.logPdf(sample.parameters.translationParameters.z) +
            rotationPrior.logPdf(sample.parameters.rotationParameters._1) +
            rotationPrior.logPdf(sample.parameters.rotationParameters._2) +
            rotationPrior.logPdf(sample.parameters.rotationParameters._3)
    }
}
