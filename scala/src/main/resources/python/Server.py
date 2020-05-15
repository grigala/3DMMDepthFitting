import socket
import sys
import os
import time

sys.path.extend([os.path.abspath("..")])

from Rs import RealSenseService

from PIL import Image
from thrift.protocol import TBinaryProtocol
from thrift.server import TServer
from thrift.transport import *

from Rs.ttypes import CaptureResult
from conversions import image_to_thrift_image, landmarks_2d_to_thrift_landmarks_2d, \
    landmarks_to_thrift_landmarks, pc_to_thrift_pc
from rs_pipeline import start_pipeline


class RsPipelineHandler:
    @staticmethod
    def capture(gui: bool) -> CaptureResult:
        # ms = lambda: int(round(time.time() * 1000)) # milliseconds lambda
        print('[PyServer] Got a new request, starting capture pipeline...')
        start = time.time()
        try:
            img, lm2d, lm, pcl = start_pipeline(gui)  # Needs an additional keyboard input to capture image
            image = image_to_thrift_image(Image.fromarray(img))
            landmarks_2d = landmarks_2d_to_thrift_landmarks_2d(lm2d)
            landmarks = landmarks_to_thrift_landmarks(lm)
            point_cloud_mesh = pc_to_thrift_pc(pcl[0], pcl[1], pcl[2])
            end = time.time()
            print("[PyServer] Work finished. Single threaded execution took:{:f}s".format(end - start))
            capture_result = CaptureResult(image, landmarks_2d, landmarks, point_cloud_mesh)
            print('[PyServer] Sending data to client -> ', type(capture_result))
            return capture_result
        except Exception as ex:
            print("Ooops! Something went wrong! Please restart server.", ex)



if __name__ == '__main__':
    hostname = socket.gethostname()
    # ip = socket.gethostbyname(hostname)
    ip = sys.argv[1].strip()  # '127.0.0.1'
    port = sys.argv[2].strip()  # '9000'
    handler = RsPipelineHandler
    processor = RealSenseService.Processor(handler)
    transport = TSocket.TServerSocket(host=ip, port=port)
    tfactory = TTransport.TBufferedTransportFactory()
    pfactory = TBinaryProtocol.TBinaryProtocolAcceleratedFactory()

    server = TServer.TSimpleServer(processor, transport, tfactory, pfactory)
    print('[Server] Waiting for incoming connections on port', port)
    server.serve()
