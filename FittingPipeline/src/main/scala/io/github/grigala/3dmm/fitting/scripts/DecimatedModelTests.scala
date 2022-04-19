package ch.unibas.cs.gravis.thriftservice.scripts

import java.io.File

import ch.unibas.cs.gravis.thriftservice.utils.DecimateModel
import scalismo.faces.io.MoMoIO
import scalismo.faces.momo.MoMo
import scalismo.geometry.{Landmark, Point, _3D}
import scalismo.io.{LandmarkIO, MeshIO, StatismoIO}
import scalismo.mesh.TriangleMesh
import scalismo.registration.{LandmarkRegistration, RigidTransformation}

object DecimatedModelTests extends App {

    scalismo.initialize()
    val targetName = "target"

    val landmarksFull = LandmarkIO.readLandmarksJson[_3D](new File(s"targetData/${targetName}3DLandmarks.json")).get
    val targetLandmarks = landmarksFull.filter(lm => !lm.point.toArray.contains(0.0))
    val targetLandmarkNames = targetLandmarks.map(lm => lm.id)

    val targetMesh: TriangleMesh[_3D] = MeshIO.readMesh(new File(s"targetData/${targetName}Mesh.ply")).get
    val modelName = "data/augmentedModelFace.h5"
    val modelFile = new File(s"$modelName")

    val statismoModel = StatismoIO.readStatismoMeshModel(new File(s"$modelName"), "/shape").get

    // Cannot call .landmarks on StatismoMeshModel
    val momoModel: MoMo = MoMoIO.read(modelFile).get
    // obtaining sequence of model landmarks
    val momoLandmarks = momoModel.landmarks.values.toIndexedSeq
    // Filtering only landmarks that are available in targetLandmarks
    val momoLandmarksFiltered: Seq[Landmark[_3D]] = momoLandmarks.filter(lm => targetLandmarkNames.contains(lm.id))
    assert(targetLandmarks.size == momoLandmarksFiltered.size, "should have the same number(IDs) of elements")

    val momoLmsSorted = momoLandmarksFiltered.sortBy(lm => lm.id)
    val targetLmsSorted = targetLandmarks.sortBy(lm => lm.id)

    val rigidTransform: RigidTransformation[_3D] = LandmarkRegistration
        .rigid3DLandmarkRegistration(momoLmsSorted, targetLmsSorted, center = Point(0, 0, 0))
    val transformedModel = statismoModel.transform(rigidTransform)

    val decimatedModel0 = DecimateModel.decimate(transformedModel, 0.95)
    MeshIO.writeMesh(decimatedModel0.referenceMesh, new File("output/decmodel/decModel095.ply"))
    val decimatedModel1 = DecimateModel.decimate(transformedModel, 0.9)
    MeshIO.writeMesh(decimatedModel1.referenceMesh, new File("output/decmodel/decModel09.ply"))
    val decimatedModel2 = DecimateModel.decimate(transformedModel, 0.6)
    MeshIO.writeMesh(decimatedModel2.referenceMesh, new File("output/decmodel/decModel06.ply"))
    val decimatedModel3 = DecimateModel.decimate(transformedModel, 0.3)
    MeshIO.writeMesh(decimatedModel3.referenceMesh, new File("output/decmodel/decModel03.ply"))
    val decimatedModel4 = DecimateModel.decimate(transformedModel, 0.1)
    MeshIO.writeMesh(decimatedModel4.referenceMesh, new File("output/decmodel/decModel01.ply"))

}
