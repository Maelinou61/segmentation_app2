package com.example.segmentationapp2;

import static android.app.Activity.RESULT_OK;
import static androidx.core.content.ContextCompat.checkSelfPermission;

import static java.lang.Math.min;
import static java.lang.Math.round;

import android.Manifest;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.AssetFileDescriptor;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.media.ThumbnailUtils;
import android.net.Uri;
import android.os.Bundle;

import androidx.annotation.Nullable;
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
import com.example.segmentationapp2.ml.Detect;

import org.opencv.android.OpenCVLoader;
import org.opencv.android.Utils;
import org.tensorflow.lite.DataType;
import org.tensorflow.lite.Interpreter;
import org.tensorflow.lite.support.tensorbuffer.TensorBuffer;
import org.opencv.core.*;
import org.opencv.imgproc.Imgproc;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;

/**
 * A simple {@link Fragment} subclass.
 * Use the {@link SegmentationFragment#newInstance} factory method to
 * create an instance of this fragment.
 */
public class SegmentationFragment extends Fragment implements PredictionListener{

    private FragmentSegmentationBinding binding;
    private Interpreter interpreter;

    Button gallery, camera, measurement;
    ImageView imageView;
    TextView areaTextView, perimeterTextView, displayError;
    int imageSize = 320;
    int cropWidthDimension;
    int cropHeightDimension;
    Bitmap originalImage;
    ArrayList<Bitmap> wounds;
    Bitmap calibration;
    private int numberOfPredictions = 0;
    private int totalPredictions = 0;
    float realArea, realPerimeter;

    private static final float confidenceThreshold = 0.1f;

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState){
        super.onViewCreated(view, savedInstanceState);

        binding = FragmentSegmentationBinding.bind(view);

        camera = binding.takePicture;
        gallery = binding.gallery;
        measurement = binding.measurement;

        imageView = binding.imageView;

        areaTextView = binding.displayArea;
        perimeterTextView = binding.displayPerimeter;
        displayError = binding.displayError;

        camera.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if(checkSelfPermission(requireContext(), Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED){
                    Intent cameraIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
                    startActivityForResult(cameraIntent, 3);
                }
                else{
                    requestPermissions(new String[]{Manifest.permission.CAMERA}, 100);
                }
            }
        });

        gallery.setOnClickListener(new View.OnClickListener()
        {
            @Override
            public void onClick(View view){
                Intent cameraIntent = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
                startActivityForResult(cameraIntent, 1);
            }
        });

        //Call the method which calculate the area and the perimeter of the wound
        measurement.setOnClickListener(new View.OnClickListener(){
            @Override
            public void onClick(View v) {
                createMask(wounds);
            }
        });


    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode == RESULT_OK) {
            if (requestCode ==3){
                //Convert the image into a Bitmap
                originalImage = (Bitmap) data.getExtras().get("data");
                int dimension = min(originalImage.getWidth(), originalImage.getHeight());
                originalImage = ThumbnailUtils.extractThumbnail(originalImage, dimension, dimension);

                areaTextView.setVisibility(View.INVISIBLE);
                perimeterTextView.setVisibility(View.INVISIBLE);
                displayError.setVisibility(View.INVISIBLE);

                this.wounds = null;
                this.calibration = null;

                imageView.setImageBitmap(originalImage);

                Bitmap image = Bitmap.createScaledBitmap(originalImage, imageSize, imageSize, false);

                //Call the method to create the bounding boxes arround the roi
                createBoundingBoxes(image);
            }
            else{
                Uri dat = data.getData();
                Bitmap image = null;
                try {
                    image = MediaStore.Images.Media.getBitmap(this.getActivity().getContentResolver(), dat);
                } catch (IOException e){
                    e.printStackTrace();
                }

                areaTextView.setVisibility(View.INVISIBLE);
                perimeterTextView.setVisibility(View.INVISIBLE);
                displayError.setVisibility(View.INVISIBLE);

                this.wounds = null;
                this.calibration = null;

                imageView.setImageBitmap(image);

                image = Bitmap.createScaledBitmap(image, imageSize, imageSize, false);
                createBoundingBoxes(image);
            }
        }
    }

    public void createBoundingBoxes(Bitmap image){
        try {
            Detect model = Detect.newInstance(getContext());

            // Creates inputs for reference.
            TensorBuffer inputFeature0 = TensorBuffer.createFixedSize(new int[]{1, 320, 320, 3}, DataType.FLOAT32);

            // Creates the byteBuffer
            ByteBuffer byteBuffer = convertBitmapToByteBufferImage(image);
            inputFeature0.loadBuffer(byteBuffer);

            // Runs model inference and gets result.
            Detect.Outputs outputs = model.process(inputFeature0);
            TensorBuffer outputFeature0 = outputs.getOutputFeature0AsTensorBuffer();
            TensorBuffer outputFeature1 = outputs.getOutputFeature1AsTensorBuffer();
            TensorBuffer outputFeature2 = outputs.getOutputFeature2AsTensorBuffer();
            TensorBuffer outputFeature3 = outputs.getOutputFeature3AsTensorBuffer();


            //Recover the data of interest. Several prediction for one image
            float[] scores = outputFeature0.getFloatArray();
            float[] boxes = outputFeature1.getFloatArray();
            float[] classes = outputFeature3.getFloatArray();

            // Draw bounding boxes on a copy of the original image
            Bitmap imageWithBoundingBoxes = image.copy(Bitmap.Config.ARGB_8888, true);
            drawBoundingBoxes(imageWithBoundingBoxes, boxes, scores, classes);

            // Releases model resources if no longer used.
            model.close();

            measurement.setVisibility(View.VISIBLE);

        } catch (IOException e) {
            e.printStackTrace();
        }
    }


    public void drawBoundingBoxes(Bitmap image, float[] boxes, float[] scores, float[] classes){
        //copy of the original image where no bbox will be draw
        wounds = new ArrayList<>();
        Bitmap noBBImage = image.copy(image.getConfig(), true);
        Paint paint = new Paint();
        paint.setColor(Color.GREEN);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(2.0f);

        Canvas canvas = new Canvas(image);

        //Recover the coordinates of the bbox for each prediction if > threshold
        for(int i = 0; i < scores.length; i++){
            if(scores[i] > confidenceThreshold){
                float ymin = boxes[i*4] * image.getHeight();
                float xmin = boxes[i*4 + 1] * image.getWidth();
                float ymax = boxes[i*4 + 2] * image.getHeight();
                float xmax = boxes[i*4 + 3] * image.getWidth();

                //Draw the Bounding Box
                canvas.drawRect(xmin, ymin, xmax, ymax, paint);

                String label = "Classe " + (int) classes[i];
                //Recover the cropped image of the wound(s) and calibration on the image without bbox
                if(label.equals("Classe 0")){
                    label = "Chronic wound";
                    wounds.add(cropDetectedWounds(noBBImage, ymin, xmin, ymax, xmax, 20));
                }
                else{
                    label = "Calibration";
                    calibration = cropDetectedCalibration(noBBImage, ymin, xmin, ymax, xmax, -12);
                }
                //Draw the label and score
                String score = "Score : " + scores[i];
                canvas.drawText(label, xmin, ymin - 10, paint);
                canvas.drawText(score, xmin, ymin - 30, paint);
            }
        }
        imageView.setImageBitmap(image);
    }

    public void createMask(ArrayList<Bitmap> wounds) {
        realArea = 0f;
        realPerimeter = 0f;

        if (calibration != null) {
            if (wounds.size() == 1) {
                totalPredictions = wounds.size();
                SendRequestTask task = new SendRequestTask(wounds.get(0), this);
                //Execute the call of the API Flask which is on the school server
                try {
                    task.execute().get();
                } catch (InterruptedException | ExecutionException e) {
                    e.printStackTrace();
                }
            }

            //Change the fragment if several wounds detected
            //Allow to have a prediction of the area and perimeter for each wounds
            else if(wounds.size() >= 2){
                FragmentManager fragmentManager = getActivity().getSupportFragmentManager();
                SeveralWoundsDetected fragment = SeveralWoundsDetected.newInstance(wounds, calibration);
                FragmentTransaction fragmentTransaction = fragmentManager.beginTransaction();
                fragmentTransaction.replace(R.id.fragment_container_view, fragment);
                fragmentTransaction.commit();
                //Prevents the user that we change the view
                afficherPopup("Several wounds detected. All wounds will be shown and you will be able to calculate the area and perimeter of each. Click on back to go back to the previous wound and next to go to the next wound.");
            }
            else{
                displayError.setVisibility(View.VISIBLE);
            }
        }

        //Print an error message if no calibration panel or wound are detected
        else{
            displayError.setVisibility(View.VISIBLE);
        }
    }


    public Bitmap cropDetectedWounds(Bitmap image, float ymin, float xmin, float ymax, float xmax, int margin){
        int startX = Math.max((int) xmin - margin, 0);
        int startY = Math.max((int) ymin - margin, 0);
        int endX = min((int) xmax + margin, image.getWidth());
        int endY = min((int) ymax + margin, image.getHeight());

        int newWidth = endX - startX;
        int newHeight = endY - startY;

        Bitmap croppedWound = Bitmap.createBitmap(image, startX, startY, newWidth, newHeight);
        //Save the dimension of the cropped images to resize the wound after the segmentation
        cropHeightDimension = croppedWound.getHeight();
        cropWidthDimension = croppedWound.getWidth();
        return croppedWound;
    }

    public Bitmap cropDetectedCalibration(Bitmap image, float ymin, float xmin, float ymax, float xmax, int margin){
        int startX = Math.max((int) xmin - margin, 0);
        int startY = Math.max((int) ymin - margin, 0);
        int endX = min((int) xmax + margin, image.getWidth());
        int endY = min((int) ymax + margin, image.getHeight());

        int newWidth = endX - startX;
        int newHeight = endY - startY;

        Bitmap croppedCalibration = Bitmap.createBitmap(image, startX, startY, newWidth, newHeight);
        return croppedCalibration;
    }


    private ByteBuffer convertBitmapToByteBufferImage(Bitmap bitmap){
        ByteBuffer byteBuffer = ByteBuffer.allocateDirect(4*imageSize*imageSize*3);
        byteBuffer.order(ByteOrder.nativeOrder());

        int[] intValues = new int[imageSize*imageSize];
        bitmap.getPixels(intValues, 0, bitmap.getWidth(), 0, 0, bitmap.getWidth(), bitmap.getHeight());
        int pixel = 0;
        for (int i = 0; i<imageSize; i++){
            for(int j = 0; j<imageSize; j++) {
                int val = intValues[pixel++];
                byteBuffer.putFloat(((val>>16)&0xFF)/(255.f));
                byteBuffer.putFloat(((val>>8)&0xFF)/(255.f));
                byteBuffer.putFloat((val&0xFF)/(255.f));
            }
        }
        return byteBuffer;
    }


    /*
    private ByteBuffer convertBitmapToByteBufferMask(Bitmap bitmap){
        ByteBuffer byteBuffer = ByteBuffer.allocateDirect(4*imageSize*imageSize*3);
        byteBuffer.order(ByteOrder.nativeOrder());

        int[] intValues = new int[bitmap.getWidth()*bitmap.getHeight()];
        bitmap.getPixels(intValues, 0, bitmap.getWidth(), 0, 0, bitmap.getWidth(), bitmap.getHeight());
        int pixel = 0;
        for (int i = 0; i<imageSize; i++) {
            for (int j = 0; j < imageSize; j++) {
                int val = intValues[pixel++];
                byteBuffer.putFloat(((val >> 16) & 0xFF) * (1.f / 1));
                byteBuffer.putFloat(((val >> 8) & 0xFF) * (1.f / 1));
                byteBuffer.putFloat((val & 0xFF) * (1.f / 1));
            }
        }
        return byteBuffer;
    }*/


    public synchronized void onPredictionReceived(int[][] prediction){
        //Do prediction for each wound detected
        numberOfPredictions++;

        //Check if the server responded well
        if (prediction != null){
            Bitmap mask = ImageUtils.binarizeMask(prediction, 0.5f, 320, 320);
            mask = Bitmap.createScaledBitmap(mask, cropWidthDimension, cropHeightDimension, false);

            float[] realMeasure = ImageUtils.calculateRealArea(mask, calibration);
            realArea += realMeasure[0];
            realPerimeter += realMeasure[1];
        }
        else{
            Log.e("Prediction", "Failed to receive prediction");
            return;
        }

        if (numberOfPredictions == totalPredictions) {
            areaTextView.setVisibility(View.VISIBLE);
            perimeterTextView.setVisibility(View.VISIBLE);

            String areaText = "Estimated area: " + String.format("%.2f", realArea) + " cm²";
            String perimeterText = "Estimated perimeter: " + String.format("%.2f",realPerimeter) + " cm";

            areaTextView.setText(areaText);
            perimeterTextView.setText(perimeterText);

            //Reset the number of predictions for the next prediction
            numberOfPredictions = 0;
        }
    }


    //Print a popup when we pass to the several wounds fragment
    //Explain the process
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
        OpenCVLoader.initDebug();
        super.onCreate(savedInstanceState);
    }


    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.fragment_segmentation, container, false);
    }
}