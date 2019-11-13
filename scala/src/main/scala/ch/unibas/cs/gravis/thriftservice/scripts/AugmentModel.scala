package ch.unibas.cs.gravis.thriftservice.scripts

import java.io.File

import scalismo.faces.io.{MoMoIO, TLMSLandmarksIO}
import scalismo.faces.momo.MoMo
import scalismo.geometry.{Landmark, _3D}

object AugmentModel extends App {
    scalismo.initialize()

    val modelFile = new File("data/model2017-1_face12_nomouth.h5")
    val momo: MoMo = MoMoIO.read(modelFile).get

    val tlmsLandmarks = TLMSLandmarksIO.read3D(new File("data/landmarks.txt")).get
    val lm_map: Seq[Landmark[_3D]] = tlmsLandmarks.map { lm =>
        Landmark(lm.id, lm.point, None, None)
    }

    val namePointMap = lm_map.map { lm => (lm.id, lm) }.toMap
    val augmentedModel = momo.withLandmarks(namePointMap)
    MoMoIO.write(augmentedModel, new File("data/augmentedModelFace.h5"), "")
}
