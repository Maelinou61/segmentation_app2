package com.example.segmentationapp2;

import android.graphics.Bitmap;
import android.graphics.Color;
import android.util.Log;

import org.opencv.android.Utils;
import org.opencv.core.Core;
import org.opencv.core.Mat;
import org.opencv.core.MatOfPoint;
import org.opencv.core.MatOfPoint2f;
import org.opencv.core.Point;
import org.opencv.core.Scalar;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class ImageUtils {
    public static Bitmap binarizeMask(int[][] prediction, float threshold, int width, int height) {
        Bitmap mask = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);

        // create the mask
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                if (prediction[y][x] > threshold) {
                    mask.setPixel(x, y, Color.WHITE);
                } else {
                    mask.setPixel(x, y, Color.BLACK);
                }
            }
        }
        return mask;
    }

    public static float[] calculateRealArea(Bitmap mask, Bitmap calibration){
        //Real metrics of the calibration
        float radiusCalibrationCm = 1.35F;
        float sideLengthCalibrationCm = 3.5F;

        //Convert the pixel to real measure
        int pixelRadius = calculateCalibrationRadius(calibration);
        float conversionFactor = radiusCalibrationCm / pixelRadius;

        Mat maskMat = new Mat();
        Utils.bitmapToMat(mask, maskMat);

        Mat grayMaskMat = new Mat();
        Imgproc.cvtColor(maskMat, grayMaskMat, Imgproc.COLOR_BGR2GRAY);

        //Calculate the area
        int areaWoundPixels = Core.countNonZero(grayMaskMat);
        float woundArea = conversionFactor*conversionFactor*areaWoundPixels;

        //Calculate the perimeter
        List<MatOfPoint> contours = new ArrayList<>();
        Mat hierarchy = new Mat();
        Imgproc.findContours(grayMaskMat, contours, hierarchy, Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE);

        float woundPerimiter = 0f;
        for(MatOfPoint contour : contours){
            woundPerimiter += Imgproc.arcLength(new MatOfPoint2f(contour.toArray()), true) * conversionFactor;
        }

        return new float[] {woundArea, woundPerimiter};
    }


    public static int calculateCalibrationRadius(Bitmap calibration) {
        int[] circleInfo = new int[3];
        double maxConfidence = 0;
        int radius = 0;
        int abs_radius = Math.min(calibration.getHeight() / 2, calibration.getWidth() / 2);
        Bitmap resultBitmap;

        //Pre-process of the bitmap image before the circle detection
        Mat matCalibration = new Mat();
        Utils.bitmapToMat(calibration, matCalibration);

        Mat grayCalibration = new Mat();
        Imgproc.cvtColor(matCalibration, grayCalibration, Imgproc.COLOR_BGR2GRAY);

        Imgproc.GaussianBlur(grayCalibration, grayCalibration, new Size(5,5), 0);

        Mat hsvImage = new Mat();
        Imgproc.cvtColor(matCalibration, hsvImage, Imgproc.COLOR_BGR2HSV);

        Scalar lowerGreen = new Scalar(35, 50, 50); // BGR
        Scalar upperGreen = new Scalar(80, 255, 255); // BGR

        Mat greenMask = new Mat();
        Core.inRange(hsvImage, lowerGreen, upperGreen, greenMask);

        Mat edgesMasked = new Mat();
        grayCalibration.copyTo(edgesMasked, greenMask);

        Mat circles = new Mat();
        Imgproc.HoughCircles(edgesMasked, circles, Imgproc.HOUGH_GRADIENT, 1.5, 30, 50, 10, (int) Math.round(abs_radius * 0.6), (int) Math.round(abs_radius * 1.3));

        if (circles.cols() != 0) {
            for (int x = 0; x < circles.cols(); x++) {
                double[] c = circles.get(0, x);
                Point center = new Point(Math.round(c[0]), Math.round(c[1]));
                radius = (int) Math.round(c[2]);

                double confidence = 1.0 - (Math.abs(center.x - calibration.getWidth() / 2) + Math.abs(center.y - calibration.getHeight() / 2)) / (calibration.getWidth() / 2 + calibration.getHeight() / 2);

                //Recover only the circle with the max confidence
                if (confidence > maxConfidence) {
                    maxConfidence = confidence;
                    circleInfo[0] = (int) Math.round(c[0]);
                    circleInfo[1] = (int) Math.round(c[1]);
                    circleInfo[2] = radius;

                    // Draw the circle
                    Imgproc.circle(matCalibration, center, radius, new Scalar(0, 255, 0), 1, 8, 0);

                    // Draw the center of the circle
                    Imgproc.circle(matCalibration, center, 1, new Scalar(0, 0, 255), 1);

                    break;
                }
            }
            resultBitmap = Bitmap.createBitmap(calibration.getWidth(), calibration.getHeight(), Bitmap.Config.RGB_565);
            Utils.matToBitmap(matCalibration, resultBitmap);

            Log.d("ImageWithCircle", "Circle info: " + Arrays.toString(circleInfo));
        }
        return radius;
    }
}
