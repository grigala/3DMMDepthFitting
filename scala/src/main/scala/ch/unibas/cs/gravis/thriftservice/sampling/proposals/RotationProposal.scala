package ch.unibas.cs.gravis.thriftservice.sampling.proposals

import breeze.linalg.{DenseMatrix, DenseVector}
import ch.unibas.cs.gravis.thriftservice.utils.Helpers.Sample
import ch.unibas.cs.gravis.thriftservice.utils.MoMoHelpers
import scalismo.faces.parameters.RenderParameter
import scalismo.sampling.{ProposalGenerator, TransitionProbability}
import scalismo.statisticalmodel.MultivariateNormalDistribution
import scalismo.utils.Random.implicits._

case class RotationProposal(stddev: Double) extends
        ProposalGenerator[Sample] with TransitionProbability[Sample] {
    val perturbationDistr = new MultivariateNormalDistribution(
        DenseVector.zeros[Double](3),
        DenseMatrix.eye[Double](3) * stddev * stddev)

    def propose(sample: Sample): Sample = {
        val perturbation: DenseVector[Double] = perturbationDistr.sample
        val newRotationParameters: (Double, Double, Double) = (
                sample.parameters.rotationParameters._1 + perturbation(0),
                sample.parameters.rotationParameters._2 + perturbation(1),
                sample.parameters.rotationParameters._3 + perturbation(2)
        )
        val newParameters = sample.parameters.copy(rotationParameters = newRotationParameters)

        val rpTemplate: RenderParameter = RenderParameter.default
        val newParamsConverted = MoMoHelpers.convertParametersToRenderParameters(newParameters, rpTemplate)

        sample.copy(generatedBy = s"RotationUpdateProposal ($stddev)", rps = newParamsConverted)
    }

    override def logTransitionProbability(from: Sample, to: Sample): Double = {
        val residual: DenseVector[Double] = DenseVector(
            to.parameters.rotationParameters._1 - from.parameters.rotationParameters._1,
            to.parameters.rotationParameters._2 - from.parameters.rotationParameters._2,
            to.parameters.rotationParameters._3 - from.parameters.rotationParameters._3
        )
        perturbationDistr.logpdf(residual)
    }
}
