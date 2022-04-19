package ch.unibas.cs.gravis.thriftservice.sampling.evaluators

import scalismo.faces.parameters.RenderParameter
import scalismo.geometry.Dim

trait LandmarksRendererEvaluator[D <: Dim] {
    def logValue(rps: RenderParameter): Double
}
