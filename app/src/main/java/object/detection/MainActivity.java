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
import android.graphics.ImageDecoder;

import org.opencv.android.BaseLoaderCallback;
import org.opencv.android.LoaderCallbackInterface;
import org.opencv.android.OpenCVLoader;
import org.opencv.android.Utils;
import org.opencv.core.Mat;
import org.opencv.imgproc.Imgproc;
import org.opencv.core.CvType;

public class MainActivity extends AppCompatActivity {
    private static final int PICK_IMG = 100;
    ImageView imageView;
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
                Bitmap bitmapContainer = ImageDecoder.decodeBitmap(sourceContainer);
                bitmapContainer = bitmapContainer.copy(Bitmap.Config.ARGB_8888, true);
                bitmapContainer = detectObject(bitmapContainer);
                imageView.setImageBitmap(bitmapContainer);
            }catch(IOException e){
                e.printStackTrace();
            }
        }
    }
    private Bitmap detectObject(Bitmap input){
        // prepare the image for processing
        Mat imgMat = new Mat(input.getWidth(), input.getHeight(), CvType.CV_8UC4);
        Utils.bitmapToMat(input, imgMat);
        Imgproc.cvtColor(imgMat, imgMat, Imgproc.COLOR_RGB2BGRA);
        Mat imgResult = imgMat.clone();

        // processing
        Imgproc.Canny(imgMat, imgResult, 80, 90);

        // return new Bitmap instance
        Bitmap imgBitmap = Bitmap.createBitmap(imgResult.cols(), imgResult.rows(), Bitmap.Config.ARGB_8888);
        Utils.matToBitmap(imgResult, imgBitmap);
        return imgBitmap;
    }
}
