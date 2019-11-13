package ch.unibas.cs.gravis.thriftservice.sampling.evaluators

trait LandmarksRendererEvaluator[D <: Dim] {
    def logValue(rps: RenderParameter): Double
}
