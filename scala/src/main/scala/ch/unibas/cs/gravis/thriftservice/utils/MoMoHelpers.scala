package ch.unibas.cs.gravis.thriftservice.utils

import ch.unibas.cs.gravis.thriftservice.utils.Helpers.Parameters

object MoMoHelpers {

    /**
     * Converts MoMo model to StatisticalMeshModel
     *
     * @param momo MoMo
     * @return StatisticalMeshModel
     */
    def convertMoMoToSMM(momo: MoMo): StatisticalMeshModel = {

        val refMesh: TriangleMesh3D = momo.neutralModel.referenceMesh
        /** Obtaining DLRGP from momo's neutral shape model **/
        val DLRGP: DiscreteLowRankGaussianProcess[_3D, UnstructuredPointsDomain[_3D], Point[_3D]] = momo.neutralModel.shape.gpModel
        /** Converting Point[_3D] to deformation vector **/
        val vectorDLRGP: DiscreteLowRankGaussianProcess[_3D, UnstructuredPointsDomain[_3D], EuclideanVector[_3D]] = ModelHelpers.pointToVectorDLRGP(DLRGP, refMesh)

        StatisticalMeshModel(refMesh, vectorDLRGP)
    }

    /**
     * Converting RenderParameter object to Parameters object which handles
     * only translation and rotation parameters with <b>shape<b> model coefficients
     *
     * @param renderParameter RenderParameter
     * @return Parameters(translationParams, rotationParams, modelCoeffs)
     * @see [[scalismo.faces.parameters.RenderParameter]]
     */
    def convertRenderParametersToParameters(renderParameter: RenderParameter): Parameters = {
        val translationParameters = renderParameter.pose.translation
        val x = renderParameter.pose.pitch // X
        val y = renderParameter.pose.yaw // Y
        val z = renderParameter.pose.roll // Z

        val modelCoefficients = renderParameter.momo.coefficients.shape // only shape coefficients

        Parameters(translationParameters, (x, y, z), modelCoefficients)
    }

    /**
     * Converts Parameters object to RenderParameter object and injects Parameters
     * translation, rotation and model coefficients.
     *
     * @param p          Parameter
     * @param rpTemplate RenderParameter
     * @return RenderParameter with rotation, translation and coefficients from p
     */
    def convertParametersToRenderParameters(p: Parameters, rpTemplate: RenderParameter): RenderParameter = {
        val translation: EuclideanVector[_3D] = p.translationParameters
        val rotation: (Double, Double, Double) = p.rotationParameters
        val modelCoeffs: DenseVector[Double] = p.modelCoefficients

        val pose: Pose = rpTemplate.pose.copy(
            translation = translation,
            roll = rotation._3,
            yaw = rotation._2,
            pitch = rotation._1
        )

        val momoInstance: MoMoInstance = rpTemplate.momo.copy(shape = modelCoeffs.toArray)

        rpTemplate.copy(pose = pose, momo = momoInstance)
    }
}
