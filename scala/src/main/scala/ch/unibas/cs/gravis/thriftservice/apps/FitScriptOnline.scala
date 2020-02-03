package ch.unibas.cs.gravis.thriftservice.apps

import java.io.File
import java.text.{DateFormat, SimpleDateFormat}
import java.util.Calendar

import ch.unibas.cs.gravis.realsense.RealSenseService
import ch.unibas.cs.gravis.thriftservice.utils.Helpers._
import ch.unibas.cs.gravis.thriftservice.utils.ThriftConversions._
import ch.unibas.cs.gravis.thriftservice.utils.Utils._
import com.twitter.finagle.Thrift
import com.twitter.util.{Await, Future}
import scalismo.color.{RGB, RGBA}
import scalismo.faces.image.PixelImage
import scalismo.faces.io.{MoMoIO, PixelImageIO}
import scalismo.faces.landmarks.TLMSLandmark2D
import scalismo.faces.momo.MoMo
import scalismo.faces.parameters._
import scalismo.faces.sampling.face.MoMoRenderer
import scalismo.geometry._
import scalismo.io.{LandmarkIO, MeshIO}
import scalismo.mesh.{TriangleMesh, VertexColorMesh3D}


/**
 * In order to run this script camera server is required to be running.
 * For offline work(without camera server) refer to FitScriptOffline file.
 */
object FitScriptOnline extends App {

    scalismo.initialize()
    val DEBUG = false
    val STATS = false
    val dateFormat: DateFormat = new SimpleDateFormat("HH-mm-ss, MMM dd")
    val date = Calendar.getInstance().getTime
    val saveTime = dateFormat.format(date)

    /** ************************************ Server Stuff ********************************/
    val requestStart = Calendar.getInstance().getTimeInMillis
    val client = Thrift.client.withBufferedTransport.newIface[RealSenseService[Future]]("127.0.0.1:9000")
    val captureFuture = client.capture(true)
    val captureResult = Await.result(captureFuture)
    val requestEnd = Calendar.getInstance().getTimeInMillis
    println(s"Request-response took: ${requestEnd - requestStart}ms")

    val pipeStart = Calendar.getInstance().getTimeInMillis
    val image: PixelImage[RGB] = thriftImageToPixelImageRGB(captureResult.image)
    val landmarks2D: Seq[TLMSLandmark2D] = thriftLandmark2DListToTLMSLandmark2D(captureResult.landmarks2d)
    val landmarks: Seq[Landmark[_3D]] = thriftLandmarkListToLandmarkList(captureResult.landmarks)
    val targetTriangleMesh: TriangleMesh[_3D] = thriftVertexColorMeshToVertexColorMesh(captureResult.mesh)

    // Saving data
    PixelImageIO.write(image, new File(s"targetData/gen/images/targetImage_$saveTime.png"))
    LandmarkIO.writeLandmarksJson(landmarks, new File(s"targetData/gen/landmarks/target3DLandmarks_$saveTime.json"))

    /** ************************************* Scalismo ********************************/
    val dataDir = "data/"
    val modelName = "augmentedModelFace.h5"
    val modelFile = new File(dataDir + modelName)
    val model: MoMo = MoMoIO.read(modelFile).get
    val renderer: MoMoRenderer = MoMoRenderer(model, RGBA.BlackTransparent).cached(50)

    val targetLandmarks: Seq[Landmark[_3D]] = landmarks.filter(lm => !lm.point.toArray.contains(0.0) || (lm.point.z < 1000 && lm.point.z != 0.0))
    val targetLmsSorted: Seq[Landmark[_3D]] = targetLandmarks.sortBy(lm => lm.id)

    /* Shape Fitting */
    val (targetMesh, bestRenderParameters, shapeFit, target3DLms, momoLmsSorted, bestFitLms) = ShapeFitting.fitShape(
        modelFile,
        image,
        targetLmsSorted,
        targetTriangleMesh,
        DEBUG,
        STATS,
        numIterations = 1000
    )
    /* Color Fitting */
    val colorFitting: RenderParameter = ColorFitting.fitColor(
        target = image,
        target3DLms = target3DLms,
        bestFit = shapeFit,
        targetMesh = targetMesh,
        initRPS = bestRenderParameters,
        outputDir = "logging/",
        modelFile = modelFile,
        guiEnabled = true,
        numIterations = 10000
    )

    if (STATS) {
        val finalFit: VertexColorMesh3D = renderer.renderMesh(colorFitting)
        val finalFitLandmarks: Seq[Landmark[_3D]] = momoLmsSorted.map(
            lm => Landmark(lm.id, colorFitting.pose.transform.apply(lm.point), lm.description, lm.uncertainty)
        )

        val gtMesh = MeshIO.readMesh(new File("data/neutralMe.ply")).get
        val gtLandmarks = LandmarkIO.readLandmarksJson[_3D](new File("data/gtLandmarks.json")).get

//        Console.withOut(fos) {
//            println("\n> ===final best fit and ground truth===")
//            alignMeshesICP(finalFit.shape, finalFitLandmarks, gtMesh, gtLandmarks, fos)
//            println("\n> ===final best fit and target mesh===")
//            //            alignMeshesICP(finalFit.shape, finalFitLandmarks, targetMesh, best, fos)
//        }
    }
}
