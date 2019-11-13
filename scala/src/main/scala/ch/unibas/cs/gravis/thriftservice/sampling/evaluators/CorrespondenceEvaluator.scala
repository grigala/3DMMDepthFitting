package ch.unibas.cs.gravis.thriftservice.sampling.evaluators

import ch.unibas.cs.gravis.thriftservice.utils.Helpers.Sample
import ch.unibas.cs.gravis.thriftservice.utils.Utils.marginalizeModelForCorrespondences
import scalismo.common.PointId
import scalismo.geometry.{EuclideanVector, Point, _3D}
import scalismo.mesh.TriangleMesh
import scalismo.sampling.DistributionEvaluator
import scalismo.statisticalmodel.{MultivariateNormalDistribution, StatisticalMeshModel}

/**
 * Landmark Correspondence Evaluator
 * @param model           StatisticalMeshModel
 * @param correspondences Point correspondences
 * @return logLikelihood
 *
 */
case class CorrespondenceEvaluator(model: StatisticalMeshModel,
                                   correspondences: Seq[(PointId, Point[_3D], MultivariateNormalDistribution)]
                                  ) extends DistributionEvaluator[Sample] {

    val (marginalizedModel, newCorrespondences) = marginalizeModelForCorrespondences(model, correspondences)

    override def logValue(sample: Sample): Double = {

        val currModelInstance: TriangleMesh[_3D] = marginalizedModel
            .instance(sample.parameters.modelCoefficients)
            .transform(sample.poseTransformation)

        val likelihoods = newCorrespondences.map(correspondence => {
            val (id: PointId, targetPoint: Point[_3D], uncertainty: MultivariateNormalDistribution) = correspondence
            val modelInstancePoint: Point[_3D] = currModelInstance.pointSet.point(id)
            val observedDeformation: EuclideanVector[_3D] = targetPoint - modelInstancePoint

            uncertainty.logpdf(observedDeformation.toBreezeVector)
        })


        val loglikelihood: Double = likelihoods.sum
        loglikelihood
    }
}
