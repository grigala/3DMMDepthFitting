package ch.unibas.cs.gravis.thriftservice.sampling.evaluators

import ch.unibas.cs.gravis.thriftservice.utils.DecimateModel
import ch.unibas.cs.gravis.thriftservice.utils.Helpers.Sample
import org.apache.commons.math3.distribution.CauchyDistribution
import scalismo.common.PointId
import scalismo.geometry.{Point, _3D}
import scalismo.mesh.TriangleMesh
import scalismo.numerics.UniformMeshSampler3D
import scalismo.sampling.DistributionEvaluator
import scalismo.statisticalmodel.StatisticalMeshModel

case class ClosestPointEvaluatorCauchy(model: StatisticalMeshModel,
                                       target: TriangleMesh[_3D],
                                       uncertainty: CauchyDistribution)(implicit val rng: scalismo.utils.Random)
    extends DistributionEvaluator[Sample] {


    // The number of points on the original model is ~53k(BFM), whereas 0.1 decimation rate yields -> ~47k points,
    // 0.3 -> ~37k, 0.6 -> ~21k, 0.9 -> ~5k points
    // The number of points on the original face model is ~28k, 0.9 decimation rate yields -> 2918pts

    val modelDec: StatisticalMeshModel = DecimateModel.decimate(model, 0.95)
    val referencePoints: IndexedSeq[Point[_3D]] = UniformMeshSampler3D(modelDec.referenceMesh, 500).sample().map(_._1)

    val modelIds: IndexedSeq[PointId] = referencePoints.map(refPt => modelDec.referenceMesh.pointSet.findClosestPoint(refPt).id)

    override def logValue(sample: Sample): Double = {

        val currModelInstance = modelDec.instance(sample.parameters.modelCoefficients).transform(sample.poseTransformation)

        val likelihoods = modelIds.map(id => {
            val pointOnCurrentInstance: Point[_3D] = currModelInstance.pointSet.point(id)
            val pointOnTarget: Point[_3D] = target.operations.closestPointOnSurface(pointOnCurrentInstance).point
            val targetIdClosestPoint: PointId = target.pointSet.findClosestPoint(pointOnTarget).id
            val isBoundary: Boolean = target.operations.pointIsOnBoundary(targetIdClosestPoint)

            val observedDeformation: Double = (pointOnTarget - pointOnCurrentInstance).norm

            val pdf: Double = uncertainty.density(observedDeformation)
            // Ignoring the point if it's a boundary point(outliers in most cases)
            if (!isBoundary) Some(math.log(pdf)) else None
        })

        val logLikelihoods: Double = likelihoods.filter(_.isDefined).map(_.get).sum
        logLikelihoods
    }

}
