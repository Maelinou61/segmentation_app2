package com.example.segmentationapp2;

import android.graphics.Bitmap;

public interface PredictionListener {
    void onPredictionReceived(int[][] prediction);
}
