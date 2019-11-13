import os
import time

import cv2
import numpy as np
import pyrealsense2 as rs

from python.pipeline_utils import pixels_to_landmarks, create_pc_mesh, clip_mesh, landmark_detector


def start_pipeline():
    save_path = "./out/cameraparams/"
    pipeline = rs.pipeline()
    config = rs.config()

    config.enable_stream(rs.stream.depth, 640, 480, rs.format.z16, 30)
    config.enable_stream(rs.stream.color, 640, 480, rs.format.rgb8, 30)
    pipeline.start(config)
    print('[INFO] Starting pipeline')

    profile = pipeline.get_active_profile()
    # sensor = profile.get_device().first_roi_sensor()
    # roi = sensor.get_region_of_interest()
    # sensor.set_region_of_interest(roi)
    # profile = pipeline.start(config)
    depth_profile = rs.video_stream_profile(profile.get_stream(rs.stream.depth))
    color_profile = rs.video_stream_profile(profile.get_stream(rs.stream.color))

    depth_intrinsics = depth_profile.get_intrinsics()
    color_intrinsics = color_profile.get_intrinsics()

    depth_extrinsics = depth_profile.get_extrinsics_to(color_profile)
    color_extrinsics = color_profile.get_extrinsics_to(depth_profile)
    print("- depth extrinsics:", depth_extrinsics)
    print("- color extrinsics:", color_extrinsics)

    color_profile = rs.video_stream_profile(profile.get_stream(rs.stream.color))
    color_intrinsics = color_profile.get_intrinsics()
    color_extrinsics = color_profile.get_extrinsics_to(depth_profile)

    # Identifying depth scaling factor
    depth_sensor = profile.get_device().first_depth_sensor()
    depth_scale = depth_sensor.get_depth_scale()

    clipping_distance_in_meters = 1
    clipping_distance = clipping_distance_in_meters / depth_scale

    pc = rs.pointcloud()
    colorizer = rs.colorizer()

    align_to = rs.stream.depth
    align = rs.align(align_to)
    frame_counter = 0
    try:
        print("[INFO] Starting pipeline stream with frame {:d}".format(frame_counter))
        while True:
            frames = pipeline.wait_for_frames()
            # depth_frame = frames.get_depth_frame()
            non_aligned_color_frame = frames.get_color_frame()

            # Aligning color and depth frames, aligned frames are
            # getting re-sampled from the original one, so they are
            # not exactly the same points.
            aligned_frames = align.process(frames)
            depth_frame = aligned_frames.get_depth_frame()
            color_frame = aligned_frames.get_color_frame()

            depth_colormap = np.asanyarray(colorizer.colorize(depth_frame).get_data())
            color_image = np.asanyarray(non_aligned_color_frame.get_data())
            color_image_from_depth = np.asanyarray(color_frame.get_data())

            pts = pc.calculate(depth_frame)
            pc.map_to(color_frame)
            if not depth_frame or not color_frame:
                print("No depth or color frame, try again...")
                continue

            depth_image = np.asanyarray(depth_frame.get_data())
            # np.savetxt("out/depth.csv", depth_image, delimiter=",")

            images = np.hstack((cv2.cvtColor(color_image, cv2.COLOR_BGR2RGB), depth_colormap))
            # cv2.cvtColor(color_image, cv2.COLOR_BGR2RGBA)
            # cv2.imwrite(os.path.join(save_path, "{:d}.color.png".format(frame_counter)),
            #             cv2.cvtColor(color_image, cv2.COLOR_RGB2BGR))

            cv2.imshow(str(frame_counter), images)
            key = cv2.waitKey(1)
            if key == ord("d"):
                # Saving camera parameters to file
                timestr = time.strftime("%Y%m%d-%H%M%S")
                with open(os.path.join(save_path, "intrinsics-{:s}.txt".format(timestr)), 'w') as f:
                    print("[INFO] Depth scale factor: ", depth_scale, file=f)
                    print("\n[INFO] Depth camera intrinsics: ", depth_intrinsics, file=f)
                    print("[INFO] Depth camera extrinsics: ", depth_extrinsics, file=f)
                    print("\n[INFO] Color camera intrinsics: ", color_intrinsics, file=f)
                    print("[INFO] Color camera extrinsics: ", color_extrinsics, file=f)

                cv2.destroyAllWindows()
                print("[rs_pipeline] Running landmark detection on a color image...")
                landmarks2d = landmark_detector(color_image)
                landmarks3d = pixels_to_landmarks(depth_intrinsics, color_frame, depth_frame, depth_scale)
                print("[rs_pipeline] Computing point cloud mesh elements...")
                vert, col, face = create_pc_mesh(intrinsics=depth_intrinsics,
                                                 points=pts,
                                                 color_frame=color_frame,
                                                 depth_frame=depth_frame,
                                                 depth_scale=depth_scale)
                # TODO it's very slow and I'm giving up, not enough time, will clip it on client side.
                # filtered_vertices, filtered_faces = clip_mesh(vert, face, landmarks3d)
                print("[INFO] Everything's ready, passing back to Thrift...")
                return color_image, landmarks2d, landmarks3d, [vert, col, face]
            if key == ord("q"):
                break
    except Exception as ex:
        print(ex)
    finally:
        pipeline.stop()

def start_pipeline_no_cv2():
    save_path = "./out/cameraparams/"
    pipeline = rs.pipeline()
    config = rs.config()

    config.enable_stream(rs.stream.depth, 640, 480, rs.format.z16, 30)
    config.enable_stream(rs.stream.color, 640, 480, rs.format.rgb8, 30)
    pipeline.start(config)
    print('[INFO] Starting pipeline')

    profile = pipeline.get_active_profile()
    # sensor = profile.get_device().first_roi_sensor()
    # roi = sensor.get_region_of_interest()
    # sensor.set_region_of_interest(roi)
    # profile = pipeline.start(config)
    depth_profile = rs.video_stream_profile(profile.get_stream(rs.stream.depth))
    color_profile = rs.video_stream_profile(profile.get_stream(rs.stream.color))

    depth_intrinsics = depth_profile.get_intrinsics()
    color_intrinsics = color_profile.get_intrinsics()

    depth_extrinsics = depth_profile.get_extrinsics_to(color_profile)
    color_extrinsics = color_profile.get_extrinsics_to(depth_profile)
    print("- depth extrinsics:", depth_extrinsics)
    print("- color extrinsics:", color_extrinsics)

    color_profile = rs.video_stream_profile(profile.get_stream(rs.stream.color))
    color_intrinsics = color_profile.get_intrinsics()
    color_extrinsics = color_profile.get_extrinsics_to(depth_profile)

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
        time.sleep(1) # Giving a second to auto exposrure
        while True:
            frames = pipeline.wait_for_frames()
            # depth_frame = frames.get_depth_frame()
            non_aligned_color_frame = frames.get_color_frame()

            # Aligning color and depth frames, aligned frames are
            # getting re-sampled from the original one, so they are
            # not exactly the same points.
            aligned_frames = align.process(frames)
            depth_frame = aligned_frames.get_depth_frame()
            color_frame = aligned_frames.get_color_frame()

            depth_colormap = np.asanyarray(colorizer.colorize(depth_frame).get_data())
            color_image = np.asanyarray(non_aligned_color_frame.get_data())

            pts = pc.calculate(depth_frame)
            pc.map_to(color_frame)
            if not depth_frame or not color_frame:
                print("No depth or color frame, try again...")
                continue
            depth_image = np.asanyarray(depth_frame.get_data())
            # np.savetxt("out/depth.csv", depth_image, delimiter=",")

            image = cv2.cvtColor(color_image, cv2.COLOR_BGR2RGB)

            cv2.imwrite(os.path.join(save_path, "{:d}.color.png".format(frame_counter)), image)
            timestr = time.strftime("%Y%m%d-%H%M%S")
            with open(os.path.join(save_path, "intrinsics-{:s}.txt".format(timestr)), 'w') as f:
                print("[INFO] Depth scale factor: ", depth_scale, file=f)
                print("\n[INFO] Depth camera intrinsics: ", depth_intrinsics, file=f)
                print("[INFO] Depth camera extrinsics: ", depth_extrinsics, file=f)
                print("\n[INFO] Color camera intrinsics: ", color_intrinsics, file=f)
                print("[INFO] Color camera extrinsics: ", color_extrinsics, file=f)

            print("[rs_pipeline] Running landmark detection on a color image...")
            landmarks2d = landmark_detector(image)
            landmarks3d = pixels_to_landmarks(depth_intrinsics, color_frame, depth_frame, depth_scale)
            print("[rs_pipeline] Computing point cloud mesh elements...")
            vert, col, face = create_pc_mesh(intrinsics=depth_intrinsics,
                                             points=pts,
                                             color_frame=color_frame,
                                             depth_frame=depth_frame,
                                             depth_scale=depth_scale)
            # TODO it's very slow and I'm giving up, not enough time, will clip it on client side.
            # filtered_vertices, filtered_faces = clip_mesh(vert, face, landmarks3d)
            print("[INFO] Everything's ready, passing back to Thrift...")
            return image, landmarks2d, landmarks3d, [vert, col, face]

    except Exception as ex:
        print(ex)
    finally:
        pipeline.stop()
