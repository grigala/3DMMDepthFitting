package ch.unibas.cs.gravis.thriftservice.utils

object DecimateModel {
    private val DEBUG = false

    class VectorMeshFieldInterpolator(mesh: TriangleMesh[_3D]) extends
        FieldInterpolator[_3D, UnstructuredPointsDomain[_3D], EuclideanVector[_3D]] {

        /**
         * Interpolates a given discrete field
         *
         * @param df DiscreteField
         * @return A continuous field of the same type.
         */
        override def interpolate(df: DiscreteField[_3D, UnstructuredPointsDomain[_3D], EuclideanVector[_3D]]): Field[_3D, EuclideanVector[_3D]] = {

            val deformationVecsOnSurface =
                SurfacePointProperty(mesh.triangulation, df.values.toIndexedSeq)

            val f = (pt: Point[_3D]) =>
                mesh.operations.closestPointOnSurface(pt) match {
                    case ClosestPointIsVertex(pt, _, id) => deformationVecsOnSurface(id)
                    case ClosestPointInTriangle(pt, _, id, bc) => deformationVecsOnSurface(id, bc)
                    case ClosestPointOnLine(pt, _, (id0, id1), d) =>
                        deformationVecsOnSurface(id0) * d + deformationVecsOnSurface(id1) * (1 - d)
                }
            Field(RealSpace[_3D], f)
        }
    }


    /**
     * Decimates the given model mesh by given rate
     *
     * @param model target mesh model to be decimated
     * @param rate  decimation rate, if no specified default value = 0.99
     * @return decimated StatisticalMeshModel
     */
    def decimate(model: StatisticalMeshModel, rate: Double = 0.99): StatisticalMeshModel = {
        val refVtk = MeshConversion.meshToVtkPolyData(model.referenceMesh)
        val decimatePro = new vtk.vtkDecimatePro()
        decimatePro.SetTargetReduction(rate)
        decimatePro.SetInputData(refVtk)
        decimatePro.Update()
        val decimatedRefVTK = decimatePro.GetOutput()
        val decimatedMesh = MeshConversion.vtkPolyDataToTriangleMesh(decimatedRefVTK).get

        val newGp = model.gp.interpolate(new VectorMeshFieldInterpolator(model.referenceMesh))
        val newSSM = StatisticalMeshModel(decimatedMesh, newGp)

        if (DEBUG) {
            println(s"[DecimateModel] - decimation rate = ${rate}")
            println(s"[DecimateModel] - number of points on default model ${model.referenceMesh.pointSet.numberOfPoints}")
            println(s"[DecimateModel] - number of points on decimated model ${newSSM.referenceMesh.pointSet.numberOfPoints}")
            println(s"[DecimateModel] - number of triangles on default model ${model.referenceMesh.triangulation.triangles.size}")
            println(s"[DecimateModel] - number of triangles on decimated model ${newSSM.referenceMesh.triangulation.triangles.size}")
        }
        newSSM

    }
}
