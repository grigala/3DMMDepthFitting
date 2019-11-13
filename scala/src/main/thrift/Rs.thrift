namespace java ch.unibas.cs.gravis.realsense
#@namespace scala ch.unibas.cs.gravis.realsense


struct ThriftImage {
    1: required string data;
    2: required i32 width;
    3: required i32 height;
}

struct ThriftPixel {
    1: required i32 width;
    2: required i32 height;
}

struct ThriftPoint3D {
   1: required double x;
   2: required double y;
   3: required double z;
}

struct ThriftVector3D {
    1: required double x;
    2: required double y;
    3: required double z;
}

struct ThriftUncertaintyCovariance {
    1: required ThriftVector3D variances;
    2: required ThriftVector3D principalAxis1;
    3: required ThriftVector3D principalAxis2;
    4: required ThriftVector3D principalAxis3;
}

struct ThriftLandmark2D {
    1: required string name;
    2: required ThriftPixel pixels;
    3: optional double uncertainty;
}

struct ThriftLandmark {
    1: required string name;
    2: required ThriftPoint3D point;
    3: optional ThriftUncertaintyCovariance uncertainty;
}

struct ThriftTriangleCell {
    1: required i32 id1;
    2: required i32 id2;
    3: required i32 id3;
}

struct ThriftColor {
    1: required i16 r;
    2: required i16 g;
    3: required i16 b;
}

typedef list<ThriftPoint3D> ThriftPointList
typedef list<ThriftLandmark2D> ThriftLandmark2DList
typedef list<ThriftLandmark> ThriftLandmarkList
typedef list<ThriftColor> ThriftVertexColorList
typedef list<ThriftTriangleCell> ThriftTriangleCellList

//struct ThriftLandmarkList {
//    1: required list<ThriftLandmark> landmarks;
//}

//struct ThriftVertexColorList {
//    1: required list<ThriftColor> color;
//}

//struct ThriftPointList {
//    1: required list<ThriftPoint3D> vertices;
//}

//struct ThriftTriangleCellList {
//    1: required list<ThriftTriangleCell> faces;
//}

struct ThriftVertexColorMesh {
    1: required ThriftPointList vertices;
    2: optional ThriftVertexColorList color;
    3: required ThriftTriangleCellList faces;
}

struct CaptureResult {
    1: required ThriftImage image;
    2: optional ThriftLandmark2DList landmarks2d;
    3: required ThriftLandmarkList landmarks;
    4: required ThriftVertexColorMesh mesh;
}

exception ThriftServerError {
    1: string message;
    2: string stackTrace;
}

service RealSenseService {
    CaptureResult capture() throws(1: ThriftServerError error)
}
