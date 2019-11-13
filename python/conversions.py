import base64

import numpy as np
from PIL import Image

from python.Rs.ttypes import *

IMAGE_DEBUG = False
LM_DEBUG = False
PT_DEBUG = False
COL_DEBUG = False
PC_DEBUG = False


def image_to_thrift_image(im: Image) -> ThriftImage:
    """
    Transforms PIL.Image object to ThriftImage object
    :param im: PIL.Image
    :return: ThriftImage
    """
    if IMAGE_DEBUG:
        print("[IMAGE_DEBUG] Got image from the pipeline, transforming it to thrift image...")
    w, h = im.size
    ba = np.array(im)
    a = []
    for x in range(ba.shape[0]):
        for y in range(ba.shape[1]):
            a.append(ba[x][y][0])
            a.append(ba[x][y][1])
            a.append(ba[x][y][2])
    data = base64.b64encode(bytearray(a)).decode()

    return ThriftImage(data, w, h)


# TODO What is this?
def vector_to_thrift_vector(vector) -> ThriftVector3D:
    return ThriftVector3D(vector.x, vector.y, vector.z)


def landmark_2d_to_thrift_landmark_2d(lm_name_pixel_tuple) -> ThriftLandmark2D:
    """
    Transforms (str(name), ThriftPixel()) landmark tuple to ThriftLandmark2D
    :param lm_name_pixel_tuple:
    :return: ThriftLandmark2D
    """
    name = lm_name_pixel_tuple[0]
    w, h = lm_name_pixel_tuple[1]
    pixel = ThriftPixel(w, h)

    return ThriftLandmark2D(name, pixel, None)


def landmarks_2d_to_thrift_landmarks_2d(landmark_2d_list: dict):
    """
    Puts individual 2D landmarks into ThriftLandmark2DList
    :param landmark_2d_list:
    :return:
    """
    landmarks_2d = []
    for item in landmark_2d_list.items():
        landmarks_2d.append(landmark_2d_to_thrift_landmark_2d(item))
    return landmarks_2d


def landmark_to_thrift_landmark(lm_name_point_tuple) -> ThriftLandmark:
    """
    Transforms (str(name), ThriftPoint3D()) landmark tuple to ThriftLandmark object
    :param lm_name_point_tuple:
    :return: ThriftLandmark()
    """
    if LM_DEBUG:
        print("[LM_DEBUG] Got landmark from the pipeline, transforming it to thrift landmark...")
    name = lm_name_point_tuple[0]
    point = ThriftPoint3D(lm_name_point_tuple[1][0],  # x
                          lm_name_point_tuple[1][1],  # y
                          lm_name_point_tuple[1][2])  # z
    return ThriftLandmark(name, point, None)


def landmarks_to_thrift_landmarks(landmark_list: dict):
    """
    Transform a list of landmarks to ThriftLandmarkList object
    :param landmark_list: dict()
    :return: ThriftLandmarkList
    """
    if LM_DEBUG:
        print("[LM_DEBUG] Received landmark list...")
        print(landmark_list)

    landmarks = []
    for item in landmark_list.items():
        landmarks.append(landmark_to_thrift_landmark(item))
    if LM_DEBUG:
        print("[LM_DEBUG] Transforming them to ThriftLandmark object list...")
        print(landmarks)
    return landmarks


def point_to_thrift_point(point) -> ThriftPoint3D:
    """
    Transforms simple 3D point to ThriftPoint3D object
    :param point: (x, y, z)
    :return: ThriftPoint3D
    """
    if PT_DEBUG:
        print("[PT_DEBUG] Got point, transforming it to ThriftPoint3D...")
        print(point[0], point[1], point[2])
    return ThriftPoint3D(point[0], point[1], point[2])


def color_to_thrift_color(color) -> ThriftColor:
    """ThriftPointList
    Transforms RGB color to ThriftColor object
    :param color: (R, G, B)
    :return: ThriftColor
    """
    if COL_DEBUG:
        print(color[0], color[1], color[2])
    return ThriftColor(color[0], color[1], color[2])


def faces_to_thrift_faces(faces) -> ThriftTriangleCell:
    """
    Transforms triangular faces to ThriftTriangleCell object
    :param faces: (id1, id2, id3)
    :return: ThriftTriangleCell
    """
    if COL_DEBUG:
        print("[COL_DEBUG] Received face, transforming it to ThriftTriangleCell...")
        print(faces[0], faces[1], faces[2])
    return ThriftTriangleCell(faces[0], faces[1], faces[2])


def pc_to_thrift_pc(vertices, color, faces) -> ThriftVertexColorMesh:
    """
    Constructs ThriftVertexColorMesh given vertices, colors and faces
    :param vertices: (x, y, z)
    :param color: (r, g, b)
    :param faces: (id1, id2, id3)
    :return: ThriftVertexColorMesh
    """
    print("[pc_to_thrift_pc] Received vertex, color and face information...", )
    thrift_vertices = []
    thrift_color = []
    thrift_faces = []
    print("[pc_to_thrift_pc] Transforming vertices to ThriftPointList...")
    v1 = 0
    for v in vertices:
        if PC_DEBUG:
            if v1 < 10:
                print(point_to_thrift_point([v[0], v[1], v[2]]))
                v1 += 1
        # if v[2] <= 1.0:
        thrift_vertices.append(point_to_thrift_point([v[0], v[1], v[2]]))
    # print("[pc_to_thrift_pc] Transforming color to ThriftVertexColorList...")
    # c1 = 0
    # for c in color:
    #     if PC_DEBUG:
    #         if c1 < 10:
    #             print(color_to_thrift_color([c[0], c[1], c[2]]))
    #             c1 += 1
    #     thrift_color.append(color_to_thrift_color([c[0], c[1], c[2]]))
    print("[pc_to_thrift_pc] Transforming faces to ThriftTriangleCellList...")
    f1 = 0
    for f in faces:
        if PC_DEBUG:
            if f1 < 10:
                print(faces_to_thrift_faces([f[0], f[1], f[2]]))
                f1 += 1
        thrift_faces.append(faces_to_thrift_faces([f[0], f[1], f[2]]))

    pc_mesh = ThriftVertexColorMesh(thrift_vertices, thrift_color, thrift_faces)

    return pc_mesh
