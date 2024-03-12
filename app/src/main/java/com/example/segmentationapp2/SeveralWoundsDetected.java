package com.example.segmentationapp2;

import static androidx.core.content.ContextCompat.checkSelfPermission;

import android.Manifest;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.os.Bundle;

import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;

import android.provider.MediaStore;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import com.example.segmentationapp2.databinding.FragmentSegmentationBinding;
import com.example.segmentationapp2.databinding.FragmentSeveralWoundsDetectedBinding;

import org.opencv.android.Utils;
import org.opencv.core.Core;
import org.opencv.core.Mat;
import org.opencv.core.MatOfPoint;
import org.opencv.core.MatOfPoint2f;
import org.opencv.core.Point;
import org.opencv.core.Scalar;
import org.opencv.imgproc.Imgproc;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;

public class SeveralWoundsDetected extends Fragment implements PredictionListener{

    private FragmentSeveralWoundsDetectedBinding binding;
    Button homePage, measurement, next, back;
    ImageView imageView;
    TextView areaTextView, perimeterTextView;
    int imageSize = 320;
    int originalHeight;
    int originalWidth;
    int cropWidthDimension;
    int cropHeightDimension;
    ArrayList<Bitmap> wounds;
    int detection = 0;
    Bitmap calibration;
    Bitmap wound;
    float realArea, realPerimeter;

    public static SeveralWoundsDetected newInstance(ArrayList<Bitmap> wounds, Bitmap calibration) {
        SeveralWoundsDetected fragment = new SeveralWoundsDetected();
        Bundle args = new Bundle();
        args.putParcelableArrayList("wounds", wounds);
        args.putParcelable("calibration", calibration);

        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState){
        super.onViewCreated(view, savedInstanceState);

        binding = FragmentSeveralWoundsDetectedBinding.bind(view);

        homePage = binding.homePage;
        measurement = binding.measurement;
        next = binding.nextImage;
        back = binding.formerImage;

        imageView = binding.imageView;

        areaTextView = binding.displayArea;
        perimeterTextView = binding.displayPerimeter;

        Bundle args = getArguments();
        if (args != null) {
            wounds = args.getParcelableArrayList("wounds");
            calibration = args.getParcelable("calibration");
        }

        if (wounds != null && !wounds.isEmpty()) {
            imageView.setImageBitmap(wounds.get(detection));
            wound = wounds.get(detection);
            cropHeightDimension = wound.getHeight();
            cropWidthDimension = wound.getWidth();
        }

        homePage.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
                builder.setMessage("Are you sure to want to come back to the home page ?")
                        .setCancelable(false)
                        .setPositiveButton("Yes", new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                SegmentationFragment segmentationFragment = new SegmentationFragment();
                                FragmentManager fragmentManager = getActivity().getSupportFragmentManager();
                                fragmentManager.beginTransaction()
                                        .replace(R.id.fragment_container_view, segmentationFragment)
                                        .addToBackStack(null)
                                        .commit();
                            }
                        })
                        .setNegativeButton("No", new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                dialog.cancel();
                            }
                        });
                AlertDialog alert = builder.create();
                alert.show();
            }
        });



        measurement.setOnClickListener(new View.OnClickListener(){
            @Override
            public void onClick(View v) {
                createMask(wound);
            }
        });

        next.setOnClickListener(new View.OnClickListener(){
            public void onClick(View v) {
                if (detection < (wounds.size()-1)) {
                    detection++;
                    wound = wounds.get(detection);
                    cropHeightDimension = wound.getHeight();
                    cropWidthDimension = wound.getWidth();
                    imageView.setImageBitmap(wounds.get(detection));

                    areaTextView.setVisibility(View.INVISIBLE);
                    perimeterTextView.setVisibility(View.INVISIBLE);
                } else {
                    afficherPopup("No other wounds detected on the image.");
                }
            }
        });

        back.setOnClickListener(new View.OnClickListener(){
            public void onClick(View v) {
                if (detection != 0) {
                    detection--;
                    wound = wounds.get(detection);
                    cropHeightDimension = wound.getHeight();
                    cropWidthDimension = wound.getWidth();
                    imageView.setImageBitmap(wounds.get(detection));

                    areaTextView.setVisibility(View.INVISIBLE);
                    perimeterTextView.setVisibility(View.INVISIBLE);
                } else {
                    afficherPopup("No other wounds detected on the image.");
                }
            }
        });
    }

    public void createMask(Bitmap wound) {
        realArea = 0f;
        realPerimeter = 0f;

        SendRequestTask task = new SendRequestTask(wound, this);
        //Execute the call of the API Flask which is on the server
        try {
            task.execute().get();
        } catch (InterruptedException | ExecutionException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onPredictionReceived(int[][] prediction) {
        if (prediction != null){
            Bitmap mask = ImageUtils.binarizeMask(prediction, 0.5f, 320, 320);
            mask = Bitmap.createScaledBitmap(mask, cropWidthDimension, cropHeightDimension, true);
            float[] realMeasure = ImageUtils.calculateRealArea(mask, calibration);
            realArea = realMeasure[0];
            realPerimeter = realMeasure[1];
        }
        else{
            Log.e("Prediction", "Failed to receive prediction");
            return;
        }

        areaTextView.setVisibility(View.VISIBLE);
        perimeterTextView.setVisibility(View.VISIBLE);

        String areaText = "Estimated area: " + String.format("%.2f", realArea) + " cm²";
        String perimeterText = "Estimated perimeter: " + String.format("%.2f",realPerimeter) + " cm";

        areaTextView.setText(areaText);
        perimeterTextView.setText(perimeterText);
    }

    private void afficherPopup(String message) {
        AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
        builder.setMessage(message)
                .setCancelable(true)
                .setPositiveButton("OK", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {
                    }
                });
        AlertDialog alert = builder.create();
        alert.show();
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.fragment_several_wounds_detected, container, false);
    }
}