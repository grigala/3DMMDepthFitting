package ch.unibas.cs.gravis.thriftservice.utils

import java.lang.Math.{max, min}
import java.util.Base64
import breeze.linalg.{DenseMatrix, DenseVector}
import ch.unibas.cs.gravis.realsense._
import scalismo.color.RGB
import scalismo.common.{PointId, UnstructuredPointsDomain}
import scalismo.faces.image.{AccessMode, PixelImage, PixelImageDomain}
import scalismo.faces.landmarks.TLMSLandmark2D
import scalismo.geometry._
import scalismo.mesh.{TriangleCell, TriangleList, TriangleMesh, TriangleMesh3D}
import scalismo.statisticalmodel.MultivariateNormalDistribution

import scala.language.implicitConversions

object ThriftConversions {

    /**
     * Convert ThriftImage to PixelImage[RGB]
     *
     * @param image ThriftImage
     * @return PixelImage[RGB]
     */
    implicit def thriftImageToPixelImageRGB(image: ThriftImage): PixelImage[RGB] = {
        val decoder = Base64.getDecoder
        val data = decoder.decode(image.data)

        def getPixel(x: Int, y: Int): RGB = {
            val index = 3 * image.width * y + 3 * x
            new RGB(data(index).sByteToDouble / 255.0, data(index + 1).sByteToDouble / 255.0, data(index + 2).sByteToDouble / 255.0)
        }

        val domain = PixelImageDomain(image.width, image.height)
        PixelImage(domain, getPixel(_, _)).withAccessMode(AccessMode.Repeat())
    }

    /**
     * Converts RGB image to ThriftImage
     *
     * @param image RGB image
     * @return ThriftImage
     */
    implicit def pixelImageRGBToThriftImage(image: PixelImage[RGB]): ThriftImage = {
        val width = image.width
        val height = image.height
        val data =
            for (y <- 0 until height) yield {
                for (x <- 0 until width) yield {
                    Seq((image(x, y).r * 255).toSByte, (image(x, y).g * 255).toSByte, (image(x, y).b * 255).toSByte)
                }
            }
        val byteArr: Array[Byte] = data.flatten.flatten.toArray

        val encoder = Base64.getEncoder
        val str = encoder.encodeToString(byteArr)

        ThriftImage(str, width, height)
    }

    /**
     * Converts ThriftPixel to Point2D
     *
     * @param thriftPixel ThriftPixel
     * @return Point2D
     */
    implicit def thriftPixelToPoint2D(thriftPixel: ThriftPixel): Point2D = {
        Point2D(thriftPixel.width, thriftPixel.height)
    }

    /**
     * Converts ThriftPoint3D to Point3D
     *
     * @param thriftPoint ThriftPoint3D
     * @return Point3D
     */
    implicit def thriftPoint3DToPoint3D(thriftPoint: ThriftPoint3D): Point3D = {
        Point3D(thriftPoint.x, thriftPoint.y, thriftPoint.z)
    }

    /**
     * Converts Point3D to ThriftPoint3D
     *
     * @param point Point3D
     * @return ThriftPoint3D
     */
    implicit def point3DToThriftPoint3D(point: Point3D): ThriftPoint3D = {
        ThriftPoint3D(point.x, point.y, point.z)
    }

    /**
     * Converts ThriftVector3D to EuclideanVector3D
     *
     * @param thriftVector ThriftVector3D
     * @return EuclideanVector3D
     */
    implicit def thriftVector3DToVector3D(thriftVector: ThriftVector3D): EuclideanVector3D = {
        EuclideanVector3D(thriftVector.x, thriftVector.y, thriftVector.z)
    }

    /**
     * Converts EuclideanVector3D to ThriftVector3D
     *
     * @param vector EuclideanVector3D
     * @return ThriftVector3D
     */
    implicit def vector3DToThriftVector3D(vector: EuclideanVector3D): ThriftVector3D = {
        ThriftVector3D(vector.x, vector.y, vector.z)
    }

    /**
     * Converts ThriftLandmark aka 3D Landmark to Landmark[_3D]
     *
     * @param thriftLandmark ThriftLandmark
     * @return Landmark[_3D] with no description
     */
    implicit def thriftLandmarkToLandmark(thriftLandmark: ThriftLandmark): Landmark[_3D] = {

        val name = thriftLandmark.name
        val point: Point3D = thriftLandmark.point
        val otherUncertainty = MultivariateNormalDistribution(
            DenseVector.zeros[Double](3),
            DenseMatrix.eye[Double](3)
        )
        val chinUncertainty = MultivariateNormalDistribution(
            DenseVector.zeros[Double](3),
            DenseMatrix.eye[Double](3) * 4.0
        )
        if (name.contains("chin")) {
            Landmark(name, point, None, Some(chinUncertainty))
        } else {
            Landmark(name, point, None, Some(otherUncertainty))
        }
        //        Landmark(name, point, None, None)
    }

    /**
     * Converts ThriftLandmark2D to TLMSLandmark2D
     *
     * @param thriftLandmark ThriftLandmark2D
     * @return TLMSLandmark2D visibility set to True
     */
    implicit def thriftLandmark2DToTLMSLandmark2D(thriftLandmark: ThriftLandmark2D): TLMSLandmark2D = {
        val name = thriftLandmark.name
        val pixels: Point2D = thriftLandmark.pixels
        // Visibility is always set to 1
        val visibility: Boolean = true

        TLMSLandmark2D(name, pixels, visibility)
    }

    /**
     * Converts Seq[ThriftLandmark2D] to Seq[TLMSLandmark2D]
     *
     * @param thriftLandmark2DList Seq[ThriftLandmark2D]
     * @return Seq[TLMSLandmark2D]
     */
    implicit def thriftLandmark2DListToTLMSLandmark2D(thriftLandmark2DList: Option[Seq[ThriftLandmark2D]]): Seq[TLMSLandmark2D] = {
        thriftLandmark2DList.get.map(thriftLandmark2DToTLMSLandmark2D)
    }

    /**
     * Converts Seq[ThriftLandmark](3D) to Seq[Landmark[_3D] ]
     *
     * @param thriftLandmarkList Seq[ThriftLandmark]
     * @return Seq[Landmark[_3D]
     */
    implicit def thriftLandmarkListToLandmarkList(thriftLandmarkList: Seq[ThriftLandmark]): Seq[Landmark[_3D]] = {
        thriftLandmarkList.map(thriftLandmarkToLandmark)
    }

    /**
     * Converts Seq[ThriftPoint3D] to Seq[Point3D]
     *
     * @param thriftPointList Seq[ThriftPoint3D]
     * @return Seq[Point3D]
     */
    implicit def thriftPoint3DListToPoint3DList(thriftPointList: Seq[ThriftPoint3D]): Seq[Point3D] = thriftPointList.map(thriftPoint3DToPoint3D)

    /**
     * Converts ThriftColor to RGB
     *
     * @param thriftColor ThriftColor
     * @return RGB
     */
    implicit def thriftColorToColor(thriftColor: ThriftColor): RGB = {
        RGB(thriftColor.r, thriftColor.g, thriftColor.b)
    }

    /**
     * Converts Seq[ThriftColor] to Seq[RGB]
     *
     * @param thriftColorList Seq[ThriftColor]
     * @return Seq[RGB]
     */
    implicit def thriftColorListToColorList(thriftColorList: Seq[ThriftColor]): Seq[RGB] = thriftColorList.map(thriftColorToColor)

    /**
     * Converts ThriftTriangleCell to TriangleCell
     *
     * @param thriftTriangleCell ThriftTriangleCell
     * @return TriangleCell
     */
    implicit def thriftTriangleCellToTriangleCell(thriftTriangleCell: ThriftTriangleCell): TriangleCell = {
        TriangleCell(PointId(thriftTriangleCell.id1), PointId(thriftTriangleCell.id2), PointId(thriftTriangleCell.id3))
    }

    /**
     * Converts Seq[ThriftTriangleCell] to Seq[TriangleCell]
     *
     * @param thriftTriangleCellList Seq[ThriftTriangleCel]
     * @return Seq[TriangleCell]
     */
    implicit def thriftTriangleCellListToTriangleCellList(thriftTriangleCellList: Seq[ThriftTriangleCell]): Seq[TriangleCell] = thriftTriangleCellList.map(thriftTriangleCellToTriangleCell)

    /**
     * Converts ThriftVertexColorMesh to TriangleMes[_3D]
     * This conversion could be modified to receive color information from the mesh itself.
     * However since we are aiming to get it from the color image we are just converting it
     * to TriangleMesh[_3D] as opposed to VertexColorMesh3D
     *
     * @param thriftMesh ThriftVertexColorMesh to TriangleMesh[_3D]
     * @return TriangleMesh[_3D]
     */
    implicit def thriftVertexColorMeshToVertexColorMesh(thriftMesh: ThriftVertexColorMesh): TriangleMesh[_3D] = {
        val points = thriftPoint3DListToPoint3DList(thriftMesh.vertices)
        val faces = thriftTriangleCellListToTriangleCellList(thriftMesh.faces)
        val mesh = TriangleMesh3D(UnstructuredPointsDomain(points.toIndexedSeq), TriangleList(faces.toIndexedSeq))
        mesh
    }

    /**
     * Scala (and Java) only has signed bytes
     *
     * @param d Double to convert to signed byte representing unsigned value
     */
    implicit class DoubleConverter(d: Double) {
        def toSByte: Byte = {
            val cut = max(min(255.0, d), 0.0).toInt // clipping
            if (cut > 127) {
                (cut - 256).toByte
            } else {
                cut.toByte
            }
        } // avoiding over/under-flows
    }

    /**
     * Scala (and Java) only has signed bytes
     *
     * @param b signed byte to convert to unsigned value
     */
    implicit class SByteConverter(b: Byte) {
        def sByteToDouble: Double = {
            val asInt = b.toInt
            if (asInt < 0) {
                256 + asInt
            } else {
                asInt
            }
        }
    }

}
