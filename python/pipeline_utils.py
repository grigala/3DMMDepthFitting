import csv
import os
import time
from collections import Counter

import dlib
import cv2
import numpy as np
import pyrealsense2 as rs
from mlxtend.image import extract_face_landmarks
import matplotlib.pyplot as plt

LM_DEBUG = False
PC_DEBUG = False
SAVE_PATH = "./out"


def write_depth_to_file(width, height, f, depth):
    file = open(f, "w+")
    file.write(str(width) + " ")
    file.write(str(height) + " ")
    file.write("pixels (X Y Z)\n")
    for x in range(width):
        for y in range(height):
            z = depth.get_distance(x, y)
            # if z < 1.0:
            file.write(str(z) + ' ')

    file.close()
    print("[INFO] Finished writing to the file...")


def detect_landmarks_mlxtend(img):
    timestr = time.strftime("%Y%m%d-%H%M%S")
    landmarks = extract_face_landmarks(img)
    fig = plt.figure(figsize=(10, 5))
    # ax = fig.add_subplot(1, 3, 1)
    # ax.imshow(img)
    ax = fig.add_subplot(1, 2, 1)
    ax.scatter(landmarks[:, 0], -landmarks[:, 1], alpha=0.8)
    # print(landmarks[:][:])
    ax = fig.add_subplot(1, 2, 2)
    img2 = img.copy()
    for p in landmarks:
        x, y = p
        cv2.circle(img2, (x, y), 1, (0, 128, 0), 3)
    ax.imshow(img2)
    plt.show()
    # fig.savefig(os.path.join(SAVE_PATH, 'landmark_plot{:s}.png'.format(timestr)))


def landmark_detector(img):
    """
    Takes color image and returns a list(portion) of facial landmarks as  pixels
    :param  img: PIL.Image object or image as an array
    :return: landmarks: A list of landmarks with signature (str(name, pixel(w, h))
    :exception: If faces are not detected method terminates
    """
    predictor_path = "data/shape_predictor_68_face_landmarks.dat"

    if LM_DEBUG:
        print("[INFO] loading facial landmark predictor...")
    detector = dlib.get_frontal_face_detector()
    predictor = dlib.shape_predictor(predictor_path)
    # win = dlib.image_window()
    # img = dlib.load_rgb_image(img)
    # win.clear_overlay()
    # win.set_title("Captured image from landmark_detector()")
    # win.set_image(img)
    # Plot if necessary
    # detect_landmarks_mlxtend(img)
    landmarks = {
        "right.eye.corner_inner": 39,
        "right.nose.wing.tip": 31,
        "left.eye.corner_outer": 45,
        "center.lips.lower.inner": 66,
        "center.chin.tip": 8,
        "right.eye.corner_outer": 36,
        "left.lips.philtrum_ridge": 52,
        "right.lips.philtrum_ridge": 50,
        "right.lips.corner": 48,
        "left.eye.corner_inner": 42,
        "center.lips.lower.outer": 57,
        "center.lips.upper.inner": 62,
        "right.eyebrow.bend.upper": 18,
        "center.nose.attachement_to_philtrum": 33,
        "left.eyebrow.inner_upper": 23,
        "left.eyebrow.bend.upper": 25,
        "center.nose.tip": 30,
        "right.eyebrow.inner_upper": 20,
        "center.lips.upper.outer": 51,
        "left.nose.wing.tip": 35,
        "left.lips.corner": 54,
        # "chin.0": 0,  # This landmark is not in the BFM model landmarks(only in face model)
        "chin.1": 1,
        "chin.2": 2,
        "chin.3": 3,
        "chin.4": 4,
        "chin.5": 5,
        "chin.6": 6,
        "chin.7": 7,
        "chin.8": 8,
        "chin.9": 9,
        "chin.10": 10,
        "chin.11": 11,
        "chin.12": 12,
        "chin.13": 13,
        "chin.14": 14,
        "chin.15": 15,
    }

    face_landmark_ids = [39, 31, 45, 66, 8, 36, 52, 50, 48, 42,
                         57, 62, 18, 33, 23, 25, 30, 20, 51, 35, 54]

    faces = detector(img, 1)
    if not faces:
        raise Exception('No face detected. Try again...')

    shape = predictor(img, faces[0])
    # For every landmark replacing dlib landmark id with
    # pixel information signature (str(name, pixel(w, h)) Example:
    # "right.eye.corner_inner": 39 -> "right.eye.corner_inner": (200, 200)
    for a, b in landmarks.items():
        landmarks.update({"%s" % a: tuple((shape.part(b).x, shape.part(b).y))})

    if LM_DEBUG:
        for a, b in landmarks.items():
            print("Landmark name: ", a, "| Pixel Location: ", [b[0], b[1]])

    # win.wait_until_closed()
    return landmarks


def pixels_to_landmarks(intrinsics, color_frame, depth_frame, depth_scale):
    """
    Transforms pixel landmarks to point space
    :param intrinsics: Intrinsic parameters of the camera
    :param color_frame: color frame
    :param depth_frame: pyrealsense2.pyrealsense2.composite_frame -> rs2::depth_frame
    :param depth_scale: depth scale information
    :return: a list of landmarks with signature -> dict(str(name), tuple((x, y, z)))
    """
    print("[INFO] Depth Scale is: ", depth_scale)
    print("[INFO] Multiplying points by:", 1 / depth_scale)
    scale = 1 / depth_scale
    color_image = np.asanyarray(color_frame.get_data())
    lm = landmark_detector(color_image)
    points = list()
    for n, px in lm.items():
        z = depth_frame.get_distance(px[0], px[1])
        xx = rs.rs2_deproject_pixel_to_point(intrinsics, [px[0], px[1]], z)[0]
        yy = rs.rs2_deproject_pixel_to_point(intrinsics, [px[0], px[1]], z)[1]
        zz = rs.rs2_deproject_pixel_to_point(intrinsics, [px[0], px[1]], z)[2]

        # No scaling, no shift - landmarks are still off so it's not scaling issue
        # lm.update({"%s" % n: (xx, yy, zz)})

        # No shift introduced
        lm.update({"%s" % n: (xx * scale, yy * scale, zz * scale)})

        # When frames are aligned
        # lm.update({"%s" % n: ((xx + 0.006) * scale, (yy + 0.003) * scale, zz * scale)})
        # lm.update({"%s" % n: ((xx) * scale, (yy) * scale, zz * scale)})

        # When frames are not aligned
        # lm.update({"%s" % n: ((xx - 0.005) * scale, (yy + 0.007) * scale, zz * scale)})

        # Test points alone
        points.append([xx * scale, yy * scale, zz * scale])

        if LM_DEBUG:
            with open(os.path.join(SAVE_PATH, '3D_landmarks_rounded.numpy.txt'), 'w+') as f:
                np.savetxt(f, np.asanyarray(points), fmt='%-7.8f', delimiter=' ')
            print("Landmark Pixels: ", px[0], px[1], "Corresponding 3D Points: ", xx, yy, zz, "Scaled: ", xx * scale,
                  yy * scale, zz * scale)

        # with open(os.path.join(SAVE_PATH, "3D_landmarks_raw.txt"), "w+") as f:
        #     csv_writer = csv.writer(f)
        #     csv_writer.writerows(lm)

    return lm


def create_pc_mesh(intrinsics, points: rs.points, color_frame: rs.frame, depth_frame, depth_scale):
    """
    Method for creating a point cloud mesh object
    :param intrinsics: Camera parameters
    :param points: rs.points
    :param color_frame: rs.frame
    :param depth_frame: rs.frame
    :param depth_scale: Depth scale(~0.001)
    :return: vertices, triangles, color
    """
    scale = 1 / depth_scale

    v = points.get_vertices()
    t = points.get_texture_coordinates()
    c = color_frame.get_data()

    # TODO filter vertices by their z value so we don't have to process entire scene
    vertices = np.asanyarray(v).view(np.float32).reshape(-1, 3)  # xyz
    texcoords = np.asanyarray(t).view(np.float32).reshape(-1, 2)  # uv texture coordinates
    color_data = np.asanyarray(c).reshape(-1, 3)  # Direct texture

    profile = rs.video_stream_profile(points.get_profile())
    width = profile.width()
    height = profile.height()

    idx_map = {}
    new_vertices = []
    color = []
    test_vertices = []

    min_distance = 1e-2
    max_distance = 1.0
    count = 0

    # Calculating vertices obtaining color values
    for i in range(points.size()):
        if (np.math.fabs(vertices[i][0]) >= min_distance
                or np.math.fabs(vertices[i][1]) >= min_distance
                or np.math.fabs(vertices[i][2]) >= min_distance):
            idx_map[i] = len(new_vertices)
            # new_vertices.append((vertices[i][0] * scale, vertices[i][1] * scale, vertices[i][2] * scale))
            # new_vertices.append(vertices[i])
            new_vertices.append(vertices[i] * scale)
            # if color_frame:
            #     color.append(color_data[i])
            if PC_DEBUG:
                if i < 20:
                    print("Vertex Coordinate: ", vertices[i],
                          "| Color Information(RGB): ", color_data[i])

    threshold = 0.05
    triangles = []
    counter = Counter(idx_map)

    # Calculating Triangular Faces
    for x in range(width - 1):
        for y in range(height - 1):
            a = y * width + x
            b = y * width + x + 1
            c = (y + 1) * width + x
            d = (y + 1) * width + x + 1

            if (vertices[a][2] and vertices[b][2] and vertices[c][2] and vertices[d][2]
                    and np.math.fabs(vertices[a][2] - vertices[b][2]) < threshold
                    and np.math.fabs(vertices[a][2] - vertices[c][2]) < threshold
                    and np.math.fabs(vertices[b][2] - vertices[d][2]) < threshold
                    and np.math.fabs(vertices[c][2] - vertices[d][2]) < threshold):
                if (counter[a] == 0
                        or counter[b] == 0
                        or counter[c] == 0
                        or counter[d] == 0):
                    continue

                triangles.append([idx_map[a], idx_map[b], idx_map[d]])
                triangles.append([idx_map[d], idx_map[c], idx_map[a]])

    if PC_DEBUG:
        print("Number of vertices: ", len(new_vertices))
        file = open(os.path.join(SAVE_PATH, "point_cloud.ply"), "w+")

        file.write("ply\n")
        file.write("format ascii 1.0\n")
        file.write("comment generated by George\n")
        file.write("element vertex ")
        file.write(str(len(new_vertices)) + "\n")
        file.write("property float32 x\n")
        file.write("property float32 y\n")
        file.write("property float32 z\n")
        file.write("property uchar red\n")
        file.write("property uchar green\n")
        file.write("property uchar blue\n")
        file.write("property uchar alpha\n")
        file.write("element face ")
        file.write(str(len(triangles)) + "\n")
        file.write("property list uchar int vertex_indices\n")
        file.write("end_header\n")
        for item in range(len(new_vertices)):
            file.write(str(round(new_vertices[item][0], 6)) + " ")
            file.write(str(round(new_vertices[item][1], 6)) + " ")
            file.write(str(round(new_vertices[item][2], 6)) + " ")
            file.write(str(color[item][0]) + " ")
            file.write(str(color[item][1]) + " ")
            file.write(str(color[item][2]) + " ")
            file.write(str(255) + "\n")

        for item in range(len(triangles)):
            file.write(str(3) + " ")
            file.write(str(triangles[item][0]) + " ")
            file.write(str(triangles[item][1]) + " ")
            file.write(str(triangles[item][2]) + "\n")
        file.close()

        with open(os.path.join(SAVE_PATH, 'color.txt'), 'w+') as f:
            np.savetxt(f, np.asanyarray(color), fmt='%i', delimiter=' ')
        with open(os.path.join(SAVE_PATH, 'triangles.txt'), 'w+') as f:
            np.savetxt(f, np.asanyarray(triangles), fmt='%i', delimiter=' ')
        with open(os.path.join(SAVE_PATH, 'vertices.txt'), 'w+') as f:
            np.savetxt(f, np.asanyarray(new_vertices), fmt='%-7.8f', delimiter=' ')

    return new_vertices, color, triangles


# TODO not working yet
def clip_mesh(vertices, triangles, landmarks):
    """
    Takes full list of vertices and faces alongside with 3D landmarks list
    and returns vertices and faces for face region only.
    :param vertices: list(list(float, float, float)) - list of 3D points
    :param triangles: list(list(int)) - list of Triangles
    :param landmarks: list of 3D landmark points
    :return: filtered vertices, filtered faces
    """
    vertices_tmp = []  # Filtered vertices, points outside of region are 0.0

    x_min, x_max, y_min, y_max = find_clipping_values(landmarks)

    for v in vertices:
        x_check = np.logical_or(v[0] < x_min, v[0] > x_max)
        y_check = np.logical_or(v[0] < y_min, v[0] > y_max)
        if x_check or y_check:
            v0 = [v[0] * 0.0, v[1] * 0.0, v[2] * 0.0]
            vertices_tmp.append(v0)
        else:
            vertices_tmp.append(v)
    assert len(vertices_tmp) == len(vertices)

    # Very slow
    final_vertices = []
    final_triangles = []

    # # Allegedly this should be working
    # final_triangles = list(filter(lambda x: (
    #         (fv[x[0]][0] != 0.0 and fv[x[0]][1] != 0.0 and fv[x[0]][2] != 0.0) and
    #         (fv[x[1]][0] != 0.0 and fv[x[1]][1] != 0.0 and fv[x[1]][2] != 0.0) and
    #         (fv[x[2]][0] != 0.0 and fv[x[2]][1] != 0.0 and fv[x[2]][2] != 0.0)
    # ), triangles))
    #
    # print("Filtered size of faces:", len(final_triangles))
    #
    # final_vertices = list(filter(lambda x: (fv[x][0] != 0.0 and fv[x][1] != 0.0 and fv[x][2] != 0.0), fv))
    # print("Filtered size of vertices:", len(final_vertices))
    #
    # count1 = 0
    # count2 = 0
    # for i in final_vertices:
    #     if count1 < 10:
    #         print("Point: ", i)
    #         count1 += 1
    # for i in final_triangles:
    #     if count2 < 10:
    #         print("Triangle: ", i)
    #         count2 += 1

    return vertices, triangles


def find_clipping_values(landmarks):
    """
    Finds outer most landmark points for x and y axes detected by Dlib
    :param landmarks: list
    :return: x_min, x_max, y_min, y_max
    """
    x_min, x_max, y_min, y_max = 0, 0, 0, 0

    for _, point in landmarks.items():
        x_max = max(point[0], x_max)
        x_min = min(point[0], x_min)
        y_min = min(point[1], y_min)
        y_max = max(point[1], y_max)

    if PC_DEBUG:
        print("min_clipping_x_coord = ", x_min)
        print("max_clipping_x_coord = ", x_max)
        print("min_clipping_y_coord = ", y_min)
        print("max_clipping_x_coord = ", y_max)

    return x_min, x_max, y_min, y_max


def get_texture_color(frame: rs.video_frame, u: float, v: float):
    """
    Manually calculating RGB values from texture coordinates (u, v)
    :param frame: color frame -> rs.frame
    :param u: texture coordinate
    :param v: texture coordinate
    :return:
    """
    w = frame.get_width()
    h = frame.get_height()
    x = min(max(int(u * w + .5), 0), w - 1)
    y = min(max(int(v * h + .5), 0), h - 1)
    idx = int(x * frame.get_bytes_per_pixel() +
              y * frame.get_stride_in_bytes())
    texture_data = np.asanyarray(frame.get_data())  # .reshape(-1, 3)

    return texture_data[idx], texture_data[idx + 1], texture_data[idx + 2]
