package ch.unibas.cs.gravis.thriftservice.rendering

/**
 * Shader object built from a RenderParameter
 *
 * @param mesh            mesh to shade, positions
 * @param color           colors (albedo)
 * @param normals         normals
 * @param renderParameter scene description
 */
case class ParametricShader(mesh: TriangleMesh3D,
                            color: MeshSurfaceProperty[RGBA],
                            normals: MeshSurfaceProperty[EuclideanVector[_3D]],
                            renderParameter: RenderParameter) {
    val modelTransform: Affine3D = renderParameter.pose.transform
    val cameraTransform: Affine3D = renderParameter.view.cameraPose.transform
    val eyeTransform: Affine3D = cameraTransform compose modelTransform
    val screenTransform: WindowTransform = WindowTransform(renderParameter.imageSize.width, renderParameter.imageSize.height)

    val transform: Point[_3D] => Point[_3D] = renderParameter.renderTransform
    val pointShader: PointShader = renderParameter.pointShader

    val worldNormals: MeshSurfaceProperty[EuclideanVector[_3D]] = normals.map(n => modelTransform(n).normalize: EuclideanVector[_3D])

    val colorTransform: RGBA => RGBA = (c: RGBA) => renderParameter.colorTransform.transform(c.toRGB).toRGBA

    val colorNormalMesh3D = ColorNormalMesh3D(mesh, color, worldNormals)

    val x: TriangleMesh3D = mesh.transform(p => modelTransform(p))
    val pixelShader: PixelShader[RGBA] = if (renderParameter.environmentMap.nonEmpty) {
        renderParameter.environmentMap.shader(colorNormalMesh3D, renderParameter.view.eyePosition) map colorTransform
    } else {
        renderParameter.directionalLight.shader(colorNormalMesh3D, renderParameter.view.eyePosition) map colorTransform
    }

}

object ParametricShader {
    /** parametric shader for a vertex color mesh with vertex normals */
    def apply(mesh: VertexColorMesh3D, renderParameter: RenderParameter): ParametricShader = {
        ParametricShader(mesh.shape, mesh.color, mesh.shape.vertexNormals, renderParameter)
    }
}
