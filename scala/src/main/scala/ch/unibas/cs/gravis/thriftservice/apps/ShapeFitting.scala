package ch.unibas.cs.gravis.thriftservice.apps

import java.awt.Color
import java.io.{File, FileOutputStream}
import java.util.Calendar

import ch.unibas.cs.gravis.thriftservice.logging.ShapeSamplingLogger
import ch.unibas.cs.gravis.thriftservice.sampling.evaluators.{ClosestPointEvaluatorCauchy, CorrespondenceEvaluator, PriorEvaluator}
import ch.unibas.cs.gravis.thriftservice.sampling.proposals.{RotationProposal, ShapeProposal, ShapeProposalICP, TranslationProposal}
import ch.unibas.cs.gravis.thriftservice.utils.Helpers._
import ch.unibas.cs.gravis.thriftservice.utils.MoMoHelpers._
import ch.unibas.cs.gravis.thriftservice.utils.Utils._

/* ===implicits=== */

object ShapeFitting {
    def fitShape(modelFile: File,
                 targetImage: PixelImage[RGB],
                 target3DLandmarks: Seq[Landmark[_3D]],
                 targetPCMesh: TriangleMesh[_3D],
                 debug: Boolean,
                 statistics: Boolean): (
        TriangleMesh[_3D],
            RenderParameter,
            TriangleMesh[_3D],
            Map[String, Landmark[_3D]],
            FileOutputStream,
            Seq[Landmark[_3D]],
            Seq[Landmark[_3D]]
        ) = {

        println(s"[ShapeFitting] Starting shape fitting pipeline with Debug mode: $debug, and statistics logging: $statistics")
        scalismo.initialize()
        val image = targetImage
        val targetLMsFiltered = target3DLandmarks
        val targetTriangleMesh = targetPCMesh
        val momoModelFull = MoMoIO.read(modelFile).get
        val shapeFittingStart = System.currentTimeMillis()
        val ui = if (debug) ScalismoUI() else ScalismoUIHeadless() // disables front-end

        val rank = 50
        val momoModel: MoMo = momoModelFull.neutralModel.truncate(rank, rank)
        val transformedModel: StatisticalMeshModel = convertMoMoToSMM(momoModel)
        val momoInstance: MoMoInstance = MoMoInstance(IndexedSeq.fill(transformedModel.rank)(0.0), IndexedSeq.fill(50)(0.0), IndexedSeq.fill(5)(0.0), modelFile.toURI)
        val momoLandmarks = momoModel.landmarks.values.toIndexedSeq

        val targetImageRGBA = image.map(_.toRGBA)
        // Dealing with outlier landmark points thar are either empty or de-projected wrongly due to stream alignment
        val targetLmNames: Seq[String] = targetLMsFiltered.map(lm => lm.id)
        val target3DLM: Map[String, Landmark[_3D]] = targetLMsFiltered.map(lm => lm.id -> {
            Landmark(lm.id, lm.point, None, None)
        }).toMap

        val momoLandmarksFiltered: Seq[Landmark[_3D]] = momoLandmarks.filter(lm => targetLmNames.contains(lm.id))

        if (debug) {
            println("All model landmarks: " + momoLandmarks.size)
            println("Thrift(target) landmarks: " + targetLMsFiltered.size)
            println("Final target landmarks(0 value removed): " + targetLMsFiltered.size)

            if (targetLMsFiltered.size == targetLMsFiltered.size) {
                println("All landmarks were successfully detected")
            } else {
                println("Removing landmarks with 0 points. Target landmarks:")
                targetLMsFiltered.foreach(println)

            }
        }

        val momoLmsSorted = momoLandmarksFiltered.sortBy(lm => lm.id)
        val targetLmsSorted = targetLMsFiltered.sortBy(lm => lm.id)

        val xMinClippingValue = targetLmsSorted.minBy(lm => lm.point.x).point.x
        val xMaxClippingValue = targetLmsSorted.maxBy(lm => lm.point.x).point.x
        val yMinClippingValue = targetLmsSorted.minBy(lm => lm.point.y).point.y
        val yMaxClippingValue = targetLmsSorted.maxBy(lm => lm.point.y).point.y
        val zMinClippingValue = targetLmsSorted.minBy(lm => lm.point.z).point.z
        val zMaxClippingValue = targetLmsSorted.maxBy(lm => lm.point.z).point.z


        if (debug) {
            println("Clipping values, xmin, xmax, ymin, ymax:")
            println(xMinClippingValue, xMaxClippingValue, yMinClippingValue, yMaxClippingValue)
        }

        val xPositiveClippedMesh = targetTriangleMesh.operations.clip(pt => pt.x > xMaxClippingValue) // Right
        val xNegativeClippedMesh = xPositiveClippedMesh.operations.clip(pt => pt.x < xMinClippingValue) // Left
        val yPositiveClippedMesh = xNegativeClippedMesh.operations.clip(pt => pt.y > yMaxClippingValue) // Bottom
        val yNegativeClippedMesh = yPositiveClippedMesh.operations.clip(pt => pt.y < yMinClippingValue - 30) // Top

        // if something is in between the target and the camera
        val discardPointsInBetweenTargetAndCamera = yNegativeClippedMesh.operations.clip(pt => pt.z < zMinClippingValue - 10)
        val targetMesh = discardPointsInBetweenTargetAndCamera.operations.clip(pt => pt.z > zMaxClippingValue)

        MeshIO.writeMesh(targetMesh, new File(s"targetData/gen/mesh/targetMesh_$saveTime.ply"))
        if (debug) {
            MeshIO.writeMesh(targetTriangleMesh, new File(s"targetData/gen/mesh/targetMeshUnClipped_$saveTime.ply"))
            println("Number of points after clipping: " + targetMesh.pointSet.numberOfPoints)
        }
        val rigidTransform: RigidTransformation[_3D] = LandmarkRegistration
            .rigid3DLandmarkRegistration(momoLmsSorted, targetLmsSorted, center = Point(0, 0, 0))

        // GUI Stuff
        val modelGroup = ui.createGroup("modelGroup")
        val targetGroup = ui.createGroup("target")
        val modelLmViews = ui.show(modelGroup, momoLmsSorted, "modelLandmarks")
        val targetLmViews = ui.show(targetGroup, targetLmsSorted, "targetLandmarks")
        val modelView = ui.show(modelGroup, transformedModel, "transformedModel")
        val pcMesh = ui.show(targetGroup, targetMesh, "targetMesh")
        pcMesh.opacity = 0.5
        modelView.meshView.color = Color.GREEN
        modelLmViews.foreach(lmView => lmView.color = Color.YELLOW)
        targetLmViews.foreach(lmView => lmView.color = Color.RED)


        val modelLmIds = momoLmsSorted.map(lm => momoModel.neutralModel.referenceMesh.pointSet.pointId(lm.point).get)
        val targetPoints = targetLmsSorted.map(lm => lm.point)

        val landmarkNoiseVariance = 9.0
        val uncertainty = MultivariateNormalDistribution(
            DenseVector.zeros[Double](3),
            DenseMatrix.eye[Double](3) * landmarkNoiseVariance
        )

        val cauchyUncertainty = new CauchyDistribution(0.0, 0.5)

        val landmarkCorrespondences = modelLmIds.zip(targetPoints).map(modelIdWithTargetPoint => {
            val (modelId, targetPoint) = modelIdWithTargetPoint
            (modelId, targetPoint, uncertainty)
        })

        val likelihoodEvaluator = CorrespondenceEvaluator(transformedModel, landmarkCorrespondences).cached(50)
        val priorEvaluator = PriorEvaluator(transformedModel).cached(50)
        val closestPointEvaluatorCauchy = ClosestPointEvaluatorCauchy(transformedModel, targetMesh, cauchyUncertainty).cached(50)

        val posteriorEvaluator = ProductEvaluator(priorEvaluator, likelihoodEvaluator, closestPointEvaluatorCauchy).cached(50)

        val shapeUpdateProposal = ShapeProposal(transformedModel.rank, 0.1)
        val shapeProposalICP = ShapeProposalICP(transformedModel, targetMesh, uncertainty, 0.1)
        val rotationUpdateProposal = RotationProposal(0.01)
        val translationUpdateProposal = TranslationProposal(1.0)

        val generator = MixtureProposal.fromProposalsWithTransition(
            (0.3, shapeUpdateProposal),
            (0.3, shapeProposalICP),
            (0.2, rotationUpdateProposal),
            (0.2, translationUpdateProposal)
        )

        val translation: EuclideanVector[_3D] = rigidTransform.translation.t
        val rotationTransform: SquareMatrix[_3D] = eulerAnglesToRotMatrix3D(rigidTransform.rotation.parameters.toDenseVector)
        val decomposedTransform: (Double, Double, Double) = decompose(rotationTransform)

        val initialParameters = RenderParameter.default.copy(
            pose = Pose(
                scaling = 1.0,
                translation = translation,
                roll = decomposedTransform._3,
                yaw = decomposedTransform._2,
                pitch = decomposedTransform._1
            ),
            momo = momoInstance,
        )

        if (debug) {
            println(initialParameters.toJson.prettyPrint)
        }

        val initialSample = Sample("initial", initialParameters, Point(0, 0, 0))
        val chain = MetropolisHastings(generator, posteriorEvaluator)
        val logger = new ShapeSamplingLogger(logToConsole = false)
        val mhIterator = chain.iterator(initialSample, logger)
        val samplingIterator = for ((sample, iteration) <- mhIterator.zipWithIndex) yield {
            if (iteration % 100 == 0) {
                println(s"Iteration #$iteration")
                modelView.shapeModelTransformationView.shapeTransformationView.coefficients = sample.parameters.modelCoefficients
                modelView.shapeModelTransformationView.poseTransformationView.transformation = sample.poseTransformation
            }
            sample
        }

        val samples = samplingIterator.slice(50, 500).toIndexedSeq
        if (debug) {
            println(s"acceptanceRatios = ${logger.acceptanceRatios()}")
        }
        val bestSample = samples.maxBy(posteriorEvaluator.logValue)
        val bestFit: TriangleMesh[_3D] = transformedModel
            .instance(bestSample.parameters.modelCoefficients)
            .transform(bestSample.poseTransformation)
        val pipeEnd = Calendar.getInstance().getTimeInMillis
        modelGroup.remove()

        val resultGroup = ui.createGroup("result")
        if (debug) {
            println(s"Best Sample: ${bestSample.rps.toJson.prettyPrint}")
        }
        val ppx = -310.522 / 640.0 * 2.0 + 1.0
        val ppy = -232.034 / 480.0 * 2.0 + 1.0
        val focalLength = 2.4 / 640 * 604

        val bestRenderParameters = bestSample.rps.copy(
            view = ViewParameter(EuclideanVector3D.zero, 0, Math.PI, Math.PI),
            camera = Camera(focalLength, Point2D(ppx, ppy), EuclideanVector(2.4, 1.8), 1, 10000, orthographic = false),
            momo = MoMoInstance(
                shape = bestSample.rps.momo.shape,
                color = IndexedSeq.fill(50)(0.0),
                expression = IndexedSeq.fill(5)(0.0),
                modelURI = bestSample.rps.momo.modelURI
            ),
            imageSize = ImageSize(targetImageRGBA.width, targetImageRGBA.height)
        )
        if (debug) {
            val result = ui.show(resultGroup, bestFit, "best fit instance")
            val scalarMeshField = colorDistance(bestFit, targetMesh)
            ui.show(scalarMeshField, "scalar mesh field")
            println(s"Best shape fitting render parameters: ${bestRenderParameters.toJson.prettyPrint}")
            MeshIO.writeMesh(bestFit, new File(s"output/fits/best-SF_$saveTime.ply"))
            RenderParameterIO.write(bestRenderParameters, new File(s"output/params/best-SF_$saveTime.rps"))
        }
        val bestFitLms: Seq[Landmark[_3D]] = momoLmsSorted.map(
            lm => Landmark(lm.id, bestRenderParameters.pose.transform.apply(lm.point), lm.description, lm.uncertainty)
        )
        val fos: FileOutputStream = new FileOutputStream(new File(s"dataset/results/stats/stats_$saveTime.txt"))
        if (statistics) {
            // Computing average distances between target mesh and best fit
            Console.withOut(fos) {
                println(s"- custom average distance, bestFit -> target = ${customAVGDistance(bestFit, targetMesh)}")
                println(s"- custom hausdorff distance target(ignores boundary points) -> bestFit = ${hausdorffDistance(bestFit, targetMesh)}")
            }
            val gtMesh = MeshIO.readMesh(new File("data/neutralMe.ply")).get
            val gtLandmarks = LandmarkIO.readLandmarksJson[_3D](new File("data/gtLandmarks.json")).get

            // Patrick
            //val gtMesh = MeshIO.readMesh(new File("data/patrick_neutral.ply")).get
            //val gtLandmarks = LandmarkIO.readLandmarksJson[_3D](new File("data/gtLandmarksPatrick.json")).get

            // Dana
            //val gtMesh = MeshIO.readMesh(new File("data/dana_neutral.ply")).get
            //val gtLandmarks = LandmarkIO.readLandmarksJson[_3D](new File("data/gtLandmarksDana.json")).get

            Console.withOut(fos) {
                println("\n> aligning best fit and ground truth...")
                alignMeshesICP(bestFit, bestFitLms, gtMesh, gtLandmarks, fos)

            }

            val shapeFittingEnd = System.currentTimeMillis()
            val t = shapeFittingEnd - shapeFittingStart
            Console.withOut(fos) {
                println(s"Elapsed time: $t ms -> ${t / 60000}m")
            }
        }


        (targetMesh, bestRenderParameters, bestFit, target3DLM, fos, momoLmsSorted, bestFitLms)
    }
}

