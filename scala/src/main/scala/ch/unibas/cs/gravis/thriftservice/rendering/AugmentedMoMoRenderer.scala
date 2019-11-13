package ch.unibas.cs.gravis.thriftservice.rendering

import ch.unibas.cs.gravis.thriftservice.utils.Helpers

class AugmentedMoMoRenderer(model: MoMo, clearColor: RGBA) extends MoMoRenderer(model, clearColor) with ParametricLandmarksRenderer3D {
    override def renderImage(parameters: RenderParameter): PixelImage[RGBA] = super.renderImage(parameters)

    override def renderMesh(parameters: RenderParameter): VertexColorMesh3D = super.renderMesh(parameters)

    override def renderLandmark(lmId: String, parameter: RenderParameter): Option[TLMSLandmark2D] = super.renderLandmark(lmId, parameter)

    override def hasLandmarkId(lmId: String): Boolean = super.hasLandmarkId(lmId)

    override def allLandmarkIds: IndexedSeq[String] = super.allLandmarkIds

    override def renderMask(parameters: RenderParameter, mask: MeshSurfaceProperty[Int]): PixelImage[Int] = super.renderMask(parameters, mask)

    /** get a cached version of this renderer */
    override def cached(cacheSize: Int) = new AugmentedMoMoRenderer(model, clearColor) {
        private val imageRenderer = Memoize(super.renderImage, cacheSize)
        private val meshRenderer = Memoize(super.renderMesh, cacheSize)
        private val maskRenderer = Memoize((super.renderMask _).tupled, cacheSize)
        private val lmRenderer = Memoize((super.renderLandmark _).tupled, cacheSize * allLandmarkIds.length)
        private val instancer = Memoize(super.instanceFromCoefficients _, cacheSize)
        private val lmRenderer3D = Memoize((super.renderLandmark3D _).tupled, cacheSize)

        override def renderImage(parameters: RenderParameter): PixelImage[RGBA] = imageRenderer(parameters)

        override def renderLandmark(lmId: String, parameter: RenderParameter): Option[TLMSLandmark2D] = lmRenderer((lmId, parameter))

        override def renderMesh(parameters: RenderParameter): VertexColorMesh3D = meshRenderer(parameters)

        override def instance(parameters: RenderParameter): VertexColorMesh3D = instancer(parameters.momo)

        override def renderMask(parameters: RenderParameter, mask: MeshSurfaceProperty[Int]): PixelImage[Int] = maskRenderer((parameters, mask))

        override def renderLandmark3D(lmId: String, parameter: RenderParameter): Option[Landmark[_3D]] = super.renderLandmark3D(lmId, parameter)
    }

    def renderZBuffer(parameters: RenderParameter): PixelImage[Option[Double]] = {
        val inst = instance(parameters)

        Helpers.renderParameterZ(parameters,
            inst.shape,
            inst.color,
            inst.shape.vertexNormals,
            clearColor)
    }

    override def renderLandmark3D(lmId: String, parameter: RenderParameter): Option[Landmark[_3D]] = {
        val renderer = parameter.renderTransform
        for {
            ptId <- model.landmarkPointId(lmId)
            lm3d <- Some(model.instanceAtPoint(parameter.momo.coefficients, ptId)._1)
            lmWorld <- Some(renderer(lm3d))
        } yield Landmark(lmId, Point(lmWorld.x, lmWorld.y, lmWorld.z), None)
    }
}

object AugmentedMoMoRenderer {
    def apply(model: MoMo, clearColor: RGBA): AugmentedMoMoRenderer = new AugmentedMoMoRenderer(model, clearColor)
}
