package com.ipaymix.mrzreader.mlkit.ui;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Rect;
import android.os.Bundle;
import android.util.Base64;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;

import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;

import com.ipaymix.mrzreader.R;
import com.ipaymix.mrzreader.mlkit.camera.CameraSource;
import com.ipaymix.mrzreader.mlkit.camera.CameraSourcePreview;
import com.ipaymix.mrzreader.mlkit.other.GraphicOverlay;
import com.ipaymix.mrzreader.mlkit.text.TextRecognitionProcessor;
import com.ipaymix.mrzreader.model.DocType;

import org.jmrtd.lds.icao.MRZInfo;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

public class CaptureActivity extends AppCompatActivity implements TextRecognitionProcessor.ResultListener {

    private CameraSource cameraSource = null;
    private CameraSourcePreview preview;
    private GraphicOverlay graphicOverlay;
    private ImageView testImage;
    private View finder;
    private ActionBar actionBar;
    private static final String JPEG_DATA_URI_PREFIX = "data:image/jpeg;base64,";

    public static final String MRZ_RESULT = "MRZ_RESULT";
    public static final String DOC_TYPE = "DOC_TYPE";

    private DocType docType = DocType.ID_CARD;


    private static String TAG = CaptureActivity.class.getSimpleName();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.mrz_reader_activity);


        if(getIntent().hasExtra(DOC_TYPE)) {
            docType = (DocType) getIntent().getSerializableExtra(DOC_TYPE);
            if(docType == DocType.PASSPORT) {
                actionBar.hide();
                setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
            }
        }

        preview = findViewById(R.id.camera_source_preview);
        if (preview == null) {
            Log.d(TAG, "Preview is null");
        }
        graphicOverlay = findViewById(R.id.graphics_overlay);
        if (graphicOverlay == null) {
            Log.d(TAG, "graphicOverlay is null");
        }
        testImage = findViewById(R.id.testImage);
        if (testImage == null) {
            Log.d(TAG, "testImage is null");
        }

        finder = findViewById(R.id.view_finder);
        if (finder == null) {
            Log.d(TAG, "testImage is null");
        }
        createCameraSource();
        startCameraSource();

    }

    @Override
    public void onResume() {
        super.onResume();
        Log.d(TAG, "onResume");
        startCameraSource();
    }

    /** Stops the camera. */
    @Override
    protected void onPause() {
        super.onPause();
        preview.stop();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (cameraSource != null) {
            cameraSource.release();
        }
    }

    private void createCameraSource() {

        if (cameraSource == null) {
            cameraSource = new CameraSource(this, graphicOverlay);
            cameraSource.setFacing(CameraSource.CAMERA_FACING_BACK);

        }

        cameraSource.setMachineLearningFrameProcessor(new TextRecognitionProcessor(docType, this));
    }

    private void startCameraSource() {
        if (cameraSource != null) {
            try {
                if (preview == null) {
                    Log.d(TAG, "resume: Preview is null");
                }
                if (graphicOverlay == null) {
                    Log.d(TAG, "resume: graphOverlay is null");
                }
                preview.start(cameraSource, graphicOverlay);
            } catch (IOException e) {
                Log.e(TAG, "Unable to start camera source.", e);
                cameraSource.release();
                cameraSource = null;
            }
        }
    }

    @Override
    public void onSuccess(MRZInfo mrzInfo) {

        Log.i("CHECKME", "DONT COME HERE ");
    }

    @Override
    public void onSuccess(MRZInfo mrzInfo, Bitmap inputImage, String mrz) {
        try{
            Intent returnIntent = getIntent();
            returnIntent.putExtra("mrzValue", mrz);
            returnIntent.putExtra("mrzValueBirth", mrzInfo.getDateOfBirth());
            Log.i("mrzInfo","resullll111" + mrzInfo.getDateOfExpiry());

            String image = toBase64(cropBitmap(inputImage), 80);

            returnIntent.putExtra("idImage", image);
            //---set the data to pass back---
            setResult(RESULT_OK, returnIntent);
            //---close the activity---
            finish();
        }catch (Exception e){
            e.printStackTrace();
        }


    }

    private Bitmap cropBitmap(Bitmap bitmap) {
        View v = (View) findViewById(R.id.view_finder);
        int x = v.getWidth();
        int y = v.getHeight();
        int top = v.getTop();
        int left= v.getLeft();

        Matrix matrix = new Matrix();

        Bitmap rotatedBitmap = Bitmap.createBitmap(bitmap, 0, 0,
                bitmap.getWidth(), bitmap.getHeight(), matrix, true);


        //get viewfinder border size and position on the screen
        View cameraV = (View) findViewById(R.id.camera_source_preview);

        int x2 = cameraV.getWidth();
        int y2 = cameraV.getHeight();

        float koefX = (float) rotatedBitmap.getWidth() /  x2;
        float koefY = (float) rotatedBitmap.getHeight() / y2;

        //calculate position and size for cropping
        int cropStartX = Math.round(left * koefX);
        int cropStartY = Math.round(top * koefY);

        int cropWidthX = Math.round(x * koefX);
        int cropHeightY = Math.round(y * koefY);
        //check limits and make crop
        Bitmap croppedBitmap = null;
        if (cropStartX + cropWidthX <= rotatedBitmap.getWidth() &&
                cropStartY + cropHeightY <= rotatedBitmap.getHeight()) {
            croppedBitmap = Bitmap.createBitmap(rotatedBitmap, cropStartX,
                    cropStartY, cropWidthX, cropHeightY);
        } else {
            croppedBitmap = null;
        }
        return croppedBitmap;
    }

    private static String toBase64(final Bitmap bitmap, final int quality) {
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        bitmap.compress(Bitmap.CompressFormat.JPEG, quality, byteArrayOutputStream);
        byte[] byteArray = byteArrayOutputStream.toByteArray();
        return JPEG_DATA_URI_PREFIX + Base64.encodeToString(byteArray, Base64.NO_WRAP);
    }

    @Override
    public void onError(Exception exp) {
        setResult(Activity.RESULT_CANCELED);
        finish();
    }
}