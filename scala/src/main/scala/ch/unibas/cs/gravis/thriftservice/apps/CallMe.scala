package ch.unibas.cs.gravis.thriftservice.apps

import java.io.File

import ch.unibas.cs.gravis.realsense.RealSenseService
import com.twitter.finagle.Thrift
import com.twitter.util.{Await, Future}
import scalismo.color.RGB
import scalismo.faces.image.PixelImage
import scalismo.faces.landmarks.TLMSLandmark2D
import scalismo.geometry.{Landmark, _3D}
import scalismo.mesh.TriangleMesh
import ch.unibas.cs.gravis.thriftservice.utils.ThriftConversions._
import scalismo.faces.io.MoMoIO
import scalismo.faces.momo.MoMo
import scalismo.faces.parameters.RenderParameter

/**
 * This object is written solely for web-service call.
 * It performs the shape fitting only and returns an image and best render parameters
 */
object CallMe {

    def resultForWeb(): (PixelImage[RGB], RenderParameter) = {
        scalismo.initialize()
        println("[CallMe] Ok, got a new shape fitting request, calling python server...")
        val client = Thrift.client.withBufferedTransport.newIface[RealSenseService[Future]]("127.0.0.1:9000")
        val captureFuture = client.capture()
        val captureResult = Await.result(captureFuture)
        val image: PixelImage[RGB] = thriftImageToPixelImageRGB(captureResult.image)
        val landmarks2D: Seq[TLMSLandmark2D] = thriftLandmark2DListToTLMSLandmark2D(captureResult.landmarks2d)
        val landmarks: Seq[Landmark[_3D]] = thriftLandmarkListToLandmarkList(captureResult.landmarks)
        val targetTriangleMesh: TriangleMesh[_3D] = thriftVertexColorMeshToVertexColorMesh(captureResult.mesh)

        val dataDir = "data/"
        val modelName = "augmentedModelFace.h5"
        val modelFile = new File(dataDir + modelName)
        val model: MoMo = MoMoIO.read(modelFile).get

        val targetLandmarks: Seq[Landmark[_3D]] = landmarks.filter(lm => !lm.point.toArray.contains(0.0) || (lm.point.z < 1000 && lm.point.z != 0.0))
        val targetLmsSorted: Seq[Landmark[_3D]] = targetLandmarks.sortBy(lm => lm.id)

        /* Shape Fitting */
        println("[CallMe] Starting shape fitting...")
        val (_, bestRenderParameters, _, _, _, _, _) = ShapeFitting.fitShape(
            modelFile,
            image,
            targetLmsSorted,
            targetTriangleMesh,
            false,
            false
        )

        (image, bestRenderParameters)
    }

}
