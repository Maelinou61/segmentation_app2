package com.example.segmentationapp2;

import android.graphics.Bitmap;
import android.os.AsyncTask;
import android.util.Base64;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.concurrent.TimeUnit;

import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class SendRequestTask extends AsyncTask<Void, Void, int[][]>{

    private Bitmap image;
    private PredictionListener listener;

    public SendRequestTask(Bitmap image, PredictionListener listener){
        this.image = image;
        this.listener = listener;
    }

    @Override
    protected int[][] doInBackground(Void... params){
        try{
            // Convert image in base64
            ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
            image.compress(Bitmap.CompressFormat.JPEG, 100, byteArrayOutputStream);
            byte[] byteArray = byteArrayOutputStream.toByteArray();

            //Build HTTP request
            OkHttpClient client = new OkHttpClient.Builder()
                    .connectTimeout(60000, TimeUnit.MILLISECONDS)
                    .build();

            MediaType mediaType = MediaType.parse("image/jpeg");

            RequestBody requestBody = new MultipartBody.Builder()
                    .setType(MultipartBody.FORM)
                    .addFormDataPart("image", "image.jpg", RequestBody.create(mediaType, byteArray))
                    .build();

            //IPv4 adress of the server
            Request request = new Request.Builder()
                    .url("http://192.168.152.24:5000/predict")
                    .post(requestBody)
                    .build();
            //Send the request to the flask app which is on a school server with a running xampp instance
            Response response = client.newCall(request).execute();
            if (response.isSuccessful()) {
                String predictionResponse = response.body().string();
                JSONObject jsonObject = new JSONObject(predictionResponse);
                JSONArray predictionArray = jsonObject.getJSONArray("result");

                // Convert the prediction into a floatting table
                int[][] prediction = new int[320][320];
                for (int i = 0; i < 320; i++) {
                    JSONArray row = predictionArray.getJSONArray(i);
                    for (int j = 0; j < 320; j++) {
                        prediction[i][j] = row.getInt(j);
                    }
                }
                return prediction;
            }
            else{
                return null;
            }

        } catch (IOException | JSONException ex){
            ex.printStackTrace();
            return null;
        }
    }

    @Override
    protected void onPostExecute(int[][] prediction){
        if (listener !=null && prediction != null){
            listener.onPredictionReceived(prediction);
        }
        else {
            Log.e("SendRequestTask", "Failed to receive prediction");
        }
    }
}
