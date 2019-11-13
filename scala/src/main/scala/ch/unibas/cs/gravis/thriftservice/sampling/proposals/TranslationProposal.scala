package ch.unibas.cs.gravis.thriftservice.sampling.proposals

import ch.unibas.cs.gravis.thriftservice.utils.Helpers.Sample
import ch.unibas.cs.gravis.thriftservice.utils.MoMoHelpers

case class TranslationProposal(stddev: Double) extends
    ProposalGenerator[Sample] with TransitionProbability[Sample] {

    val perturbationDistr: MultivariateNormalDistribution = new MultivariateNormalDistribution(
        DenseVector.zeros(3),
        DenseMatrix.eye[Double](3) * stddev * stddev
    )

    def propose(sample: Sample): Sample = {
        val newTranslationParameters = sample.parameters.translationParameters + EuclideanVector.fromBreezeVector(perturbationDistr.sample())
        val newParameters = sample.parameters.copy(translationParameters = newTranslationParameters)

        val rpTemplate: RenderParameter = RenderParameter.default
        val newParamsConverted: RenderParameter = MoMoHelpers.convertParametersToRenderParameters(newParameters, rpTemplate)


        sample.copy(generatedBy = s"TranlationUpdateProposal ($stddev)", rps = newParamsConverted)
    }

    override def logTransitionProbability(from: Sample, to: Sample): Double = {
        val residual: EuclideanVector[_3D] = to.parameters.translationParameters - from.parameters.translationParameters
        perturbationDistr.logpdf(residual.toBreezeVector)
    }
}
