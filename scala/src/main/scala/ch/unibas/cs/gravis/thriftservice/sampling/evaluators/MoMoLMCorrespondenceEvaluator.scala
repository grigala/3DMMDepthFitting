package ch.unibas.cs.gravis.thriftservice.sampling.evaluators

import ch.unibas.cs.gravis.thriftservice.utils.MoMoHelpers._
import ch.unibas.cs.gravis.thriftservice.utils.Utils._
import scalismo.common.PointId
import scalismo.faces.momo.MoMo
import scalismo.faces.parameters.RenderParameter
import scalismo.geometry.{EuclideanVector, Point, _3D}
import scalismo.mesh.TriangleMesh
import scalismo.sampling.DistributionEvaluator
import scalismo.statisticalmodel.{MultivariateNormalDistribution, StatisticalMeshModel}

/**
 * Landmark Correspondence Evaluator
 *
 * @param model           MoMo later converted to StatisticalMeshModel
 * @param correspondences Landmark point correspondences
 * @return logLikelihood over landmark points
 *
 */
case class MoMoLMCorrespondenceEvaluator(model: MoMo,
                                         correspondences: Seq[(PointId, Point[_3D], MultivariateNormalDistribution)]
                                        ) extends DistributionEvaluator[RenderParameter] {

    val smm: StatisticalMeshModel = convertMoMoToSMM(model)
    val (marginalizedModel, newCorrespondences) = marginalizeModelForCorrespondences(smm, correspondences)

    override def logValue(sample: RenderParameter): Double = {

        val currModelInstance: TriangleMesh[_3D] = marginalizedModel
                .instance(sample.momo.coefficients.shape)
                .transform(sample.pose.transform.apply)

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
