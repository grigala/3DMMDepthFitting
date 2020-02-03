import unittest

import cv2
import numpy as np
import pyrealsense2 as rs

from python.pipeline_utils import landmark_detector


def start_pipeline():
    save_path = "./out"
    pipeline = rs.pipeline()
    config = rs.config()

    config.enable_stream(rs.stream.depth, 1280, 720, rs.format.z16, 30)
    config.enable_stream(rs.stream.color, 1280, 720, rs.format.rgb8, 30)
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
    decimate = rs.decimation_filter()
    spatial = rs.spatial_filter
    hole = rs.hole_filling_filter
    temporal = rs.temporal_filter()
    colorizer = rs.colorizer()

    align_to = rs.stream.color
    align = rs.align(align_to)
    frame_counter = 0
    try:
        print("[INFO] Starting pipeline stream with frame {:d}".format(frame_counter))
        while True:
            frames = pipeline.wait_for_frames()
            aligned_frames = align.process(frames)

            aligned_depth_frame = aligned_frames.get_depth_frame()
            aligned_color_frame = aligned_frames.get_color_frame()

            depth_colormap = np.asanyarray(colorizer.colorize(aligned_depth_frame).get_data())
            color_image = np.asanyarray(aligned_color_frame.get_data())

            pts = pc.calculate(aligned_depth_frame)
            pc.map_to(aligned_color_frame)

            if not aligned_depth_frame or not aligned_color_frame:
                print("No depth or color frame, try again...")
                continue

            depth_image = np.asanyarray(aligned_depth_frame.get_data())

            images = np.hstack((cv2.cvtColor(color_image, cv2.COLOR_BGR2RGB), depth_colormap))
            cv2.imshow(str(frame_counter), images)
            key = cv2.waitKey(1)
            if key == ord("d"):
                landmarks = landmark_detector(color_image)
                for n, px in landmarks.items():
                    z = aligned_depth_frame.get_distance(px[0], px[1])
                    # Test Landmarks px = [width, height]
                    pt = rs.rs2_deproject_pixel_to_point(depth_intrinsics, [px[0], px[1]], z)
                    w, h = rs.rs2_project_point_to_pixel(depth_intrinsics, pt)
                    print("Original Pixel:", px)
                    print("Deprojected  Pixel:", [w, h])
                    return px, w, h
    except Exception as ex:
        print(ex)
    finally:
        pipeline.stop()


class ProjectionTest(unittest.TestCase):
    def test_pixel_to_point_point_to_pixel(self):
        px, w, h = start_pipeline()
        self.assertEqual(px[0], w)
        self.assertEqual(px[1], h)


if __name__ == "__main__":
    unittest.main()
