package ch.unibas.cs.gravis.thriftservice.rendering

trait ParametricLandmarksRenderer3D {

    def renderLandmark3D(lmId: String, parameter: RenderParameter): Option[Landmark[_3D]]

    /** check if landmark id is known */
    def hasLandmarkId(lmId: String): Boolean

    /** list of all known landmarks */
    def allLandmarkIds: IndexedSeq[String]
}
