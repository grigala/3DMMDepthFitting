import unittest

from python.Rs.ttypes import ThriftLandmark2D, ThriftPixel
from python.conversions import landmark_2d_to_thrift_landmark_2d


class Landmark2DTest(unittest.TestCase):
    def test_2d_landmark(self):
        fake_landmark = ["landmark.name", (100, 100)]
        fake_thrift_landmark = ThriftLandmark2D("landmark.name", ThriftPixel(100, 100))
        lm2d = landmark_2d_to_thrift_landmark_2d(fake_landmark)
        self.assertEqual(lm2d.name, fake_thrift_landmark.name)
        self.assertEqual(lm2d.pixels, fake_thrift_landmark.pixels)

if __name__ == "__main__":
    unittest.main()