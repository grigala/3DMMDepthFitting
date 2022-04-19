package ch.unibas.cs.gravis.thriftservice.rendering

import scalismo.faces.image.{ColumnMajorImageDomain, ImageBuffer, PixelImage, RowMajorImageDomain}
import scalismo.faces.render.RenderBuffer

import scala.reflect.ClassTag

case class ZBuffer[A: ClassTag](override val width: Int, override val height: Int, background: A, zInit: Double = Double.PositiveInfinity) extends RenderBuffer[A] {
    private val buffer = ImageBuffer.makeConstantBuffer(width, height, background)
    private val zBuffer = ImageBuffer.makeConstantBuffer(width, height, zInit)

    override def update(x: Int, y: Int, z: Double, v: A): Unit = {
        if (isDefinedAt(x, y)) {
            if (z < zBuffer(x, y)) {
                buffer(x, y) = v
                zBuffer(x, y) = z
            }
        }
    }

    override def toImage: PixelImage[A] = buffer.toImage

    def zBufferToDepthImage(zNear: Double, zFar: Double): PixelImage[Option[Double]] = {

        val image: PixelImage[Double] = zBuffer.toImage
        // inverse zBuffer, obtains actual z coord
        val depthImage = image.map(f => if (f == zInit) {
            None
        } else Some(2.0 * zFar * zNear / (zFar + zNear - (zFar - zNear) * (2.0 * f - 1.0))))

        val di: PixelImage[Option[Double]] = depthImage.domain match {
            case cm: ColumnMajorImageDomain => depthImage.withDomain(RowMajorImageDomain(cm.width, cm.height))
            case _ => depthImage
        }
        di
    }


    override def isDefinedAt(x: Int, y: Int): Boolean = x >= 0 && x < width && y >= 0 && y < height
}
