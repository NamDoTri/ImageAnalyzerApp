package object.detection;

import androidx.appcompat.app.AppCompatActivity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.ImageDecoder;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import android.graphics.ImageDecoder;

import org.opencv.android.BaseLoaderCallback;
import org.opencv.android.LoaderCallbackInterface;
import org.opencv.android.OpenCVLoader;
import org.opencv.android.Utils;
import org.opencv.core.Core;
import org.opencv.core.Mat;
import org.opencv.core.MatOfFloat;
import org.opencv.core.MatOfInt;
import org.opencv.core.MatOfPoint;
import org.opencv.core.MatOfPoint2f;
import org.opencv.core.Point;
import org.opencv.core.Rect;
import org.opencv.core.Scalar;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;
import org.opencv.core.CvType;

public class MainActivity extends AppCompatActivity {
    private static final int PICK_IMG = 100;
    ImageView imageView;
    ImageView histogramView;
    // Button buttonCamera;
    Button buttonLoadImage;
    Uri imageUri;
    private BaseLoaderCallback mLoaderCallback = new BaseLoaderCallback(this) {
        @Override
        public void onManagerConnected(int status) {
            switch (status) {
                case LoaderCallbackInterface.SUCCESS:
                {
                    Log.i("OpenCV", "OpenCV loaded successfully");
                } break;
                default:
                {
                    super.onManagerConnected(status);
                } break;
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        OpenCVLoader.initDebug();

        imageView = findViewById(R.id.imageView);
        histogramView = findViewById(R.id.histogramView);
        // buttonCamera = findViewById(R.id.buttonOpenCamera); //TODO: implement camera API
        buttonLoadImage = findViewById(R.id.buttonLoadImage);

        buttonLoadImage.setOnClickListener(new View.OnClickListener(){
            @Override
            public void onClick(View v){
                openGallery();
            }
        });
    }

    public void onResume()
    {
        super.onResume();
        if (!OpenCVLoader.initDebug()) {
            Log.d("OpenCV", "Internal OpenCV library not found. Using OpenCV Manager for initialization");
            OpenCVLoader.initAsync(OpenCVLoader.OPENCV_VERSION, this, mLoaderCallback);
        } else {
            Log.d("OpenCV", "OpenCV library found inside package. Using it!");
            mLoaderCallback.onManagerConnected(LoaderCallbackInterface.SUCCESS);
        }
    }

    private void openGallery(){
        Intent gallery = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.INTERNAL_CONTENT_URI);
        startActivityForResult(gallery, PICK_IMG); // call to onActivityResult
    }
    protected void onActivityResult(int requestCode, int resultCode, Intent data){
        super.onActivityResult(requestCode, resultCode, data);
        if( resultCode == RESULT_OK && requestCode == PICK_IMG){
            imageUri = data.getData(); // return an Uri instance
            ImageDecoder.Source sourceContainer = ImageDecoder.createSource(this.getContentResolver(), imageUri);
            try{
                // load data from Uri to a container of type Bitmap
                Bitmap bitmapContainer = ImageDecoder.decodeBitmap(sourceContainer);
                bitmapContainer = bitmapContainer.copy(Bitmap.Config.ARGB_8888, true);

                // do the processing and display it
                Bitmap histogramBitmapContainer = calcHist(bitmapContainer, histogramView.getWidth(), histogramView.getHeight());
                Bitmap sourceImagebitmapContainer = detectObject(bitmapContainer);
                imageView.setImageBitmap(sourceImagebitmapContainer);
                histogramView.setImageBitmap(histogramBitmapContainer);
            }catch(IOException e){
                e.printStackTrace();
            }
        }
    }
    private Bitmap detectObject(Bitmap input){
        Log.i("Customized", "detectObject called");
        // prepare the image for object detection
        Mat sourceImgMat = new Mat(input.getWidth(), input.getHeight(), CvType.CV_8UC4);
        Utils.bitmapToMat(input, sourceImgMat);
        Imgproc.cvtColor(sourceImgMat, sourceImgMat, Imgproc.COLOR_RGB2BGRA);
        Mat imgResult = sourceImgMat.clone();

        // remove noise for better object detection
        Mat blurred = new Mat();
        Imgproc.GaussianBlur(sourceImgMat, blurred, new Size(7, 7), 2);

        //convert image to grayscale to determine thresholds
        Imgproc.cvtColor(blurred, blurred, Imgproc.COLOR_BGRA2GRAY);
        Core.MinMaxLocResult thresholds = Core.minMaxLoc(blurred);
        Mat binImage = sourceImgMat.clone();
        Imgproc.Canny(blurred, binImage, thresholds.minVal+80.0, thresholds.maxVal-80.0);

        // morphological operations
        Mat dilateElement = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, new Size(24, 24));
        Mat erodeElement = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, new Size(12, 12));

        Mat morphOutput = new Mat();

        Imgproc.dilate(binImage, morphOutput, dilateElement);
        //Imgproc.dilate(morphOutput, morphOutput, dilateElement);

        Imgproc.erode(morphOutput, morphOutput, erodeElement);
        //Imgproc.erode(morphOutput, morphOutput, erodeElement);

        // find the contours and highlight them
        List<MatOfPoint> contours = new ArrayList<>();
        Mat hierarchy = new Mat();
        Mat morphOutput32S = morphOutput.clone();

        Log.i("Customized", "morphOutput32S " + morphOutput32S.toString());
        Imgproc.findContours(morphOutput, contours, hierarchy, Imgproc.RETR_CCOMP, Imgproc.CHAIN_APPROX_SIMPLE);
        Log.i("Customized", "Contours: " + contours.toString());
        if (hierarchy.size().height > 0 && hierarchy.size().width > 0)
        {
            // for each contour, display it in blue
            for (int idx = 0; idx >= 0; idx = (int) hierarchy.get(0, idx)[0])
            {
                Imgproc.drawContours(imgResult, contours, idx, new Scalar(250, 0, 0));
                MatOfPoint2f approxCurve = new MatOfPoint2f();
                MatOfPoint2f contour2f = new MatOfPoint2f( contours.get(idx).toArray() );
                //Processing on mMOP2f1 which is in type MatOfPoint2f
                double approxDistance = Imgproc.arcLength(contour2f, true)*0.02;
                Imgproc.approxPolyDP(contour2f, approxCurve, approxDistance, true);

                //Convert back to MatOfPoint
                MatOfPoint points = new MatOfPoint( approxCurve.toArray() );

                // Get bounding rect of contour
                Rect rect = Imgproc.boundingRect(points);

                Imgproc.rectangle(imgResult, new Point(rect.x, rect.y), new Point(rect.x + rect.width, rect.y + rect.height), new Scalar(255, 0, 0, 255), 3);
            }
        }

        // return new Bitmap instance
        Imgproc.cvtColor(imgResult, imgResult, Imgproc.COLOR_BGRA2RGB);
        Bitmap imgBitmap = Bitmap.createBitmap(imgResult.cols(), imgResult.rows(), Bitmap.Config.ARGB_8888);
        Utils.matToBitmap(imgResult, imgBitmap);
        return imgBitmap;
    }
    private Bitmap calcHist(Bitmap input, int histWidth, int histHeight){
        // prepare Mat object for processing
        Mat sourceImgMat = new Mat(input.getWidth(), input.getHeight(), CvType.CV_8UC4);
        Utils.bitmapToMat(input, sourceImgMat);
        Imgproc.cvtColor(sourceImgMat, sourceImgMat, Imgproc.COLOR_RGB2GRAY);

        // calculate the histogram
        MatOfInt histSize = new MatOfInt(256);
        MatOfInt channels = new MatOfInt(0);
        MatOfFloat histRange = new MatOfFloat(0, 256);

        Mat histGray = new Mat();

        Imgproc.calcHist(Arrays.asList(sourceImgMat), channels, new Mat(), histGray, histSize, histRange, false);

        // draw histogram on a new Mat object
        int binWidth = (int)Math.round(histWidth/histSize.get(0,0)[0]);
        Mat histImage = new Mat(histHeight, histWidth, CvType.CV_8UC3, new Scalar(0,0,0));
        Core.normalize(histGray, histGray, 0, histImage.rows(), Core.NORM_MINMAX, -1, new Mat());
        Core.normalize(histGray, histGray, histImage.cols(), 0, Core.NORM_MINMAX);
        for(int i = 0; i < histSize.get(0,0)[0]; i++){
            Point point1 = new Point(binWidth * (i - 1), histHeight - Math.round(histGray.get(i - 1, 0)[0]));
            Point point2 = new Point(binWidth * i, histHeight - Math.round(histGray.get(i, 0)[0]));
            Imgproc.line(histImage, point1, point2, new Scalar(255, 0, 0), 2, 8, 0);
        }

        // convert it to Bitmap
        Bitmap imgBitmap = Bitmap.createBitmap(histImage.cols(), histImage.rows(), Bitmap.Config.ARGB_8888);
        Utils.matToBitmap(histImage, imgBitmap);
        return imgBitmap;
    }
}
