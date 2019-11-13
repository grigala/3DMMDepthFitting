package ch.unibas.cs.gravis.thriftservice.io

import java.awt.image.BufferedImage

/**
 * BufferedImageConverterScaledDouble can be used as a parameter to PixelImageIO.write
 * Maps Double values between min and max to values from 0 to 255. Ignores None values -> black
 * min and max passed either as parameters or using minimum and maximum value of the image.
 *
 * @param min
 * @param max
 */
class BufferedImageConverterScaledDoubleOption(var min: Double = (0.0), var max: Double = (0.0)) extends BufferedImageConverter[Option[Double]] {
    override def toBufferedImage(image: PixelImage[Option[Double]]): BufferedImage = {


        if (min == max) {
            min = image.values.flatten.min
            max = image.values.flatten.max
        }

        val range: Double = max - min
        println(s"${min} ${max} ${range}")

        def readIndexed8bitColor(d: Double): Int = {
            def toInt(f: Double): Int = {
                val fInRange: Double = (math.min(max, math.max(min, f)))
                val fScaled = (fInRange - min) / range * 255.0

                // White infront
                (255.0 - fScaled).toInt
            }

            val g = toInt(d)
            g
        }

        // construct BufferedImage
        val bufImg = new BufferedImage(image.width, image.height, BufferedImage.TYPE_BYTE_GRAY)


        for (
            x <- 0 until image.width;
            y <- 0 until image.height
        ) {
            image(x, y) match {
                case Some(d) => bufImg.getRaster.setPixel(x, y, Array(readIndexed8bitColor(d)))
                case _ =>
            }

        }
        bufImg
    }

    override def toPixelImage(bufferedImage: BufferedImage): PixelImage[Option[Double]] = ???
}
