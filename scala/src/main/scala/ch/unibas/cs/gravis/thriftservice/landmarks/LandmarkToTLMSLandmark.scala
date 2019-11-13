package ch.unibas.cs.gravis.thriftservice.landmarks

import java.io.{File, FileOutputStream, OutputStream, PrintWriter}

import scala.util.Try

object LandmarkToTLMSLandmark {
    /**
     * Writes [[scalismo.geometry.Landmark]] to TLMS file.
     * Visibility is set to 1, since this concept is not known by the Landmark
     *
     * @param file      file to write in
     * @param landmarks landmarks to write
     * @tparam D Dimension of landmarks
     * @return
     */
    def writeLandmarksTLMS[D <: Dim : NDSpace](file: File, landmarks: Seq[Landmark[D]]): Try[Unit] = {
        writeLandmarksTLMSToStream[D](new FileOutputStream(file), landmarks)
    }

    def writeLandmarksTLMSToStream[D <: Dim : NDSpace](stream: OutputStream, landmarks: Seq[Landmark[D]]): Try[Unit] = {
        val writer = new PrintWriter(stream, true)
        val visibility = 1
        val result = Try {
            val seq: Seq[String] = landmarks.map(lm => lm.id + " " + visibility + " " + lm.point.toArray.mkString(" "))
            writer.write(seq.mkString("\n"))
        }
        Try {
            writer.close()
        }
        result
    }
}
