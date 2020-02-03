package ch.unibas.cs.gravis.thriftservice

import java.awt.Color
import java.io.{File, PrintWriter}

import ch.unibas.cs.gravis.realsense.{CaptureResult, RealSenseService}
import ch.unibas.cs.gravis.thriftservice.apps.{ColorFitting, ShapeFitting}
import ch.unibas.cs.gravis.thriftservice.rendering.{AugmentedMoMoRenderer, InjectExtrinsicParameters}
import ch.unibas.cs.gravis.thriftservice.server.DepthCameraServer
import ch.unibas.cs.gravis.thriftservice.utils.Helpers.overlayImages
import ch.unibas.cs.gravis.thriftservice.utils.ResourceDeZipper
import ch.unibas.cs.gravis.thriftservice.utils.ThriftConversions._
import com.twitter.finagle.Thrift
import com.twitter.logging.Logger
import com.twitter.util.{Await, Future}
import scalismo.color.{RGB, RGBA}
import scalismo.faces.gui.GUIBlock.{label, shelf, stack}
import scalismo.faces.gui.ImagePanel
import scalismo.faces.image.PixelImage
import scalismo.faces.io.MoMoIO
import scalismo.faces.landmarks.TLMSLandmark2D
import scalismo.faces.momo.MoMo
import scalismo.faces.parameters.RenderParameter
import scalismo.geometry.{Landmark, _3D}
import scalismo.ui.api.ScalismoUI
import scalismo.utils.Random

import scala.io.Source
import scala.reflect.io.Path

object DepthFittingService {

    private val serverDir: Path = new File("python").getAbsolutePath
    private val serverScript: File = new File(serverDir + File.separator + "Server.py")
    private val logger: Logger = Logger.get("depth-fitting-service")

    /**
     * Unpacks the server scripts based on file server-contents.txt
     *
     * @param name of the content file
     */
    private def unpackFiles(name: String = "server-contents.txt"): Unit = {
        logger.info("Unpacking python scripts...")
        val contents = Source.fromResource(name).getLines()
        val contentSeq = contents.toSeq
        val rdz = new ResourceDeZipper
        rdz.deZip(contentSeq, new File("./"))
    }


    /**
     * Saves the content as a list of resource files
     *
     * @param name    of the content file
     * @param logging prints the content lines(files)
     */
    private def createResourceContentFile(name: String = "server-contents.txt", logging: Boolean = false): Unit = {
        logger.info("Creating resource file...")
        val file = new File(s"src/main/resources/$name")
        file.getParentFile.mkdirs()
        file.createNewFile()
        val rdz = new ResourceDeZipper
        val content = rdz.listResourceContent("/python")
        val pw = new PrintWriter(file)
        if (logging) content.foreach(pw.println)
        pw.close()
    }


    def initialize(): Unit = {
        println("unpacking server content...")
        val contents = Source.fromResource("server-contents.txt").getLines()
        val contentsSeq = contents.toSeq
        val rdz = new ResourceDeZipper
        rdz.deZip(contentsSeq, new File("./"))
        //        val contentFile = new File("src/main/resources/server-contents.txt")
        //        if (contentFile.exists()) {
        //            logger.info("Resource file exists, unpacking files...")
        //            unpackFiles()
        //        } else {
        //            logger.info("Resource file does not exist, creating it...")
        //            createResourceContentFile(logging = true)
        //            logger.info("Unpacking files...")
        //            unpackFiles()
        //        }
    }

    /**
     * A method that starts the camera server
     *
     * @throws Exception if the server could not be started exception is being thrown
     * @return running DepthCameraServer instance
     */
    def getDepthCameraServer: DepthCameraServer = {
        val cameraServer = new DepthCameraServer(serverScript, "127.0.0.1", 9000)

        if (cameraServer.startCameraServer()) {
            logger.info("Camera server is alive...")
            cameraServer
        } else {
            logger.error("Camera server is not running...")
            throw new Exception("Could not start camera server, please check logs for more details...")
        }
    }

    /**
     * A method that sets up a client that receives the data from the server
     *
     * @return CaptureResult object containing an image, landmarks and a mesh
     */
    private def startClient(ip: String = "127.0.0.1", port: Int = 9000, gui: Boolean): CaptureResult = {
        val client = Thrift.client.withBufferedTransport.newIface[RealSenseService[Future]](ip + ":" + port)
        val captureFuture = client.capture(gui)
        val captureResult = Await.result(captureFuture)
        client.asClosable.close()
        captureResult
    }

    def startShapeFitting(ip: String = "127.0.0.1",
                          port: Int = 9000,
                          gui: Boolean = false,
                          shapeIterations: Int = 500,
                          colorIterations: Int = 0): (PixelImage[RGB], RenderParameter) = {

        scalismo.initialize()
        logger.info("Got a new shape fitting request, calling the camera server...")
        // TODO starting and keeping alive camera server
        //        val cameraServer = new DepthCameraServer(serverScript, "127.0.0.1", 9000)
        //        if (cameraServer.startCameraServer()) {
        //            logger.info("Camera server is alive...")
        //            cameraServer
        //        } else {
        //            logger.error("Camera server is not running...")
        //            throw new Exception("Could not start camera server. Please check logs for more details...")
        //        }
        val captureResult = startClient(ip, port, gui)
        logger.info("Data Received...")

        val image: PixelImage[RGB] = thriftImageToPixelImageRGB(captureResult.image)
        val landmarks2D: Seq[TLMSLandmark2D] = thriftLandmark2DListToTLMSLandmark2D(captureResult.landmarks2d)
        val landmarks3D: Seq[Landmark[_3D]] = thriftLandmarkListToLandmarkList(captureResult.landmarks)
        val targetTriangleMesh = thriftVertexColorMeshToVertexColorMesh(captureResult.mesh)

        val dataDir = "data/"
        val modelName = "augmentedModelFace.h5"
        val modelFile = new File(dataDir + modelName)
        val model: MoMo = MoMoIO.read(modelFile).get

        val targetLandmarks: Seq[Landmark[_3D]] = landmarks3D.filter(lm => !lm.point.toArray.contains(0.0) || (lm.point.z < 1000 && lm.point.z != 0.0))
        val targetLmsSorted: Seq[Landmark[_3D]] = targetLandmarks.sortBy(lm => lm.id)

        logger.info("Starting shape fitting...")

        val (targetMesh, bestRenderParameters, shapeFit, target3DLM, momoLmsSorted, bestFitLms) = ShapeFitting.fitShape(
            modelFile = modelFile,
            targetImage = image,
            target3DLandmarks = targetLmsSorted,
            targetPCMesh = targetTriangleMesh,
            debug = false,
            statistics = false,
            numIterations = shapeIterations
        )
        var rps: RenderParameter = ???
        logger.info("Performing a slight pose adjustment... This shouldn't take too much time...")
        if (colorIterations > 0) {
            val r = ColorFitting.fitColor(
                target = image,
                target3DLms = target3DLM,
                bestFit = shapeFit,
                targetMesh = targetMesh,
                initRPS = bestRenderParameters,
                outputDir = "logging/",
                modelFile = modelFile,
                guiEnabled = false,
                numIterations = colorIterations
            )
            rps = r
        } else {
            rps = bestRenderParameters
        }

        (image, rps)
    }
}
