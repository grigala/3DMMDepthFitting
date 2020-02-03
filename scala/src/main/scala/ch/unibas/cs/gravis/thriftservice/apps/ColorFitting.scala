package ch.unibas.cs.gravis.thriftservice.apps

import java.io.File
import java.text.{DateFormat, SimpleDateFormat}
import java.util.{Calendar, Date}

import breeze.linalg.{DenseMatrix, DenseVector}
import ch.unibas.cs.gravis.thriftservice.logging.ImageSamplingLogger
import ch.unibas.cs.gravis.thriftservice.rendering.AugmentedMoMoRenderer
import ch.unibas.cs.gravis.thriftservice.sampling.evaluators.{CauchyMoMoShapeEvaluator, IndependentLandmarksEvaluator, LandmarksRendererEvaluator3D}
import ch.unibas.cs.gravis.thriftservice.utils.Helpers._
import javax.swing.JLabel
import org.apache.commons.math3.distribution.CauchyDistribution
import scalismo.color.{RGB, RGBA}
import scalismo.faces.deluminate.SphericalHarmonicsOptimizer
import scalismo.faces.gui.ImagePanel
import scalismo.faces.image.PixelImage
import scalismo.faces.io.renderparameters.RenderParameterJSONFormat._
import scalismo.faces.io.{MoMoIO, PixelImageIO, RenderParameterIO}
import scalismo.faces.mesh.MeshSurfaceSampling
import scalismo.faces.momo.MoMo
import scalismo.faces.parameters._
import scalismo.faces.sampling.face.evaluators.PixelEvaluators.IsotropicGaussianPixelEvaluator
import scalismo.faces.sampling.face.evaluators.PointEvaluators.IsotropicGaussianPointEvaluator
import scalismo.faces.sampling.face.evaluators.PriorEvaluators.{GaussianShapePrior, GaussianTexturePrior}
import scalismo.faces.sampling.face.evaluators.{CollectiveLikelihoodEvaluator, HistogramRGB, ImageRendererEvaluator, IndependentPixelEvaluator, PixelEvaluators}
import scalismo.faces.sampling.face.loggers._
import scalismo.faces.sampling.face.proposals.SphericalHarmonicsLightProposals._
import scalismo.faces.sampling.face.proposals._
import scalismo.faces.sampling.face.{ParametricLandmarksRenderer, ParametricModel}
import scalismo.geometry._
import scalismo.mesh.TriangleMesh
import scalismo.sampling.algorithms.MetropolisHastings
import scalismo.sampling.evaluators.ProductEvaluator
import scalismo.sampling.loggers.{BestSampleLogger, ChainStateLogger, ChainStateLoggerContainer}
import scalismo.sampling.proposals.{MetropolisFilterProposal, MixtureProposal}
import scalismo.sampling.{ProposalGenerator, TransitionProbability}
import scalismo.statisticalmodel.MultivariateNormalDistribution
import scalismo.utils.Random
import spray.json._

/* ===implicits==*/
import scalismo.faces.gui.GUIBlock._
import scalismo.faces.sampling.evaluators.CachedDistributionEvaluator.implicits._
import scalismo.faces.sampling.face.proposals.ImageCenteredProposal.implicits._
import scalismo.faces.sampling.face.proposals.ParameterProposals.implicits._
import scalismo.sampling.loggers.ChainStateLogger.implicits._
import scalismo.sampling.proposals.MixtureProposal.implicits._


object ColorFitting {
    implicit val rnd: Random = Random(1024L)

    scalismo.initialize()

    val DEBUG: Boolean = false
    val dateFormat: DateFormat = new SimpleDateFormat("HH-mm-ss, MMM dd")
    val date: Date = Calendar.getInstance().getTime
    val saveTime: String = dateFormat.format(date)

    def defaultPoseProposal(lmRenderer: ParametricLandmarksRenderer)(implicit rnd: Random):
    ProposalGenerator[RenderParameter] with TransitionProbability[RenderParameter] = {
        val yawProposalF = GaussianRotationProposal(EuclideanVector3D.unitY, 0.01f)
        val rotationYaw = MixtureProposal(yawProposalF)

        val pitchProposalF = GaussianRotationProposal(EuclideanVector3D.unitX, 0.01f)
        val rotationPitch = MixtureProposal(pitchProposalF)

        val rollProposalF = GaussianRotationProposal(EuclideanVector3D.unitZ, 0.01f)
        val rotationRoll = MixtureProposal(rollProposalF)

        val rotationProposal = MixtureProposal(0.5 *: rotationYaw + 0.3 *: rotationPitch + 0.2 *: rotationRoll).toParameterProposal

        val translationHF = GaussianTranslationProposal(EuclideanVector(5f, 5f)).toParameterProposal
        val translationProposal = MixtureProposal(translationHF)

        val poseMovingNoTransProposal = MixtureProposal(rotationProposal)
        val centerREyeProposal = poseMovingNoTransProposal.centeredAt("right.eye.corner_outer", lmRenderer).get
        val centerLEyeProposal = poseMovingNoTransProposal.centeredAt("left.eye.corner_outer", lmRenderer).get
        val centerRLipsProposal = poseMovingNoTransProposal.centeredAt("right.lips.corner", lmRenderer).get
        val centerLLipsProposal = poseMovingNoTransProposal.centeredAt("left.lips.corner", lmRenderer).get

//        MixtureProposal(rotationProposal + translationProposal)
        MixtureProposal(centerREyeProposal + centerLEyeProposal + centerRLipsProposal + centerLLipsProposal + translationProposal)
    }

    /* Collection of all statistical model (shape, texture) related proposals */
    def neutralMorphableModelProposal(implicit rnd: Random):
    ProposalGenerator[RenderParameter] with TransitionProbability[RenderParameter] = {
        val shapeC = GaussianMoMoShapeProposal(0.5f)
        val shapeF = GaussianMoMoShapeProposal(0.1f)
        val shapeHF = GaussianMoMoShapeProposal(0.01f)
        val shapeScaleProposal = GaussianMoMoShapeCaricatureProposal(0.01f)
        val shapeProposal = MixtureProposal(
            0.5f *: shapeHF +
                    0.5f *: shapeScaleProposal
        ).toParameterProposal

        val textureC = GaussianMoMoColorProposal(0.2f)
        val textureF = GaussianMoMoColorProposal(0.1f)
        val textureHF = GaussianMoMoColorProposal(0.025f)
        val textureScale = GaussianMoMoColorCaricatureProposal(0.2f)
        val textureProposal = MixtureProposal(0.1f *: textureC + 0.5f *: textureF + 0.2 *: textureHF + 0.2f *: textureScale).toParameterProposal

        MixtureProposal(
            shapeProposal +
                    3f *: textureProposal
        )
    }

    /* Collection of all statistical model (shape, texture, expression) related proposals */
    def defaultMorphableModelProposal(implicit rnd: Random):
    ProposalGenerator[RenderParameter] with TransitionProbability[RenderParameter] = {

        val expressionF = GaussianMoMoExpressionProposal(0.1f)
        val expressionHF = GaussianMoMoExpressionProposal(0.025f)
        val expressionScaleProposal = GaussianMoMoExpressionCaricatureProposal(0.2f)
        val expressionProposal = MixtureProposal(0.5f *: expressionF + 0.2f *: expressionHF + 0.2f *: expressionScaleProposal).toParameterProposal

        MixtureProposal(neutralMorphableModelProposal + expressionProposal)
    }

    /* Collection of all color transform proposals */
    def defaultColorProposal(implicit rnd: Random):
    ProposalGenerator[RenderParameter] with TransitionProbability[RenderParameter] = {
        val colorC = GaussianColorProposal(RGB(0.01f, 0.01f, 0.01f), 0.01f, RGB(1e-4f, 1e-4f, 1e-4f))
        val colorF = GaussianColorProposal(RGB(0.001f, 0.001f, 0.001f), 0.01f, RGB(1e-4f, 1e-4f, 1e-4f))
        val colorHF = GaussianColorProposal(RGB(0.0005f, 0.0005f, 0.0005f), 0.01f, RGB(1e-4f, 1e-4f, 1e-4f))

        MixtureProposal(0.2f *: colorC + 0.6f *: colorF + 0.2f *: colorHF).toParameterProposal
    }

    /* Collection of all illumination related proposals */
    def defaultIlluminationProposal(modelRenderer: ParametricModel, target: PixelImage[RGBA])(implicit rnd: Random):
    ProposalGenerator[RenderParameter] with TransitionProbability[RenderParameter] = {
        val shOpt = SphericalHarmonicsOptimizer(modelRenderer, target)
        val shOptimizerProposal = SHLightSolverProposal(shOpt, MeshSurfaceSampling.sampleUniformlyOnSurface(100))

        val lightSHPert = SHLightPerturbationProposal(0.001f, fixIntensity = true)
        val lightSHIntensity = SHLightIntensityProposal(0.1f)
        val lightSHBandMixer = SHLightBandEnergyMixer(0.1f)
        val lightSHSpatial = SHLightSpatialPerturbation(0.05f)
        val lightSHColor = SHLightColorProposal(0.01f)

        MixtureProposal(
            (5f / 6f) *: MixtureProposal(lightSHSpatial + lightSHBandMixer + lightSHIntensity + lightSHPert + lightSHColor).toParameterProposal
                    + (1f / 6f) *: shOptimizerProposal
        )
    }

    /**
     * Color fitting script
     *
     * @param target     target image
     * @param bestFit    bets triangle mesh after shape fitting
     * @param targetMesh target triangle mesh
     * @param initRPS    initial RenderParameter
     * @param outputDir  directory where the result will be saved
     * @param modelFile  model file
     * @param guiEnabled GUI for logging
     */
    def fitColor(target: PixelImage[RGB],
                 target3DLms: Map[String, Landmark[_3D]],
                 bestFit: TriangleMesh[_3D],
                 targetMesh: TriangleMesh[_3D],
                 initRPS: RenderParameter,
                 outputDir: String,
                 modelFile: File,
                 guiEnabled: Boolean = false,
                numIterations: Int = 100
                ): RenderParameter = {

        val startColorFittingModule = System.currentTimeMillis()
        val outputFolder: File = new File(outputDir)
        if (!outputFolder.exists) outputFolder.mkdirs

        val targetRGBA: PixelImage[RGBA] = target.map(_.toRGBA)
        val imageScale: Double = 1.0
        val image: PixelImage[RGBA] = target.map(_.toRGBA)
        val targetImage: PixelImage[RGBA] = image.resample(
            (image.width * imageScale).toInt,
            (image.height * imageScale).toInt,
        )

        // Model stuff
        val expression = false
        val rank = 50
        val momoModelFull: MoMo = MoMoIO.read(modelFile).get
        val momoModel: MoMo = momoModelFull.neutralModel.truncate(rank, rank)
        val renderer: AugmentedMoMoRenderer = AugmentedMoMoRenderer(momoModel.neutralModel, RGBA.BlackTransparent).cached(10)

        val sdev: Double = 0.043
        val sdevmm: Double = 2.566f
        val cauchyUncertainty = new CauchyDistribution(0.0, 0.5)
        val uncertainty = MultivariateNormalDistribution(DenseVector.zeros[Double](3), DenseMatrix.eye[Double](3))
        val bestFitLandmarks: Seq[Landmark[_3D]] = momoModel.landmarks.values.toIndexedSeq.map(lm =>
            Landmark(lm.id, initRPS.pose.transform.apply(lm.point), lm.description, lm.uncertainty)
        )
        /* ===Evaluators=== */
        val texturePrior = GaussianTexturePrior(0.0, 1.0)
        val shapePrior = GaussianShapePrior(0.0, 1.0)
        val priorEvaluator = ProductEvaluator(texturePrior, shapePrior)

        val foregroundPixelEvaluator: IsotropicGaussianPixelEvaluator = IsotropicGaussianPixelEvaluator(sdev)
        val constantPixelEvaluator = PixelEvaluators.ConstantPixelEvaluator[RGB](4.68)
        val histogramBGEvaluator = HistogramRGB.fromImageRGBA(targetImage, 25)
        val pixelEvaluator = IndependentPixelEvaluator(foregroundPixelEvaluator, histogramBGEvaluator)
        val imageEvaluator = ImageRendererEvaluator(renderer, pixelEvaluator.toDistributionEvaluator(targetImage))
        val collectiveLikelihoodEval = CollectiveLikelihoodEvaluator(0.072, 9.0).toDistributionEvaluator(targetImage)
        val shapeEvaluator = CauchyMoMoShapeEvaluator(momoModel, targetMesh, cauchyUncertainty)
        //        val shapeEvaluator = MultiNormalMoMoShapeEvaluator(momoModel, bestFit, uncertainty)
        val pointEval = IsotropicGaussianPointEvaluator[_3D](sdevmm * 2)
        val lmListEvaluator = IndependentLandmarksEvaluator[_3D](pointEval)
        val landmarksEvaluator = LandmarksRendererEvaluator3D(target3DLms.keySet, renderer, lmListEvaluator.toDistributionEvaluator(target3DLms))
        val allEvaluators = ProductEvaluator(shapeEvaluator, imageEvaluator).cached(10)

        /* ===Proposals=== */
        val totalPose = defaultPoseProposal(renderer)
        val momoProposal = if (expression) defaultMorphableModelProposal else neutralMorphableModelProposal
        val colorProposal = defaultColorProposal
        val lightProposal = defaultIlluminationProposal(renderer, targetImage)
//        val fullFittingProposal = MetropolisFilterProposal(
//            MetropolisFilterProposal(
//                MixtureProposal(
//                    totalPose +
//                            momoProposal +
//                            2f *: colorProposal +
//                            2f *: lightProposal
//                ),
//                landmarksEvaluator
//            ),
//            priorEvaluator
//        )
        val fullFittingProposal = MetropolisFilterProposal(
            MetropolisFilterProposal(
                MixtureProposal(
                    totalPose
                ),
                landmarksEvaluator
            ),
            priorEvaluator
        )

        /* ===Logging=== */
        val imageLogger: ImageRenderLogger = ImageRenderLogger(renderer, new File(s"$outputFolder/img"), "img-").withBackground(targetImage)
        val bestRPSFileLogger: ParametersFileBestLogger = ParametersFileBestLogger(imageEvaluator, new File(s"$outputFolder/rps/best_$saveTime.rps"))
        val parametersFileLogger: ParametersFileLogger = ParametersFileLogger(new File(s"$outputFolder/rps"), "rps-iter-")
        val bestLogger: BestSampleLogger[RenderParameter] = BestSampleLogger(imageEvaluator)

        val initialParameters = initRPS.copy(
            imageSize = ImageSize(targetImage.width, targetImage.height),
        )

        val targetPanel = ImagePanel(targetImage)
        val imagePanel = ImagePanel(targetImage)
        val guiLabel = new JLabel("Image Fitting")
        if (guiEnabled) {
            stack(
                shelf(
                    stack(targetPanel, label("target image")),
                    stack(imagePanel, label("current sample")),
                ),
                guiLabel
            ).displayIn("GUI Logger")
        }

        val guiLogger: ImageSamplingLogger[RenderParameter] = ImageSamplingLogger[RenderParameter](guiLabel, logToConsole = false)
        var count = 0
        val renderLogger = new ChainStateLogger[RenderParameter] {
            override def logState(sample: RenderParameter): Unit = {
                val currentBestFit: PixelImage[RGBA] = renderer.renderImage(sample)
                val blended = overlayImages(targetImage, currentBestFit)
//                PixelImageIO.write[RGBA](blended, new File(s"output/img_$count.png"))
                count += 1
                imagePanel.updateImage(blended)
            }
        }
        val fullFittingLogger = ChainStateLoggerContainer(Seq(
            renderLogger.subSampled(500),
            bestLogger,
            //bestRPSFileLogger
        ))


        //        RenderParameterIO.write(initialParameters, new File(s"$outputFolder/initialParameters_$saveTime.rps")).get

        /* ===Metropolis Hastings=== */
        val imageFitter = MetropolisHastings(fullFittingProposal, allEvaluators)

        /** Full Fitting - Color, Light */
        if (DEBUG) {
            println(s"- Started full fitting with GUI logging: $guiEnabled")
        }

        val fullChainIterator = imageFitter.iterator(initialParameters, guiLogger)
        val fullFittingSamples = fullChainIterator.take(numIterations).loggedWith(fullFittingLogger).toIndexedSeq

        val bestSample: RenderParameter = bestLogger.currentBestSample().get
        // In case you work with low-res image let RPS know to render it back in original resolution
        val bestSampleUpscaled = bestSample.copy(
            imageSize = ImageSize(target.width, target.height)
        )
        val bestFitImage: PixelImage[RGBA] = overlayImages(targetRGBA, renderer.renderImage(bestSampleUpscaled))
        renderLogger.logState(bestSample)

        val finalImage = ImagePanel(bestFitImage)
        val tPanel = ImagePanel(targetRGBA)
        if (guiEnabled) {
            shelf(
                stack(tPanel, new JLabel("Target Image")),
                stack(finalImage, new JLabel("Final Result"))
            ).displayIn("Final Result")
        }

        if (DEBUG) {
            //        MeshIO.write(bestFitMesh, new File(s"$outputFolder/bestFitMesh.ply"))
            RenderParameterIO.write(bestSample, new File(s"$outputFolder/bestSample_$saveTime.rps"))
            PixelImageIO.write(bestFitImage, new File(s"$outputFolder/bestFits/bestFitBlended_$saveTime.png"))
            PixelImageIO.write(renderer.renderImage(bestSampleUpscaled), new File(s"$outputFolder/bestFits/bestFit_$saveTime.png"))

            println(bestSample.toJson.prettyPrint)
            println(s"= acceptanceRatios: ${guiLogger.acceptanceRatios()}")
            println("- Done.")
            val endColorFittingModule = System.currentTimeMillis()
            val t = endColorFittingModule - startColorFittingModule
            println(s"Elapsed time: $t ms -> ${t / 60000}m ")
        }

        bestSample
    }
}
