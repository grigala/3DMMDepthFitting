# Efficient 3D Morphable Model Face Fitting Using Depth Sensing Technologies

This is the root directory for the project we've implemented as part of my MSc work. It performs a 3D Morphable Model face fitting and as a result produces a dense 3D face reconstructions based on a single image and the depth information obtained from the camera.
 
 
Project contains two language specific modules Python server and the Scala client. The server handles all the camera related operations, including configuration, data acquisition and delivery for fitting pipeline.

If you do not have a dedicated camera [FitScriptOffline.scala](scala/src/main/scala/ch/unibas/cs/gravis/thriftservice/apps/FitScriptOffline.scala) is your starting point. 

Otherwise, first you should run [Server.py](python/Server.py) and then [FitScriptOnline.scala](scala/src/main/scala/ch/unibas/cs/gravis/thriftservice/apps/FitScriptOnline.scala)



Example result:

![](./sample.png)



## Technology Stack & Credits

This work would not have been possible without the following Open Source projects:
### Server-side:

* [Python 3](https://www.python.org/)
* [OpenCV (Python)](https://opencv.org/)
* [librealsense (Python Wrapper)](https://github.com/IntelRealSense/librealsense)
* [dlib (Python)](http://dlib.net/)
* [Apache Thrift](https://github.com/apache/thrift)
* [Numpy](https://github.com/numpy/numpy)

### Client-side:

* JDK 1.8
* [Scala](https://github.com/scala/scala)
* [SBT](https://github.com/sbt/sbt)
* [Finagle](https://github.com/twitter/finagle)
* [Scrooge](https://github.com/twitter/scrooge)
* [Scalismo](https://github.com/unibas-gravis/scalismo)
* [Scalismo-faces](https://github.com/unibas-gravis/scalismo-faces)
* [Scalismo-ui](https://github.com/unibas-gravis/scalismo-ui)
* [Scalismo Tutorials](https://scalismo.org/tutorials)


## Copyright and License

The code is distributed under GNU General Public License v3.0, refer to [LICENSE](LICENSE) for more details.

© Giorgi Grigalashvili as part of my MSc Thesis at University of Basel, Graphics and Vision Research Group, 2019.

## References

`TODO`
