package ch.unibas.cs.gravis.thriftservice.rendering

import scalismo.faces.parameters.RenderParameter
import scalismo.geometry.{Landmark, _2D, _3D}

trait ParametricLandmarksRenderer3D {

    def renderLandmark3D(lmId: String, parameter: RenderParameter): Option[Landmark[_3D]]

    /** check if landmark id is known */
    def hasLandmarkId(lmId: String): Boolean

    /** list of all known landmarks */
    def allLandmarkIds: IndexedSeq[String]
}
