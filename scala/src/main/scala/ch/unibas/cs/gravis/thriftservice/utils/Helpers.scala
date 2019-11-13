package ch.unibas.cs.gravis.thriftservice.utils

import java.awt.Color
import java.io.{File, FileOutputStream}
import java.text.{DateFormat, SimpleDateFormat}
import java.util.{Calendar, Date}

import breeze.linalg.DenseVector
import ch.unibas.cs.gravis.thriftservice.rendering.{ParametricShader, ZBuffer}
import scalismo.color.{RGB, RGBA}
import scalismo.common.{DiscreteField, DiscreteScalarField, DiscreteVectorField, PointId, UnstructuredPointsDomain}
import scalismo.faces.image.PixelImage
import scalismo.faces.mesh.ColorNormalMesh3D
import scalismo.faces.parameters.RenderParameter
import scalismo.faces.render.{Affine3D, RenderTransforms, TriangleRenderer}
import scalismo.geometry._
import scalismo.mesh.{MeshMetrics, MeshSurfaceProperty, ScalarMeshField, TriangleId, TriangleMesh, TriangleMesh3D}
import scalismo.registration.{LandmarkRegistration, RigidTransformation, RotationTransform, TranslationTransform}
import scalismo.ui.api.{ScalismoUI, ScalismoUIHeadless}
import scalismo.faces.io.renderparameters.RenderParameterJSONFormat._
import scalismo.mesh.boundingSpheres.ClosestPointOnSurface
import scalismo.utils.Random
import scalismo.utils.Random._
import spray.json._

import scala.collection.immutable

object Helpers {
    val dateFormat: DateFormat = new SimpleDateFormat("HH-mm-ss, MMM dd")
    val date: Date = Calendar.getInstance().getTime
    val saveTime: String = dateFormat.format(date)

    case class Parameters(translationParameters: EuclideanVector[_3D],
                          rotationParameters: (Double, Double, Double),
                          modelCoefficients: DenseVector[Double])

    case class Sample(generatedBy: String, rps: RenderParameter, rotationCenter: Point[_3D]) {
        // Converting RenderParameter to Parameter
        val parameters: Parameters = MoMoHelpers.convertRenderParametersToParameters(rps)

        def poseTransformation: RigidTransformation[_3D] = {

            val translation: TranslationTransform[_3D] = TranslationTransform(parameters.translationParameters)
            val rotation: RotationTransform[_3D] = RotationTransform(
                parameters.rotationParameters._3,
                parameters.rotationParameters._2,
                parameters.rotationParameters._1,
                rotationCenter
            )
            RigidTransformation(translation, rotation)
        }

        def transform: Affine3D = RenderTransforms.modelTransform(
            translation = parameters.translationParameters,
            scaling = 1.0,
            pitch = parameters.rotationParameters._1,
            yaw = parameters.rotationParameters._2,
            roll = parameters.rotationParameters._3
        )
    }

    /**
     * Computing attribute correspondences
     *
     * @param movingMesh    mesh that is going to be moved
     * @param ptIds         ids of points to be evaluated
     * @param transformedGT mesh towards which moving mesh is moving, in our case ground truth
     * @return a map of corresponding points
     */
    def attributeCorrespondences(movingMesh: TriangleMesh[_3D], ptIds: Seq[PointId], transformedGT: TriangleMesh[_3D]): Seq[(Point[_3D], Point[_3D])] = {
        ptIds.map { id: PointId =>
            val pt = movingMesh.pointSet.point(id)
            val closestPointOnMesh2 = transformedGT.pointSet.findClosestPoint(pt).point
            (pt, closestPointOnMesh2)
        }
    }

    /**
     * Returns the Hausdorff distance between the two meshes
     * only takes into account points that are not a boundary points.
     *
     * @param m1 TriangleMesh[_3D]
     * @param m2 TriangleMesh[_3D]
     * @return Hausdorff distance [Double]
     */
    def hausdorffDistance(m1: TriangleMesh[_3D], m2: TriangleMesh[_3D]): Double = {
        def allDistsBetweenMeshes(mm1: TriangleMesh[_3D], mm2: TriangleMesh[_3D]): Iterator[Option[Double]] = {
            for (ptM1 <- mm1.pointSet.points) yield {
                val cpM2 = mm2.operations.closestPointOnSurface(ptM1).point
                val cpId = mm2.pointSet.findClosestPoint(cpM2).id
                val isBoundary = mm2.operations.pointIsOnBoundary(cpId)

                if (!isBoundary) Some((ptM1 - cpM2).norm) else None
            }
        }

        val d1 = allDistsBetweenMeshes(m1, m2).filter(_.isDefined).map(_.get)
        val d2 = allDistsBetweenMeshes(m2, m1).filter(_.isDefined).map(_.get)

        Math.max(d1.max, d2.max)
    }

    def customAVGDistance(m1: TriangleMesh[_3D], m2: TriangleMesh[_3D]): Double = {
        val dists = for (ptM1 <- m1.pointSet.points) yield {
            val cpM2 = m2.operations.closestPointOnSurface(ptM1).point
            val cpId = m2.pointSet.findClosestPoint(cpM2).id
            val isBoundary = m2.operations.pointIsOnBoundary(cpId)
            if (!isBoundary) Some((ptM1 - cpM2).norm) else None
        }
        dists.filter(_.isDefined).map(_.get).sum / m1.pointSet.numberOfPoints
    }

    /**
     * Produces a ScalarMeshField with colored distance intensities.
     *
     * @param bestFit    Mesh to color
     * @param targetMesh original mesh
     * @return ScalarMeshField[Double]
     */
    def colorDistance(bestFit: TriangleMesh[_3D], targetMesh: TriangleMesh[_3D]): ScalarMeshField[Double] = {
        val ptsWithDist = for (id <- bestFit.pointSet.pointIds) yield {
            val bfPoint: Point[_3D] = bestFit.pointSet.point(id)
            val correspondingPointOnSurface = targetMesh.operations.closestPoint(bfPoint)
            val distance = (bfPoint - correspondingPointOnSurface.point).norm
            (bfPoint, distance)
        }
        val data: immutable.IndexedSeq[Double] = ptsWithDist.toIndexedSeq.map(_._2)
        ScalarMeshField(bestFit, data)
    }

    /**
     * Takes two meshes, aligns and visualizes them using ICP algorithm
     *
     * @param movingMesh    target mesh TriangleMesh[_3D]
     * @param movingMeshLms target mesh landmarks Seq[Landmark[_3D]
     * @param gtMesh        ground truth mesh TriangleMesh[_3D]
     * @param gtMeshLms     ground truth landmarks Seq[Landmark[_3D]
     */
    def alignMeshesICP(movingMesh: TriangleMesh[_3D],
                       movingMeshLms: Seq[Landmark[_3D]],
                       gtMesh: TriangleMesh[_3D],
                       gtMeshLms: Seq[Landmark[_3D]],
                       fos: FileOutputStream): Unit = {
        //        val ui = ScalismoUIHeadless()
        val ui = ScalismoUI()

        val gtGroup = ui.createGroup("ground truth")

        val gtLandmarkNames = gtMeshLms.map(lm => lm.id)
        val targetLandmarksFiltered = movingMeshLms.filter(lm => gtLandmarkNames.contains(lm.id))
        val targetLandmarksSorted = targetLandmarksFiltered.sortBy(lm => lm.id)
        //        assert(targetLandmarksFiltered.size == gtLandmarks.size, "landmark size should be same")
        val gtLandmarksSorted = gtMeshLms.sortBy(lm => lm.id)
        val rigidTransformation: RigidTransformation[_3D] = LandmarkRegistration.rigid3DLandmarkRegistration(
            gtLandmarksSorted,
            targetLandmarksSorted,
            center = Point(0, 0, 0)
        )

        val transformedGT = gtMesh.transform(rigidTransformation)
        val gtView = ui.show(gtGroup, transformedGT, "groundtruth")

        val ptIds = (0 until movingMesh.pointSet.numberOfPoints by 100).map(it => PointId(it))
        val rigidFit = ICPRigidAlign(movingMesh, ptIds, 200, transformedGT)
        val alignedGroup = ui.createGroup("aligned")
        val rigidFitView = ui.show(alignedGroup, rigidFit, "alignedTarget")
        rigidFitView.color = Color.GREEN
        gtView.opacity = 0.35

        val scalarMeshField = colorDistance(rigidFit, transformedGT)
        ui.show(scalarMeshField, "scalar mesh field")
        Console.withOut(fos) {
            println(s"- custom average distance: ${customAVGDistance(rigidFit, transformedGT)}")
            println(s"- custom hausdorff distance(ignores boundary points): ${hausdorffDistance(rigidFit, transformedGT)}")
        }
    }


    //    val targetMesh = MeshIO.readMesh(new File("targetData/targetMesh.ply")).get
    //    val targetLandmarks = LandmarkIO.readLandmarksJson[_3D](new File("targetData/target3DLandmarks.json")).get

    //    val gtMesh = MeshIO.readMesh(new File("data/neutralMe.ply")).get
    //    val gtLandmarks = LandmarkIO.readLandmarksJson[_3D](new File("data/gtLandmarks.json")).get

    /**
     * Performs rigid3DLandmarkRegistration iteratively
     *
     * @param movingMesh         mesh that is being moved
     * @param ptIds              point ids
     * @param numberOfIterations number of iterations
     * @param staticMesh         mesh to move towards
     * @return final position of movingMesh -> TriangleMesh[_3D]
     */
    @scala.annotation.tailrec
    def ICPRigidAlign(movingMesh: TriangleMesh[_3D],
                      ptIds: Seq[PointId],
                      numberOfIterations: Int,
                      staticMesh: TriangleMesh[_3D]
                     ): TriangleMesh[_3D] = {
        if (numberOfIterations == 0) {
            movingMesh
        } else {
            val correspondences = attributeCorrespondences(movingMesh, ptIds, staticMesh)
            val transform = LandmarkRegistration.rigid3DLandmarkRegistration(correspondences, center = Point3D(0, 0, 0))
            val transformed = movingMesh.transform(transform)

            ICPRigidAlign(transformed, ptIds, numberOfIterations - 1, staticMesh)
        }
    }

    /**
     * Overlays rendered face image over full image
     *
     * @param bgImg everything that's not a face
     * @param fgImg face image
     * @return combination of face image and background image
     */
    def overlayImages(bgImg: PixelImage[RGBA], fgImg: PixelImage[RGBA]): PixelImage[RGBA] = {
        bgImg.zip(fgImg).map {
            case (bg: RGBA, fg: RGBA) =>
                bg.toRGB.blend(fg).toRGBA
        }
    }

    def renderParameterMesh(parameter: RenderParameter,
                            mesh: ColorNormalMesh3D,
                            clearColor: RGBA = RGBA.BlackTransparent): PixelImage[RGBA] = {
        val buffer = ZBuffer(parameter.imageSize.width, parameter.imageSize.height, clearColor)

        val worldMesh = mesh.transform(parameter.modelViewTransform)
        val backfaceCullingFilter = (_: TriangleId) => true

        TriangleRenderer.renderMesh(
            mesh.shape,
            backfaceCullingFilter,
            parameter.pointShader,
            parameter.imageSize.screenTransform,
            parameter.pixelShader(mesh),
            buffer).toImage
    }

    /**
     * render zBuffer of a mesh with specified colors and normals according to scene description parameter
     * colors and normals are ignored
     *
     * @param renderParameter scene description
     * @param mesh            mesh to render, positions
     * @param color           color (albedo)
     * @param normals         normals
     * @param clearColor      background color of buffer
     * @return
     */
    def renderParameterZ(renderParameter: RenderParameter,
                         mesh: TriangleMesh3D,
                         color: MeshSurfaceProperty[RGBA],
                         normals: MeshSurfaceProperty[EuclideanVector[_3D]],
                         clearColor: RGBA = RGBA.BlackTransparent): PixelImage[Option[Double]] = {
        val buffer = ZBuffer(renderParameter.imageSize.width, renderParameter.imageSize.height, clearColor)
        val shader = ParametricShader(mesh, color, normals, renderParameter)
        TriangleRenderer.renderMesh(
            mesh,
            renderParameter.pointShader,
            shader.pixelShader,
            buffer)

        buffer.zBufferToDepthImage(renderParameter.camera.near, renderParameter.camera.far)
    }

}
