package ch.unibas.cs.gravis.thriftservice.apps

import java.io.File
import java.text.{DateFormat, SimpleDateFormat}
import java.util.Calendar

import ch.unibas.cs.gravis.thriftservice.rendering.{AugmentedMoMoRenderer, InjectExtrinsicParameters}
import ch.unibas.cs.gravis.thriftservice.utils.Helpers._
import ch.unibas.cs.gravis.thriftservice.utils.Utils._
import scalismo.color.{RGB, RGBA}
import scalismo.faces.gui.GUIBlock.label
import scalismo.faces.gui.ImagePanel
import scalismo.faces.image.PixelImage
import scalismo.faces.io.{MoMoIO, PixelImageIO, RenderParameterIO}
import scalismo.faces.landmarks.{LandmarksDrawer, TLMSLandmark2D}
import scalismo.faces.momo.{MoMo, MoMoBasic}
import scalismo.faces.parameters._
import scalismo.faces.sampling.face.MoMoRenderer
import scalismo.geometry._
import scalismo.io.{LandmarkIO, MeshIO}
import scalismo.mesh.{TriangleMesh, VertexColorMesh3D}


/**
 * Working with local files - no need for the camera hardware.
 */
object FitScriptOffline extends App {
    scalismo.initialize()

    val DEBUG = false
    val LOG_STATS = false
    val dateFormat: DateFormat = new SimpleDateFormat("HH-mm-ss, MMM dd")
    val date = Calendar.getInstance().getTime

    val saveTime = dateFormat.format(date)
    val shapeFittingStart = System.currentTimeMillis()
    val targetName = "target"
    val imageScaling = 1.0
    val targetFull = PixelImageIO.read[RGB](new File(s"targetData/targetImage.png")).get
    val landmarksFull = LandmarkIO.readLandmarksJson[_3D](new File(s"targetData/target3DLandmarks.json")).get

    // Dealing with outlier landmark points thar are either empty or de-projected wrongly due to stream alignment
    // 1000 = 1m distance
    val targetLandmarks: Seq[Landmark[_3D]] = landmarksFull.filter(lm => !lm.point.toArray.contains(0.0) || (lm.point.z < 1000 && lm.point.z != 0.0))
    val targetLandmarkNames: Seq[String] = targetLandmarks.map(lm => lm.id)

    val targetPCMesh: TriangleMesh[_3D] = MeshIO.readMesh(new File(s"targetData/targetMesh.ply")).get
    val modelBFM = "augmentedBFM"
    val modelName = "augmentedModelFace"
    val modelFile = new File(s"data/$modelName.h5")

    val rank = 50
    val fullMoMoModel: MoMo = MoMoIO.read(modelFile).get
    val momoModel: MoMoBasic = fullMoMoModel.neutralModel.truncate(rank, rank)
    val renderer: AugmentedMoMoRenderer = AugmentedMoMoRenderer(momoModel.neutralModel, RGBA.BlackTransparent).cached(50)

    val targetLmsSorted: Seq[Landmark[_3D]] = targetLandmarks.sortBy(lm => lm.id)


    /* Shape Fitting */
    val (targetMesh, bestRenderParameters, shapeFit, target3DLM, momoLmsSorted, bestFitLms) = ShapeFitting.fitShape(
        modelFile,
        targetFull,
        targetLmsSorted,
        targetPCMesh,
        DEBUG,
        LOG_STATS,
        numIterations = 1000
    )

    val shapeFittingEnd = System.currentTimeMillis()
    val t = shapeFittingEnd - shapeFittingStart
    println(s"Elapsed time: $t ms -> ${t / 60000}m")

    val colorFitting = ColorFitting.fitColor(
        target = targetFull,
        target3DLms = target3DLM,
        bestFit = shapeFit,
        targetMesh = targetMesh,
        initRPS = bestRenderParameters,
        outputDir = "logging/",
        modelFile = modelFile,
        guiEnabled = true,
        numIterations = 10000
    )


    if (DEBUG) {
        val finalFit: VertexColorMesh3D = renderer.renderMesh(colorFitting)
        val finalFitLandmarks: Seq[Landmark[_3D]] = momoLmsSorted.map(
            lm => Landmark(lm.id, colorFitting.pose.transform.apply(lm.point), lm.description, lm.uncertainty)
        )
        val gtMesh = MeshIO.readMesh(new File("data/neutralMe.ply")).get
        val gtLandmarks = LandmarkIO.readLandmarksJson[_3D](new File("data/gtLandmarks.json")).get

       Console.withOut(fos) {
           println("\n> final best fit and target mesh...")
           alignMeshesICP(finalFit.shape, finalFitLandmarks, targetMesh, targetLmsSorted, fos)
           println("\n> aligning final best fit and ground truth...")
           alignMeshesICP(finalFit.shape, finalFitLandmarks, gtMesh, gtLandmarks, fos)
                  println(s"- custom average distance: ${customAVGDistance(finalFit.shape, targetMesh)}")
                  println(s"- custom hausdorff distance(ignores boundary points): ${hausdorffDistance(finalFit.shape, targetMesh)}")
       }

        val targetImageRGBA = targetFull.map(_.toRGBA)

        val finalParams = colorFitting.copy(imageSize = ImageSize(targetFull.width, targetImageRGBA.height))
        val blendedImage = overlayImages(targetImageRGBA, renderer.renderImage(finalParams))
        PixelImageIO.write(renderer.renderImage(finalParams), new File(s"dataset/results/bestFits/bestFit_$saveTime.png"))
        PixelImageIO.write(blendedImage, new File(s"dataset/results/bestFits/bestFitBlended_$saveTime.png"))
        RenderParameterIO.write(finalParams, new File(s"dataset/results/bestRPS/bestRPS_$saveTime.rps"))
    }

}
