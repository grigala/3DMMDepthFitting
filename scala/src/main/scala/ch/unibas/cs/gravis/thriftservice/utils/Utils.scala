package ch.unibas.cs.gravis.thriftservice.utils

object Utils {

    def marginalizeModelForCorrespondences(model: StatisticalMeshModel,
                                           correspondences: Seq[(
                                               PointId, Point[_3D], MultivariateNormalDistribution
                                               )]): (StatisticalMeshModel, Seq[(PointId, Point[_3D], MultivariateNormalDistribution)]) = {

        val (modelIds, _, _) = correspondences.unzip3
        val marginalizedModel = model.marginal(modelIds.toIndexedSeq)
        val newCorrespondences = correspondences.map(idWithTargetPoint => {
            val (id, targetPoint, uncertainty) = idWithTargetPoint
            val modelPoint = model.referenceMesh.pointSet.point(id)
            val newId = marginalizedModel.referenceMesh.pointSet.findClosestPoint(modelPoint).id
            (newId, targetPoint, uncertainty)
        })
        (marginalizedModel, newCorrespondences)
    }

    def computeCenterOfMass(mesh: TriangleMesh[_3D]): Point[_3D] = {
        val normFactor = 1.0 / mesh.pointSet.numberOfPoints
        mesh.pointSet.points.foldLeft(Point(0, 0, 0))((sum, point) => sum + point.toVector * normFactor)
    }

    def computeCenterOfMassMoMo(mesh: TriangleMesh3D): Point[_3D] = {
        val normFactor = 1.0 / mesh.pointSet.numberOfPoints
        mesh.pointSet.points.foldLeft(Point(0, 0, 0))((sum, point) => sum + point.toVector * normFactor)
    }

    def eulerAnglesToRotMatrix3D(p: DenseVector[Double]): SquareMatrix[_3D] = {
        val rotMatrix = {
            // rotation matrix according to the "x-convention"
            val cospsi = Math.cos(p(2))
            val sinpsi = Math.sin(p(2))

            val costh = Math.cos(p(1))
            val sinth = Math.sin(p(1))

            val cosphi = Math.cos(p(0))
            val sinphi = Math.sin(p(0))

            SquareMatrix(
                (costh * cosphi, sinpsi * sinth * cosphi - cospsi * sinphi, sinpsi * sinphi + cospsi * sinth * cosphi),
                (costh * sinphi, cospsi * cosphi + sinpsi * sinth * sinphi, cospsi * sinth * sinphi - sinpsi * cosphi),
                (-sinth, sinpsi * costh, cospsi * costh))
        }
        rotMatrix
    }


    // scalismo-faces way for rotM → euler
    def decompose(r: SquareMatrix[_3D]): (Double, Double, Double) = {
        val pitch = math.atan2(r(2, 1), r(2, 2))
        val yaw = math.atan2(-r(2, 0), math.sqrt(r(2, 1) * r(2, 1) + r(2, 2) * r(2, 2)))
        val roll = math.atan2(r(1, 0), r(0, 0))
        (pitch, yaw, roll)
    }

    def time[R](block: => R): R = {
        val t0 = System.currentTimeMillis()
        val result = block
        val t1 = System.currentTimeMillis()
        println("Elapsed time: " + (t1 - t0) + "ms")
        result
    }

    def landmarkRMSD(targetLandmarks: Seq[Landmark[_3D]], modelLandmarks: Seq[Landmark[_3D]]): Double = {
        require(targetLandmarks.length == modelLandmarks.length)
        var absSum = 0.0
        targetLandmarks.foreach(lm =>
            modelLandmarks.foreach(l => {
                if (lm.id == l.id) {
                    val sum: Double = math.pow(math.abs(lm.point.x - l.point.x), 2) +
                        math.pow(math.abs(lm.point.y - l.point.y), 2) +
                        math.pow(math.abs(lm.point.z - l.point.z), 2)
                    absSum = absSum + sum
                }
            })
        )
        val RMSD: Double = math.sqrt(absSum / targetLandmarks.length)
        RMSD
    }

    def rmsDiff(img1: PixelImage[RGB], img2: PixelImage[RGB]): Double = {
        assert(img1.domain == img2.domain, "image resolution should be the same")
        val zipped: PixelImage[(RGB, RGB)] = img1.zip(img2)
        var sum = 0.0
        for (p <- zipped) yield {
            val p1 = p._1
            val p2 = p._2
            sum += math.pow(p1.r - p2.r, 2) +
                math.pow(p1.g - p2.g, 2) +
                math.pow(p1.b - p2.b, 2)
        }
        math.sqrt(sum / zipped.length)
    }

    def pixelRMSE(target: PixelImage[RGBA], result: PixelImage[RGBA]): Unit = {
        require(result.domain == target.domain, "pixelRMSE: images must be comparable! (different sizes)")
        val sigma = 0.072
        val relativeVariance = 9.0

        // Image Loop: sum all fg pixels, CLT enforces noise assumption, no bg model needed
        var sum: Double = 0.0
        var count = 0.0
        var x: Int = 0
        while (x < target.width) {
            var y: Int = 0
            while (y < result.height) {
                val refCol: RGB = target(x, y).toRGB
                val smp: RGBA = result(x, y)
                if (smp.a > 1e-4) {
                    val diff = refCol - smp.toRGB
                    val normSq: Double = diff.dot(diff)
                    sum += normSq
                    count += 1
                }
                y += 1
            }
            x += 1
        }
        if (count > 0) { // was something rendered on the image?
            val avg = sum / count
            //            val stdNormVariate: Double = (avg - 1) / Math.sqrt(relativeVariance / count)
            //            val r = -0.5 * Math.log(2 * Math.PI) - 0.5 * (stdNormVariate * stdNormVariate)
            println(s"Mean Squared error: $avg")
            println(s"Root Mean Squared erro: ${math.sqrt(avg)}")
        }
    }
}
