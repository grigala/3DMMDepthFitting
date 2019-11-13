package ch.unibas.cs.gravis.thriftservice.sampling.proposals

import ch.unibas.cs.gravis.thriftservice.utils.Helpers.Sample
import ch.unibas.cs.gravis.thriftservice.utils.MoMoHelpers

case class ShapeProposal(paramVectorSize: Int, stddev: Double)
    extends ProposalGenerator[Sample] with TransitionProbability[Sample] {

    val perturbationDistr = new MultivariateNormalDistribution(
        DenseVector.zeros(paramVectorSize),
        DenseMatrix.eye[Double](paramVectorSize) * stddev * stddev
    )
    val generator: GaussianParameterProposal = GaussianParameterProposal(stddev)

    override def propose(sample: Sample): Sample = {
        val perturbation: DenseVector[Double] = perturbationDistr.sample()
        val newParameters = sample.parameters.copy(modelCoefficients = sample.parameters.modelCoefficients + perturbation)

        val rpTemplate: RenderParameter = RenderParameter.default
        val newParamsConverted: RenderParameter = MoMoHelpers.convertParametersToRenderParameters(newParameters, rpTemplate)

        sample.copy(generatedBy = s"ShapeUpdateProposal ($stddev)", rps = newParamsConverted)
    }

    override def logTransitionProbability(from: Sample, to: Sample): Double = {
        val residual: DenseVector[Double] = to.parameters.modelCoefficients - from.parameters.modelCoefficients
        perturbationDistr.logpdf(residual)
    }
}
