package ch.unibas.cs.gravis.thriftservice.sampling.proposals

import breeze.linalg.DenseVector
import ch.unibas.cs.gravis.thriftservice.utils.DecimateModel
import ch.unibas.cs.gravis.thriftservice.utils.Helpers.Sample
import scalismo.common.PointId
import scalismo.faces.parameters.RenderParameter
import scalismo.geometry.{EuclideanVector, Point, _3D}
import scalismo.mesh.TriangleMesh
import scalismo.numerics.UniformMeshSampler3D
import scalismo.registration.RigidTransformation
import scalismo.sampling.{ProposalGenerator, TransitionProbability}
import scalismo.statisticalmodel.{LowRankGaussianProcess, MultivariateNormalDistribution, StatisticalMeshModel}

case class ShapeProposalICP(model: StatisticalMeshModel,
                            targetMesh: TriangleMesh[_3D],
                            uncertainty: MultivariateNormalDistribution,
                            stepLength: Double)(implicit val rng: scalismo.utils.Random) extends
        ProposalGenerator[Sample] with TransitionProbability[Sample] {

    val targetMeshPoints: IndexedSeq[Point[_3D]] = UniformMeshSampler3D(targetMesh, 500).sample().map(_._1)
    val decimatedModel: StatisticalMeshModel = DecimateModel.decimate(model, 0.95)
    val referenceMesh: TriangleMesh[_3D] = decimatedModel.referenceMesh
    val gpInterpolated: LowRankGaussianProcess[_3D, EuclideanVector[_3D]] = decimatedModel.gp.interpolateNearestNeighbor

    override def propose(current: Sample): Sample = {
        val inversePoseTransform: RigidTransformation[_3D] = current.poseTransformation.inverse
        val currentModelInstance: TriangleMesh[_3D] = decimatedModel.instance(current.parameters.modelCoefficients)
        val targetPtsInModelSpace: IndexedSeq[Point[_3D]] = targetMeshPoints.map(inversePoseTransform)

        val deformation = for (targetPointModelSpace <- targetPtsInModelSpace) yield {
            val referenceId: PointId = currentModelInstance.pointSet.findClosestPoint(targetPointModelSpace).id
            val referencePt: Point[_3D] = decimatedModel.referenceMesh.pointSet.point(referenceId)
            (referencePt, targetPointModelSpace - referencePt, uncertainty)
        }

        val modelCoeffs: DenseVector[Double] = gpInterpolated.coefficients(deformation.toIndexedSeq)
        val currentModelParams: DenseVector[Double] = current.parameters.modelCoefficients
        val newShapeParameters: DenseVector[Double] = currentModelParams + (modelCoeffs - currentModelParams) * stepLength
        val newParameters: RenderParameter = current.rps.copy(momo = current.rps.momo.copy(shape = newShapeParameters.toArray))
        current.copy(generatedBy = s"ShapeProposalICP ($stepLength)", rps = newParameters)
    }

    override def logTransitionProbability(from: Sample, to: Sample): Double = {
        0.0 // deterministic setting
    }
}
