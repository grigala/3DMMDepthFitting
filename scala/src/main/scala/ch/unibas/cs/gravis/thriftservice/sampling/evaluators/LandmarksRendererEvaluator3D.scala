package ch.unibas.cs.gravis.thriftservice.sampling.evaluators

import ch.unibas.cs.gravis.thriftservice.rendering.ParametricLandmarksRenderer3D

class LandmarksRendererEvaluator3D(landmarks: Set[String],
                                   renderer: ParametricLandmarksRenderer3D,
                                   lmEvaluator: DistributionEvaluator[Map[String, Landmark[_3D]]])
    extends DistributionEvaluator[RenderParameter] with LandmarksRendererEvaluator[_3D] {
    override def logValue(rps: RenderParameter): Double = {
        val renderedLm = for {
            id <- landmarks
            lm <- renderer.renderLandmark3D(id, rps)
        } yield id -> lm
        lmEvaluator.logValue(renderedLm.toMap)
    }

    override def toString = "LandmarksRendererEvaluator3D(" + lmEvaluator.toString + ")"
}

object LandmarksRendererEvaluator3D {
    def apply[D <: Dim](landmarks: Set[String],
                        renderer: ParametricLandmarksRenderer3D,
                        lmEvaluator: DistributionEvaluator[Map[String, Landmark[_3D]]]) = new LandmarksRendererEvaluator3D(landmarks, renderer, lmEvaluator)

}
