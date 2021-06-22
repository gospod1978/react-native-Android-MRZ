package com.ipaymix.mrzreader.mlkit.text;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ImageFormat;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.YuvImage;
import android.os.Handler;
import android.util.Base64;
import android.util.Log;

import androidx.annotation.NonNull;

//import com.ipaymix.mrzreader.mlkit.other.FrameMetadata;
import com.ipaymix.mrzreader.mlkit.other.FrameMetadata2;
import com.ipaymix.mrzreader.mlkit.other.GraphicOverlay;
import com.ipaymix.mrzreader.model.DocType;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;
import com.google.mlkit.common.MlKitException;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.Text;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.TextRecognizer;

import net.sf.scuba.data.Gender;

import org.jmrtd.lds.icao.MRZInfo;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class TextRecognitionProcessor {


    private static final String TAG = TextRecognitionProcessor.class.getName();

    private final TextRecognizer textRecognizer;

    private ResultListener resultListener;

    private String scannedTextBuffer;

    private DocType docType;

    public static final String TYPE_PASSPORT = "P<";

    public static final String TYPE_ID_CARD = "I";

    public static final String ID_CARD_TD_1_LINE_1_REGEX = "([I][A-Z0-9<]{1})([A-Z]{3})([A-Z0-9<]{24})";

    public static final String ID_CARD_TD_1_LINE_2_REGEX = "([0-9]{6})([0-9]{1})([M|F|X|<]{1})([0-9]{6})([0-9]{1})([A-Z]{3})([A-Z0-9<]{11})([0-9]{1})";

    public static final String ID_CARD_TD_1_LINE_3_REGEX = "([A-Z0-9<]{30})";

    public static final String PASSPORT_TD_3_LINE_1_REGEX = "(P[A-Z0-9<]{1})([A-Z]{3})([A-Z0-9<]{39})";

    public static final String PASSPORT_TD_3_LINE_2_REGEX = "([A-Z0-9<]{9})([0-9]{1})([A-Z]{3})([0-9]{6})([0-9]{1})([M|F|X|<]{1})([0-9]{6})([0-9]{1})([A-Z0-9<]{14})([0-9]{1})([0-9]{1})";

    // Whether we should ignore process(). This is usually caused by feeding input data faster than
    // the model can handle.
    private final AtomicBoolean shouldThrottle = new AtomicBoolean(false);

    public TextRecognitionProcessor(DocType docType, ResultListener resultListener) {
        this.docType = docType;
        this.resultListener = resultListener;
        textRecognizer = TextRecognition.getClient();
    }

    //region ----- Exposed Methods -----


    public void stop() {
        textRecognizer.close();
    }


    public void process(ByteBuffer data, FrameMetadata2 frameMetadata, GraphicOverlay graphicOverlay) throws MlKitException {

        if (shouldThrottle.get()) {
            return;
        }
        InputImage inputImage = InputImage.fromByteBuffer(data,
                frameMetadata.getWidth(),
                frameMetadata.getHeight(),
                frameMetadata.getRotation(),
                InputImage.IMAGE_FORMAT_NV21);

        detectInVisionImage(inputImage, frameMetadata, graphicOverlay);
    }

    public static Bitmap getBitmap(ByteBuffer data, FrameMetadata2 metadata) {
        data.rewind();
        byte[] imageInBuffer = new byte[data.limit()];
        data.get(imageInBuffer, 0, imageInBuffer.length);
        try {
            YuvImage image =
                    new YuvImage(
                            imageInBuffer, ImageFormat.NV21, metadata.getWidth(), metadata.getHeight(), null);
            ByteArrayOutputStream stream = new ByteArrayOutputStream();
            image.compressToJpeg(new Rect(0, 0, metadata.getWidth(), metadata.getHeight()), 80, stream);

            Bitmap bmp = BitmapFactory.decodeByteArray(stream.toByteArray(), 0, stream.size());

            stream.close();
            return rotateBitmap(bmp, metadata.getRotation(), false, false);
        } catch (Exception e) {
            Log.e("VisionProcessorBase", "Error: " + e.getMessage());
        }
        return null;
    }

    private static Bitmap rotateBitmap(
            Bitmap bitmap, int rotationDegrees, boolean flipX, boolean flipY) {
        Matrix matrix = new Matrix();

        // Rotate the image back to straight.
        matrix.postRotate(rotationDegrees);

        // Mirror the image along the X or Y axis.
        matrix.postScale(flipX ? -1.0f : 1.0f, flipY ? -1.0f : 1.0f);
        Bitmap rotatedBitmap =
                Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true);

        // Recycle the old bitmap if it has changed.
        if (rotatedBitmap != bitmap) {
            bitmap.recycle();
        }
        return rotatedBitmap;
    }

    //endregion

    //region ----- Helper Methods -----

    protected Task<Text> detectInImage(InputImage image) {
        return textRecognizer.process(image);
    }


    protected void onSuccess(@NonNull Text results, @NonNull FrameMetadata2 frameMetadata, @NonNull GraphicOverlay graphicOverlay) {

        graphicOverlay.clear();

        scannedTextBuffer = "";

        List<Text.TextBlock> blocks = results.getTextBlocks();
        for (int i = 0; i < blocks.size(); i++) {
            List<Text.Line> lines = blocks.get(i).getLines();
            for (int j = 0; j < lines.size(); j++) {
                List<Text.Element> elements = lines.get(j).getElements();
                for (int k = 0; k < elements.size(); k++) {
                    filterScannedText(graphicOverlay, elements.get(k));
                }
            }
        }
    }

    protected void onSuccess(@NonNull Text results, @NonNull FrameMetadata2 frameMetadata, @NonNull GraphicOverlay graphicOverlay, InputImage inputImage) {

        graphicOverlay.clear();

        scannedTextBuffer = "";

        List<Text.TextBlock> blocks = results.getTextBlocks();
        for (int i = 0; i < blocks.size(); i++) {
            List<Text.Line> lines = blocks.get(i).getLines();
            for (int j = 0; j < lines.size(); j++) {
                List<Text.Element> elements = lines.get(j).getElements();
                for (int k = 0; k < elements.size(); k++) {
                    filterScannedText(graphicOverlay, elements.get(k), inputImage, frameMetadata);
                }
            }
        }
    }

    private void filterScannedText(GraphicOverlay graphicOverlay, Text.Element element, InputImage inputImage, @NonNull FrameMetadata2 frameMetadata) {
        GraphicOverlay.Graphic textGraphic = new TextGraphic(graphicOverlay, element, Color.GREEN);
//        Log.i("textGraphic" , "Gettext " + element.getText());
        scannedTextBuffer += element.getText();
        scannedTextBuffer = scannedTextBuffer.toUpperCase();
        if(docType == DocType.ID_CARD) {
            if(scannedTextBuffer.length() >= 90){

                scannedTextBuffer = scannedTextBuffer.substring(scannedTextBuffer.length()-90);
                if(scannedTextBuffer.length() == 90){
                    graphicOverlay.add(textGraphic);
                    String firstLine = scannedTextBuffer.substring(0,30);
                    String secondLine = scannedTextBuffer.substring(30,60);
                    String thirdLine = scannedTextBuffer.substring(60,90);

                    Pattern patternIDCardTD1Line1 = Pattern.compile(ID_CARD_TD_1_LINE_1_REGEX);
                    Matcher matcherIDCardTD1Line1 = patternIDCardTD1Line1.matcher(firstLine);

                    Pattern patternIDCardTD1Line2 = Pattern.compile(ID_CARD_TD_1_LINE_2_REGEX);
                    Matcher matcherIDCardTD1Line2 = patternIDCardTD1Line2.matcher(secondLine);

                    Pattern patternIDCardTD1Line3 = Pattern.compile(ID_CARD_TD_1_LINE_3_REGEX);
                    Matcher matcherIDCardTD1Line3 = patternIDCardTD1Line3.matcher(thirdLine);

                    if (    matcherIDCardTD1Line1.find() &&
                            matcherIDCardTD1Line2.find() &&
                            matcherIDCardTD1Line3.find() ){
                            graphicOverlay.add(textGraphic);


                        MRZInfo mrzInfo = null;
                        String wholething = "";
                        try {
                             wholething = firstLine+secondLine+thirdLine;
                            mrzInfo = new MRZInfo(wholething);
                        }catch (Exception e){
                            e.printStackTrace();
                        }
                        if (mrzInfo != null)
//                            Log.i("mrzInfo","resullll000" + mrzInfo.getDateOfExpiry());
                            finishScanningWithImage(mrzInfo, inputImage, frameMetadata, wholething);
                    }
                }
            }
        } else if (docType == DocType.PASSPORT) {

            Pattern patternPassportTD3Line1 = Pattern.compile(PASSPORT_TD_3_LINE_1_REGEX);
            Matcher matcherPassportTD3Line1 = patternPassportTD3Line1.matcher(scannedTextBuffer);

            Pattern patternPassportTD3Line2 = Pattern.compile(PASSPORT_TD_3_LINE_2_REGEX);
            Matcher matcherPassportTD3Line2 = patternPassportTD3Line2.matcher(scannedTextBuffer);

            if(matcherPassportTD3Line1.find() && matcherPassportTD3Line2.find()) {
                graphicOverlay.add(textGraphic);
                String line2 = matcherPassportTD3Line2.group(0);
                String documentNumber = line2.substring(0, 9);
                documentNumber = documentNumber.replace("O", "0");
                String dateOfBirthDay = line2.substring(13, 19);
                String expiryDate = line2.substring(21, 27);

                Log.d(TAG, "Scanned Text Buffer Passport ->>>> " + "Doc Number: " + documentNumber + " DateOfBirth: " + dateOfBirthDay + " ExpiryDate: " + expiryDate);

                MRZInfo mrzInfo = buildTempMrz(documentNumber, dateOfBirthDay, expiryDate);

                if (mrzInfo != null)
                    finishScanning(mrzInfo);
            }
        }
    }

    private void filterScannedText(GraphicOverlay graphicOverlay, Text.Element element) {
        GraphicOverlay.Graphic textGraphic = new TextGraphic(graphicOverlay, element, Color.GREEN);
        scannedTextBuffer += element.getText();
        scannedTextBuffer = scannedTextBuffer.toUpperCase();
        if(docType == DocType.ID_CARD) {
            if(scannedTextBuffer.length() >= 90){

                scannedTextBuffer = scannedTextBuffer.substring(scannedTextBuffer.length()-90);
                if(scannedTextBuffer.length() == 90){
                    String firstLine = scannedTextBuffer.substring(0,30);
                    String secondLine = scannedTextBuffer.substring(30,60);
                    String thirdLine = scannedTextBuffer.substring(60,90);

                    Pattern patternIDCardTD1Line1 = Pattern.compile(ID_CARD_TD_1_LINE_1_REGEX);
                    Matcher matcherIDCardTD1Line1 = patternIDCardTD1Line1.matcher(firstLine);

                    Pattern patternIDCardTD1Line2 = Pattern.compile(ID_CARD_TD_1_LINE_2_REGEX);
                    Matcher matcherIDCardTD1Line2 = patternIDCardTD1Line2.matcher(secondLine);

                    Pattern patternIDCardTD1Line3 = Pattern.compile(ID_CARD_TD_1_LINE_3_REGEX);
                    Matcher matcherIDCardTD1Line3 = patternIDCardTD1Line3.matcher(thirdLine);

                    if (    matcherIDCardTD1Line1.find() &&
                            matcherIDCardTD1Line2.find() &&
                            matcherIDCardTD1Line3.find() ){
                        graphicOverlay.add(textGraphic);

                        MRZInfo mrzInfo = null;
                        try {
                            String wholething = firstLine+secondLine+thirdLine;
                            mrzInfo = new MRZInfo(wholething);
                        }catch (Exception e){
                            e.printStackTrace();
                        }
                        if (mrzInfo != null)
                            finishScanning(mrzInfo);
                    }
                }
            }
        } else if (docType == DocType.PASSPORT) {

            Pattern patternPassportTD3Line1 = Pattern.compile(PASSPORT_TD_3_LINE_1_REGEX);
            Matcher matcherPassportTD3Line1 = patternPassportTD3Line1.matcher(scannedTextBuffer);

            Pattern patternPassportTD3Line2 = Pattern.compile(PASSPORT_TD_3_LINE_2_REGEX);
            Matcher matcherPassportTD3Line2 = patternPassportTD3Line2.matcher(scannedTextBuffer);

            if(matcherPassportTD3Line1.find() && matcherPassportTD3Line2.find()) {
                graphicOverlay.add(textGraphic);
                String line2 = matcherPassportTD3Line2.group(0);
                String documentNumber = line2.substring(0, 9);
                documentNumber = documentNumber.replace("O", "0");
                String dateOfBirthDay = line2.substring(13, 19);
                String expiryDate = line2.substring(21, 27);

                Log.d(TAG, "Scanned Text Buffer Passport ->>>> " + "Doc Number: " + documentNumber + " DateOfBirth: " + dateOfBirthDay + " ExpiryDate: " + expiryDate);

                MRZInfo mrzInfo = buildTempMrz(documentNumber, dateOfBirthDay, expiryDate);

                if (mrzInfo != null)
                    finishScanning(mrzInfo);
            }
        }
    }

    protected void onFailure(@NonNull Exception e) {
        Log.w(TAG, "Text detection failed." + e);
        resultListener.onError(e);
    }

    private void detectInVisionImage(InputImage image, final FrameMetadata2 metadata, final GraphicOverlay graphicOverlay) {

        detectInImage(image)
                .addOnSuccessListener(
                        new OnSuccessListener<Text>() {
                            @Override
                            public void onSuccess(Text results) {
                                shouldThrottle.set(false);
                                TextRecognitionProcessor.this.onSuccess(results, metadata, graphicOverlay, image);
                            }
                        })
                .addOnFailureListener(
                        new OnFailureListener() {
                            @Override
                            public void onFailure(@NonNull Exception e) {
                                shouldThrottle.set(false);
                                TextRecognitionProcessor.this.onFailure(e);
                            }
                        });
        // Begin throttling until this frame of input has been processed, either in onSuccess or
        // onFailure.
        shouldThrottle.set(true);
    }


    private void finishScanning(final MRZInfo mrzInfo) {
        try {
            if(isMrzValid(mrzInfo)) {
                // Delay returning result 1 sec. in order to make mrz text become visible on graphicOverlay by user
                // You want to call 'resultListener.onSuccess(mrzInfo)' without no delay
                new Handler().postDelayed(() -> resultListener.onSuccess(mrzInfo), 1000);
            }

        } catch(Exception exp) {
            Log.d(TAG, "MRZ DATA is not valid");
        }
    }



    private void finishScanningWithImage(final MRZInfo mrzInfo, InputImage inputImage, FrameMetadata2 frameMetadata2, String mrz) {
        try {
            if (isMrzValid(mrzInfo)) {
                // Delay returning result 1 sec. in order to make mrz text become visible on graphicOverlay by user
                // You want to call 'resultListener.onSuccess(mrzInfo)' without no delay

              Bitmap bmp =   getBitmap(inputImage.getByteBuffer(), frameMetadata2);
//                Log.i("mrzInfo","resullll111" + mrzInfo.getDateOfExpiry());
                new Handler().postDelayed(() -> resultListener.onSuccess(mrzInfo, bmp, mrz), 1000);
//                resultListener.onSuccess(mrzInfo, inputImage);
            }

        } catch (Exception exp) {
            exp.printStackTrace();
            Log.d(TAG, "MRZ DATA is not valid");
        }

    }

    private MRZInfo buildTempMrz(String documentNumber, String dateOfBirth, String expiryDate) {
        MRZInfo mrzInfo = null;
        try {
            mrzInfo = new MRZInfo("P","NNN", "", "", documentNumber, "NNN", dateOfBirth, Gender.UNSPECIFIED, expiryDate, "");
        } catch (Exception e) {
            Log.d(TAG, "MRZInfo error : " + e.getLocalizedMessage());
        }

        return mrzInfo;
    }

    private boolean isMrzValid(MRZInfo mrzInfo) {
        return mrzInfo.getDocumentNumber() != null && mrzInfo.getDocumentNumber().length() >= 8 &&
                mrzInfo.getDateOfBirth() != null && mrzInfo.getDateOfBirth().length() == 6 &&
                mrzInfo.getDateOfExpiry() != null && mrzInfo.getDateOfExpiry().length() == 6;
    }

    public interface ResultListener {
        void onSuccess(MRZInfo mrzInfo);
        void onSuccess(MRZInfo mrzInfo, Bitmap inputImage, String mrz);
        void onError(Exception exp);
    }
}

