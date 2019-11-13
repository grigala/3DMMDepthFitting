package ch.unibas.cs.gravis.thriftservice.sampling.proposals

import breeze.linalg.{DenseMatrix, DenseVector}
import ch.unibas.cs.gravis.thriftservice.utils.Helpers.Sample
import ch.unibas.cs.gravis.thriftservice.utils.MoMoHelpers
import scalismo.faces.parameters.RenderParameter
import scalismo.geometry.{EuclideanVector, _3D}
import scalismo.sampling.{ProposalGenerator, TransitionProbability}
import scalismo.statisticalmodel.MultivariateNormalDistribution
import scalismo.utils.Random.implicits._

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
