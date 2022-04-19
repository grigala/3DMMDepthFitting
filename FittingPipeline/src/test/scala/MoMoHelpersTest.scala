
import breeze.linalg.DenseVector
import ch.unibas.cs.gravis.thriftservice.utils.Helpers.Parameters
import ch.unibas.cs.gravis.thriftservice.utils.MoMoHelpers
import org.scalatest.FunSuite
import scalismo.faces.io.MoMoIO
import scalismo.faces.io.renderparameters.RenderParameterJSONFormat._
import scalismo.faces.momo.MoMo
import scalismo.faces.parameters.{MoMoInstance, Pose, RenderParameter}
import scalismo.geometry.{EuclideanVector, EuclideanVector3D}
import scalismo.io.StatismoIO
import scalismo.statisticalmodel.StatisticalMeshModel
import spray.json._

import java.io.File

class MoMoHelpersTest extends FunSuite {

    scalismo.initialize()

    val modelName = "augmentedModelFace"
    val modelFile = new File(s"data/$modelName.h5")
    val smm: StatisticalMeshModel = StatismoIO.readStatismoPDM(new File("data/augmentedModelFace.h5"), "/shape").get
    val momoModel: MoMo = MoMoIO.read(modelFile).get
    val momoInstance: MoMoInstance = MoMoInstance(IndexedSeq.fill(50)(0.0), IndexedSeq.fill(50)(1.0), IndexedSeq.fill(5)(0.0), modelFile.toURI)


    val parameters: Parameters = Parameters(
        EuclideanVector(1, 0, 0),
        (1.0, 2.0, 0.0),
        DenseVector.zeros[Double](smm.rank)
    )

    val renderParameter: RenderParameter = RenderParameter.default.copy(pose = Pose(
        1.0,
        translation = EuclideanVector3D(1.0, 2.0, 3.0),
        roll = 6.0, // Z
        yaw = 5.0, // Y
        pitch = 2.0 // X
    )).withMoMo(momoInstance)

    test("convertMoMoToSMM()") {
        val convertedSMM: StatisticalMeshModel = MoMoHelpers.convertMoMoToSMM(momoModel)
        assert(smm.rank == convertedSMM.rank, "ranks should be same")
        assert(smm.mean == convertedSMM.mean, "smm means should be same")
        assert(smm == convertedSMM, "shape models should be same")
    }

    test("convertRenderParameterToParameter()") {
        println(renderParameter.toJson.prettyPrint)
        val t1: Parameters = MoMoHelpers.convertRenderParametersToParameters(renderParameter)
        val rpRotation = (renderParameter.pose.pitch, renderParameter.pose.yaw, renderParameter.pose.roll)
        val rpTranslation = renderParameter.pose.translation
        assert(t1.rotationParameters == rpRotation, "rotation parmeters should be same")
        assert(t1.translationParameters == rpTranslation, "translation parameters should be  same")
        assert(t1.modelCoefficients == renderParameter.momo.coefficients.shape, "model coefficients should be same")
    }

    test("convertParameterToRenderParameter()") {
        val t2: RenderParameter = MoMoHelpers.convertParametersToRenderParameters(parameters, renderParameter)
        println(t2.toJson.prettyPrint)
        val rotationParameters = (t2.pose.pitch, t2.pose.yaw, t2.pose.roll) // x, y, z
        assert(rotationParameters == parameters.rotationParameters, "rotation parameters should be same")
        assert(t2.pose.translation == parameters.translationParameters, "translation parameters should be same")
        assert(t2.momo.coefficients.shape == parameters.modelCoefficients, "model coefficients should be same")
    }
}
