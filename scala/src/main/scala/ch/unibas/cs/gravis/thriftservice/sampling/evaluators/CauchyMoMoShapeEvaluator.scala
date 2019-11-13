package ch.unibas.cs.gravis.thriftservice.sampling.evaluators

import ch.unibas.cs.gravis.thriftservice.utils.{DecimateModel, MoMoHelpers}
import org.apache.commons.math3.distribution.CauchyDistribution
import scalismo.common.PointId
import scalismo.faces.momo.MoMo
import scalismo.faces.parameters.RenderParameter
import scalismo.geometry.{Point, _3D}
import scalismo.mesh.TriangleMesh
import scalismo.numerics.UniformMeshSampler3D
import scalismo.sampling.DistributionEvaluator
import scalismo.statisticalmodel.{MultivariateNormalDistribution, StatisticalMeshModel}
import scalismo.utils.Random

case class CauchyMoMoShapeEvaluator(model: MoMo,
                                    target: TriangleMesh[_3D],
                                    uncertainty: CauchyDistribution)(implicit val rng: Random)
    extends DistributionEvaluator[RenderParameter] {

    val ssm: StatisticalMeshModel = MoMoHelpers.convertMoMoToSMM(model)
    val decimatedModel: StatisticalMeshModel = DecimateModel.decimate(ssm, 0.9)

    val referencePoints: IndexedSeq[Point[_3D]] = UniformMeshSampler3D(model.referenceMesh, 500).sample().map(_._1)

    val modelIds: IndexedSeq[PointId] = referencePoints.map(refPt => model.referenceMesh.pointSet.findClosestPoint(refPt).id)

    override def logValue(sample: RenderParameter): Double = {
        val currentModelInstance = model
            .instance(sample.momo.coefficients)
            .transform(sample.pose.transform.apply).shape

        val likelihoods = modelIds.map(id => {
            val pointOnCurrentInstance = currentModelInstance.pointSet.point(id)
            val pointOnTarget = target.operations.closestPointOnSurface(pointOnCurrentInstance).point
            val targetIdClosestPoint = target.pointSet.findClosestPoint(pointOnTarget).id
            val isBoundary = target.operations.pointIsOnBoundary(targetIdClosestPoint)
            val observedDeformation = (pointOnTarget - pointOnCurrentInstance).norm
            val pdf = uncertainty.density(observedDeformation)

            if (!isBoundary) Some(math.log(pdf)) else None
        })

        val logLikelihoods = likelihoods.filter(_.isDefined).map(_.get).sum
        logLikelihoods
    }
}
