package ch.unibas.cs.gravis.thriftservice.sampling.evaluators

import ch.unibas.cs.gravis.thriftservice.utils.{DecimateModel, MoMoHelpers}
import scalismo.common.PointId
import scalismo.faces.momo.MoMo
import scalismo.faces.parameters.RenderParameter
import scalismo.geometry.{Point, _3D}
import scalismo.mesh.TriangleMesh
import scalismo.numerics.UniformMeshSampler3D
import scalismo.sampling.DistributionEvaluator
import scalismo.statisticalmodel.{MultivariateNormalDistribution, StatisticalMeshModel}
import scalismo.utils.Random

case class MultiNormalMoMoShapeEvaluator(model: MoMo,
                                         target: TriangleMesh[_3D],
                                         uncertainty: MultivariateNormalDistribution)(implicit val rng: Random)
        extends DistributionEvaluator[RenderParameter] {

    val referencePoints: IndexedSeq[Point[_3D]] = UniformMeshSampler3D(model.referenceMesh, 1000).sample().map(_._1)

    //    val ssm: StatisticalMeshModel = MoMoHelpers.convertMoMoToSMM(model)
    //    val decimatedModel: StatisticalMeshModel = DecimateModel.decimate(ssm, 0.95)

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
            val observedDeformation = pointOnTarget - pointOnCurrentInstance
            val pdf = uncertainty.logpdf(observedDeformation.toBreezeVector)

            if (!isBoundary) Some(pdf) else None
        })

        val logLikelihoods = likelihoods.filter(_.isDefined).map(_.get).sum
        logLikelihoods
    }
}
