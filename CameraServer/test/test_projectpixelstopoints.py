import random
import unittest

import cv2
import numpy as np
import pyrealsense2 as rs


def start_pipeline():
    save_path = "./out"
    pipeline = rs.pipeline()
    config = rs.config()

    config.enable_stream(rs.stream.depth, 640, 480, rs.format.z16, 30)
    config.enable_stream(rs.stream.color, 640, 480, rs.format.rgb8, 30)
    pipeline.start(config)
    print('[INFO] Starting pipeline')

    profile = pipeline.get_active_profile()
    # profile = pipeline.start(config)
    depth_profile = rs.video_stream_profile(profile.get_stream(rs.stream.depth))

    depth_intrinsics = depth_profile.get_intrinsics()
    w, h = depth_intrinsics.width, depth_intrinsics.height
    print("[INFO] Width: ", w, "Height: ", h)

    fx, fy, = depth_intrinsics.fx, depth_intrinsics.fy
    print("[INFO] Focal length X: ", fx)
    print("[INFO] Focal length Y: ", fy)

    ppx, ppy = depth_intrinsics.ppx, depth_intrinsics.ppy
    print("[INFO] Principal point X: ", ppx)
    print("[INFO] Principal point Y: ", ppy)

    # Identifying depth scaling factor
    depth_sensor = profile.get_device().first_depth_sensor()
    depth_scale = depth_sensor.get_depth_scale()

    pc = rs.pointcloud()
    colorizer = rs.colorizer()

    align_to = rs.stream.depth
    align = rs.align(align_to)
    frame_counter = 0
    try:
        print("[INFO] Starting pipeline stream with frame {:d}".format(frame_counter))
        while True:
            frames = pipeline.wait_for_frames()
            aligned_frames = align.process(frames)
            depth_frame = aligned_frames.get_depth_frame()
            color_frame = aligned_frames.get_color_frame()

            depth_colormap = np.asanyarray(colorizer.colorize(depth_frame).get_data())
            color_image = np.asanyarray(color_frame.get_data())

            pts = pc.calculate(depth_frame)
            pc.map_to(color_frame)

            if not depth_frame or not color_frame:
                print("No depth or color frame, try again...")
                continue

            images = np.hstack((cv2.cvtColor(color_image, cv2.COLOR_BGR2RGB), depth_colormap))
            cv2.imshow(str(frame_counter), images)

            key = cv2.waitKey(1)

            if key == ord("d"):
                min_distance = 1e-6
                v = pts.get_vertices()
                c = color_frame.get_data()
                vertices = np.asanyarray(v).view(np.float32).reshape(-1, 3)  # xyz
                color_data = np.asanyarray(c).reshape(-1, 3)  # Direct RGB
                h, w, _ = color_image.shape
                new_verts = []
                projected_pts = []
                counter = 0
                for x in range(h):
                    for y in range(w):
                        z = depth_frame.get_distance(y, x)
                        if z:
                            point = rs.rs2_deproject_pixel_to_point(depth_intrinsics, [y, x], z)
                            if (np.math.fabs(point[0]) >= min_distance
                                    or np.math.fabs(point[1]) >= min_distance
                                    or np.math.fabs(point[2]) >= min_distance):
                                projected_pts.append([
                                    round(point[0], 8),
                                    round(point[1], 8),
                                    round(point[2], 8)
                                ])
                        else:
                            # print("no info at:", [y, x], z)
                            counter += 1
                            pass  # Ignoring the pixels which doesn't have depth value
                for i in range(pts.size()):
                    if (np.math.fabs(vertices[i][0]) >= min_distance
                            or np.math.fabs(vertices[i][1]) >= min_distance
                            or np.math.fabs(vertices[i][2]) >= min_distance):
                        new_verts.append(vertices[i])

                print("Number of pixels ignored:", counter)
                return projected_pts, new_verts

            if key == ord("q"):
                break

    except Exception as ex:
        print(ex)
    finally:
        pipeline.stop()


class ProjectPixelsToPoints(unittest.TestCase):
    projected_pts, sdk_pts = start_pipeline()
    rnd = random.randint(0, len(projected_pts))

    print("Random point information bellow:")
    print("Projected point:",
          "index =", rnd,
          "x =", projected_pts[rnd][0],
          "y =", projected_pts[rnd][1],
          "z =", projected_pts[rnd][2]
          )
    print("SDK point:",
          "index =", rnd,
          "x =", sdk_pts[rnd][0],
          "y =", sdk_pts[rnd][1],
          "z =", sdk_pts[rnd][2]
          )

    # PASS
    def test_size_of_lists(self):
        if __name__ == '__main__':
            self.assertEqual(len(self.projected_pts), len(self.sdk_pts))

    # PASS
    def test_x_coordinate(self):
        for i in range(len(self.projected_pts)):
            self.assertAlmostEqual(
                self.projected_pts[i][0],
                self.sdk_pts[i][0],
                6,
                msg="x coordinates should be the same"
            )

    # PASS
    def test_y_coordinate(self):
        for i in range(len(self.projected_pts)):
            self.assertAlmostEqual(
                self.projected_pts[i][1],
                self.sdk_pts[i][1],
                6,
                msg="y coordinates should be the same"
            )

    # PASS
    def test_z_coordinate(self):
        for i in range(len(self.projected_pts)):
            self.assertAlmostEqual(
                self.projected_pts[i][2],
                self.sdk_pts[i][2],
                6,
                msg="z coordinates should be the same"
            )


if __name__ == "__main__":
    # start_pipeline()
    unittest.main()
