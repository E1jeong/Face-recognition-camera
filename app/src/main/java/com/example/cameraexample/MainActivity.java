package com.example.cameraexample;

//import androidx.appcompat.app.AlertDialog;
//import androidx.appcompat.app.AppCompatActivity;

import android.content.DialogInterface;
import android.content.Intent;
import android.content.res.AssetManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.media.ExifInterface;
import android.net.Uri;
import android.os.Bundle;
import android.annotation.TargetApi;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;
import android.support.v4.content.FileProvider;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.util.SparseIntArray;
import android.view.SurfaceView;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;

import org.opencv.android.BaseLoaderCallback;
import org.opencv.android.CameraBridgeViewBase;
import org.opencv.android.LoaderCallbackInterface;
import org.opencv.android.OpenCVLoader;
import org.opencv.android.Utils;
import org.opencv.core.Core;
import org.opencv.core.Mat;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.imgproc.Imgproc;
import org.opencv.objdetect.CascadeClassifier;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.concurrent.Semaphore;

import static android.Manifest.permission.CAMERA;
import static android.Manifest.permission.WRITE_EXTERNAL_STORAGE;
import static org.opencv.core.Core.findFile;
import static org.opencv.core.Core.findFileOrKeep;
import static org.opencv.imgcodecs.Imgcodecs.IMREAD_UNCHANGED;
import static org.opencv.imgcodecs.Imgcodecs.imread;

//카메라에 필터를 입히는 예제를 참고해서 만듬
public class MainActivity extends AppCompatActivity implements CameraBridgeViewBase.CvCameraViewListener2 {

    private static final String TAG = "MainActivity";
    private Mat matInput;
    private Mat matResult;

    //static final int REQUEST_TAKE_PHOTO = 1; // 지금 사용하고있지 않음,, 사진을 파일로 저장할때 사용하는 메소드 dispatchTakePictureIntent() 에 사용됨 (마지막 인텐트에서)
    private int filterMode = 0; // 카메라에서 필터들을 바꿔줄때 사용하는 변수, 0은 기본값(RGB모드)
    private int cameraSensorMode; // 카메라의 전방,후방모드를 바꿔줄때 사용하는 변수
    private String imageFilePath; // 이미지 저장할때 사용되는 변수
    private int filterButtonVisible = 0; // 필터버튼(기본모드, Gray, HSV등)들을 보였다가 안보였다가 제어하기 위해 만든 변수, onStart에서 초기화 한번 더해줬음(액티비티가 다시 나타날때를 대비해서)
    Singleton singleton = Singleton.getInstance(); // 현재 필터를 변경할때 filterMode의 값으로 제어하는데, 그 값을 고정시켜주기 위해서만 사용되고있음

    private CameraBridgeViewBase mOpenCvCameraView;

    //native-lib.cpp 에서 만든 필터에 관련된 메소드, 거기서 만든 메소드를 여기에 선언해서 사용하는 느낌
    public native void ConvertRGBtoGray(long matAddrInput, long matAddrResult); //회색 필터
    public native void ConvertRGBtoHSV(long matAddrInput, long matAddrResult); //어두운 형광 파란색을 기본으로 살짝 갈라지는 현상을 보여주는 필터
    public native void ConvertRGBtoLuv(long matAddrInput, long matAddrResult); //살짝 파스텔톤의 파란색과 분홍색의 필터

    //얼굴인식 예제에서 사용 및 추가된것들
    public native long loadCascade(String cascadeFileName);
    public native void detectFaceAndEye(long cascadeClassifier_face, long cascadeClassifier_eye, long matAddrInput, long matAddrResult);
    public native void wearSunglasses(long cascadeClassifier_face, long cascadeClassifier_eye, long matAddrInput, long matAddrResult, long matAddrglasses);
    public long cascadeClassifier_face = 0;
    public long cascadeClassifier_eye = 0;

//    //얼굴인식 예제에서 추가된 것들. 세마포어
//    private final Semaphore writeLock = new Semaphore(1);
//    public void getWriteLock() throws InterruptedException {
//        writeLock.acquire();
//    }
//    public void releaseWriteLock() {
//        writeLock.release();
//    }


    static {
        System.loadLibrary("opencv_java4");
        System.loadLibrary("native-lib");
    }


    //얼굴인식 예제에서 가저온 메소드
    //TODO 파일 내부저장소, 외부저장소에 관한 개념 필요
    private void copyFile(String filename) {

        Log.d(TAG, "!!@@ copyFile() : 실행 위치 확인");

        String baseDir = Environment.getExternalStorageDirectory().getPath();
        String pathDir = baseDir + File.separator + filename;

        AssetManager assetManager = this.getAssets();

        InputStream inputStream = null;
        OutputStream outputStream = null;

        try {
            Log.d(TAG, "copyFile :: 다음 경로로 파일복사 "+ pathDir);
            inputStream = assetManager.open(filename);
            outputStream = new FileOutputStream(pathDir);

            byte[] buffer = new byte[1024];
            int read;
            while ((read = inputStream.read(buffer)) != -1) {
                outputStream.write(buffer, 0, read);
            }
            inputStream.close();
            inputStream = null;
            outputStream.flush();
            outputStream.close();
            outputStream = null;
        } catch (Exception e) {
            Log.d(TAG, "copyFile :: 파일 복사 중 예외 발생 "+e.toString() );
        }
    }

    //얼굴인식 예제에서 가저온 메소드
    //메인액티비티에서는 카메라 퍼미션 받아오는 부분에서 사용되고있음
    private void read_cascade_file(){
        Log.d(TAG,"!!@@ read_cascade_file() : 실행 위치 확인");

        copyFile("haarcascade_frontalface_alt.xml");
        copyFile("haarcascade_eye_tree_eyeglasses.xml");
        Log.d(TAG, "read_cascade_file:");
        cascadeClassifier_face = loadCascade( "haarcascade_frontalface_alt.xml");
        Log.d(TAG, "read_cascade_file:");
        cascadeClassifier_eye = loadCascade( "haarcascade_eye_tree_eyeglasses.xml");
    }

    //BaseLoaderCallback 비동기 초기화 - OpenCV Manager 를 사용하여 대상 시스템에 외부 적으로 설치된 OpenCV 라이브러리에 액세스
    private BaseLoaderCallback mLoaderCallback = new BaseLoaderCallback(this) {

        @Override
        public void onManagerConnected(int status) {
            switch (status) {
                case LoaderCallbackInterface.SUCCESS:
                {
                    mOpenCvCameraView.enableView();
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

        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON, WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        setContentView(R.layout.activity_main);

        mOpenCvCameraView = findViewById(R.id.activity_surface_view);
        mOpenCvCameraView.setVisibility(SurfaceView.VISIBLE);
        mOpenCvCameraView.setCvCameraViewListener(this);
        mOpenCvCameraView.setCameraIndex(0); // front-camera(1),  back-camera(0)

        Log.d(TAG, "!!@@ onCreate() : 실행 위치 확인");

        btnCreate();
    }

    @Override
    public void onPause() {
        super.onPause();

        Log.d(TAG, "!!@@ onPause() : 실행 위치 확인");

        if (mOpenCvCameraView != null)
            mOpenCvCameraView.disableView();
    }

    @Override
    public void onResume() {
        super.onResume();

        Log.d(TAG, "!!@@ onResume() : 실행 위치 확인");

        if (!OpenCVLoader.initDebug()) {
            Log.d(TAG, "onResume :: Internal OpenCV library not found.");
            OpenCVLoader.initAsync(OpenCVLoader.OPENCV_VERSION_3_2_0, this, mLoaderCallback);
        } else {
            Log.d(TAG, "onResum :: OpenCV library found inside package. Using it!");
            mLoaderCallback.onManagerConnected(LoaderCallbackInterface.SUCCESS);
        }
    }

    @Override
    protected void onStop() {
        super.onStop();

        Log.d(TAG, "!!@@ onStop() : 실행 위치 확인");
    }

    public void onDestroy() {
        super.onDestroy();

        Log.d(TAG, "!!@@ onDestroy() : 실행 위치 확인");

        if (mOpenCvCameraView != null)
            mOpenCvCameraView.disableView();
    }

    @Override
    public void onCameraViewStarted(int width, int height) {
        //액티비티가 만들어지고 카메라가 작동하는 시작지점에서 이 메소드가 실행되는듯 함
        Log.d(TAG, "!!@@ onCameraViewStarted() : 실행 위치 확인");
    }

    @Override
    public void onCameraViewStopped() {
        //액티비티가 종료되고 카메라가 작동을 종료하는 시점에서 이 메소드가 실행되는듯 함
        Log.d(TAG, "!!@@ onCameraViewStopped() : 실행 위치 확인");
    }

    @Override
    public Mat onCameraFrame(CameraBridgeViewBase.CvCameraViewFrame inputFrame) {

        //해당 로그를 통해 이 메소드가 약 0.1초에 한번씩 반복됨을 확인
        //Log.d(TAG, "!!@@ onCameraFrame() : 실행 위치 확인");

            //inputFrame이 현재 나타나고있는 카메라 화면
            matInput = inputFrame.rgba();

            //filterMode의 값에 따라 필터를 바꿔주는 메소드를 바꿔주는 부분
            if(filterMode == singleton.filterGrayMode)
            {
                //Gray버튼 눌렀을때 로그 실행되는것 확인함, 로그가 0.1초마다 반복되기 때문에 주석처리함
                //Log.d(TAG, "!!@@ onCameraFrame() - filterMode - gary");

                if ( matResult == null )

                    //matResult에 matInput의 값들을 넣어줌
                    matResult = new Mat(matInput.rows(), matInput.cols(), matInput.type());

                //matInput을 Gray 필터로 변환
                //getNativeObjAddr() 메소드는 java에서 C++로 mat객체를 넘겨줄때 사용한다고함
                //C++에서 해당 mat객체를 받을때는 Mat &matInput = *(Mat *)matAddrInput; 이런식으로 받는다고 함
                ConvertRGBtoGray(matInput.getNativeObjAddr(), matResult.getNativeObjAddr());

                //회색으로 변환된 matResult를 return해서 화면에 띄워줌
                return matResult;
            }

            else if(filterMode == singleton.filterHSVMode)
            {

                //Log.d(TAG, "!!@@ onCameraFrame() - filterMode - HSV");

                if ( matResult == null )

                    matResult = new Mat(matInput.rows(), matInput.cols(), matInput.type());

                ConvertRGBtoHSV(matInput.getNativeObjAddr(), matResult.getNativeObjAddr());

                return matResult;
            }

            else if(filterMode == singleton.filterLuvMode)
            {

                //Log.d(TAG, "!!@@ onCameraFrame() - filterMode - Luv");

                if ( matResult == null )

                    matResult = new Mat(matInput.rows(), matInput.cols(), matInput.type());

                ConvertRGBtoLuv(matInput.getNativeObjAddr(), matResult.getNativeObjAddr());

                return matResult;
            }

            else if(filterMode ==  singleton.filterBasicMode)
            {

                //Log.d(TAG, "!!@@ onCameraFrame() - filterMode - Basic");

                if ( matResult == null )

                matResult = new Mat(matInput.rows(), matInput.cols(), matInput.type());
                //실제 얼굴 인식하는 부분
                detectFaceAndEye(cascadeClassifier_face, cascadeClassifier_eye, matInput.getNativeObjAddr(), matResult.getNativeObjAddr());
                return matResult;
            }

            else if(filterMode == singleton.filterSunglasses)
            {
                //mat 객체 만들어서 assets에 있는 이미지를 비트맵으로 불러와서 비트맵 이미지를 mat객체로 바꿔주는 부분
                Mat matSunglasses = new Mat();
                Bitmap imgSunglasses = loadBitmap("sunglasses.png");
                Utils.bitmapToMat(imgSunglasses, matSunglasses);

                if ( matResult == null )

                    matResult = new Mat(matInput.rows(), matInput.cols(), matInput.type());

                //선글라스 이미지를 입혀주는 메소드
                wearSunglasses(cascadeClassifier_face, cascadeClassifier_eye, matInput.getNativeObjAddr(), matResult.getNativeObjAddr(), matSunglasses.getNativeObjAddr());
                return matResult;
            }

            //조건문이라 else를 설정하지않으면 오류가 발생해서 영향이 없는 기본값을 else에 넣어줌.
            else
            {
                return matInput;
            }


// 예제 가져왔을때 원본, 프레임에 띄워주는 부분
//        if ( matResult == null )
//
//            matResult = new Mat(matInput.rows(), matInput.cols(), matInput.type());
//
//        ConvertRGBtoGray(matInput.getNativeObjAddr(), matResult.getNativeObjAddr());
//
//        return matResult;

    }


    protected List<? extends CameraBridgeViewBase> getCameraViewList() {

        Log.d(TAG, "!!@@ getCameraViewList() : 실행 위치 확인");

        return Collections.singletonList(mOpenCvCameraView);
    }


    //여기서부턴 퍼미션 관련 메소드
    private static final int CAMERA_PERMISSION_REQUEST_CODE = 200;


    protected void onCameraPermissionGranted() {

        Log.d(TAG, "!!@@ onCameraPermissionGranted() : 실행 위치 확인");

        List<? extends CameraBridgeViewBase> cameraViews = getCameraViewList();
        if (cameraViews == null) {
            return;
        }
        for (CameraBridgeViewBase cameraBridgeViewBase: cameraViews) {
            if (cameraBridgeViewBase != null) {
                cameraBridgeViewBase.setCameraPermissionGranted();

                //얼굴인식 예제에서 추가한것
                read_cascade_file();
            }
        }
    }

    @Override
    protected void onStart() {
        super.onStart();

        //TODO 생각대로 작동을안함, 액티비티가 다시 나타날때마다 필터선택부분을 초기화 해주는부분
        filterButtonVisible = 0;
        Log.d(TAG, "!!@@ onStart() : 실행 위치 확인");
        Log.d(TAG, "!!@@ onStart(), filterButtonVisible : " + filterButtonVisible);

        boolean havePermission = true;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (checkSelfPermission(CAMERA) != PackageManager.PERMISSION_GRANTED || checkSelfPermission(WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{CAMERA,WRITE_EXTERNAL_STORAGE}, CAMERA_PERMISSION_REQUEST_CODE);
                havePermission = false;
            }
        }
        if (havePermission) {
            onCameraPermissionGranted();
        }
    }

    @Override
    @TargetApi(Build.VERSION_CODES.M)
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        if (requestCode == CAMERA_PERMISSION_REQUEST_CODE && grantResults.length > 0
                && grantResults[0] == PackageManager.PERMISSION_GRANTED && grantResults[1] == PackageManager.PERMISSION_GRANTED) {
            onCameraPermissionGranted();
        }else{
            showDialogForPermission("앱을 실행하려면 퍼미션을 허가하셔야합니다.");
        }
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
    }


    @TargetApi(Build.VERSION_CODES.M)
    private void showDialogForPermission(String msg) {

        AlertDialog.Builder builder = new AlertDialog.Builder( MainActivity.this);
        builder.setTitle("알림");
        builder.setMessage(msg);
        builder.setCancelable(false);
        builder.setPositiveButton("예", new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int id){
                requestPermissions(new String[]{CAMERA, WRITE_EXTERNAL_STORAGE}, CAMERA_PERMISSION_REQUEST_CODE);
            }
        });
        builder.setNegativeButton("아니오", new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface arg0, int arg1) {
                finish();
            }
        });
        builder.create().show();
    }


    //메인 액티비티의 버튼에 관련된 메소드
    private void btnCreate() {

        Button btnCapture = findViewById(R.id.btn_capture);
        final Button btnBasic = findViewById(R.id.btn_basic);
        final Button btnFirstFilter = findViewById(R.id.btn_first_filter);
        final Button btnSecondFilter = findViewById(R.id.btn_second_filter);
        final Button btnThirdFilter = findViewById(R.id.btn_third_filter);
        Button btnMoveGallery = findViewById(R.id.btn_move_gallery);
        Button btnFilterChoice = findViewById(R.id.btn_filter_choice);
        Button btnSunglasses = findViewById(R.id.btn_sunglasses);
        //Button btnCameraSensor = findViewById(R.id.btn_camera_sensor);

        //캡쳐버튼 누르면 저장하게하기
        //TODO 이부분도 마찬가지로 파일 내부저장소, 외부저장소에 관한 개념 필요
        btnCapture.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {

                //해당 위치에 폴더 만들기
                File path = new File(Environment.getExternalStorageDirectory() + "/Images/");
                path.mkdirs();

                //이부분이 이미지 저장할때 이미지 파일명 생성하는 부분인데 파일명을 시간에 따라 바뀌게 해놔서 이미지가 한 파일로 갱신되면서 저장하는 오류 수정함
                String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
                String imageFileName = "TEST_" + timeStamp + "_";
                File file = new File(path, imageFileName + ".jpg");

                String filename = file.toString();
                Imgcodecs.imwrite(filename, matResult);

                Intent mediaScanIntent = new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE);
                mediaScanIntent.setData(Uri.fromFile(file));
                sendBroadcast(mediaScanIntent);




//                //다른 예제에서 이미지 저장하려고 가져온 부분인데 안써도 될것같음
//                Bitmap bitmap = BitmapFactory.decodeFile(imageFilePath);
//                ExifInterface exif = null;
//
//                try {
//                    exif = new ExifInterface(imageFilePath);
//                } catch (IOException e) {
//                    e.printStackTrace();
//                }
//
//                int exifOrientation;
//                int exifDegree;
//
//                if(exif != null) {
//                    exifOrientation = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL);
//                    exifDegree = exifOrientationToDegress(exifOrientation);
//                } else {
//                    exifDegree = 0;
//                }

            }
        });

        //필터 기본 버튼을 눌렀을때
        btnBasic.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                filterMode = singleton.filterBasicMode;
            }
        });

        //필터1 버튼을 눌렀을때 - grayscale
        btnFirstFilter.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                filterMode = singleton.filterGrayMode;
            }
        });

        //필터2 버튼을 눌렀을때 - hsv
        btnSecondFilter.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                filterMode = singleton.filterHSVMode;
            }
        });

        //필터3 버튼을 눌렀을때 - luv
        btnThirdFilter.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                filterMode = singleton.filterLuvMode;
            }
        });

        //선글라스 버튼을 눌렀을때
        btnSunglasses.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                filterMode = singleton.filterSunglasses;
            }
        });


        //갤러리 이동 버튼을 눌렀을때 - 갤러리 액티비티로 화면전환
        btnMoveGallery.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                Intent intent = new Intent(MainActivity.this, GalleryActivity.class);
                startActivity(intent);
            }
        });

        //필터선택 버튼을 눌렀을때 나머지 필터버튼들이 보였다가 안보였다가 하게 해줌
        //filterButtonVisible의 변화(홀수, 짝수)를 통해서 버튼들의 상태 제어
        //필터 버튼들은 레이아웃에서 초기설정을 invisible로 해둔 상태
        btnFilterChoice.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {

                //이 변수는 onStart에서 0으로 초기화 시켜주고 있음 but 내 생각대로 작동안됨
                filterButtonVisible ++;

                Log.d(TAG, "!!@@ btnCreate(), filterButtonVisible : " + filterButtonVisible);

                //filterButtonVisible 변수를 2로나눈 나머지가 0일때(즉 변수가 짝수일때) : 4개의 버튼을 안보이게함, 다만 위치는 차지한 상태
                if(filterButtonVisible % 2 == 0)
                {
                    btnBasic.setVisibility(View.INVISIBLE);
                    btnFirstFilter.setVisibility(View.INVISIBLE);
                    btnSecondFilter.setVisibility(View.INVISIBLE);
                    btnThirdFilter.setVisibility(View.INVISIBLE);
                }
                //filterButtonVisible 변수를 2로나눈 나머지가 1일때(즉 변수가 홀수일때) : 4개의 버튼을 보여줌
                else
                {
                    btnBasic.setVisibility(View.VISIBLE);
                    btnFirstFilter.setVisibility(View.VISIBLE);
                    btnSecondFilter.setVisibility(View.VISIBLE);
                    btnThirdFilter.setVisibility(View.VISIBLE);
                }

            }
        });

        //카메라 센서 버튼(전방/후방 버튼)을 눌렀을때
//        btnCameraSensor.setOnClickListener(new View.OnClickListener() {
//            public void onClick(View v) {
//                cameraSensorMode ++;
//                Log.i(TAG, "카메라센서 버튼 눌렀을때 : " + cameraSensorMode);
//            }
//        });

    }

    //assets 폴더에 있는 이미지 파일을 비트맵으로 불러오는 메소드
    public Bitmap loadBitmap(String urlStr) {
        Bitmap bitmap = null;

        AssetManager mngr = getResources().getAssets();

        try{
            InputStream is = mngr.open(urlStr);

            bitmap = BitmapFactory.decodeStream(is);

        }catch(Exception e){
            Log.e(TAG, "loadDrawable exception" + e.toString());
        }

        return bitmap;
    }







    //이 아래의 메소드들은 예제들로 부터 찾아온것, 사용안함
    //이미지 저장을위해서 다른 예제에서 가져온부분, 이미지를 저장할때 이미지의 이름이 겹치지 않기위해 시간에 관한 패턴을 사용함
//    private File createImageFile() throws IOException {
//        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
//        String imageFileName = "TEST_" + timeStamp + "_";
//        File storageDir = getExternalFilesDir(Environment.DIRECTORY_PICTURES);
//        File image = File.createTempFile(imageFileName, ".jpg", storageDir);
//        imageFilePath = image.getAbsolutePath();
//        return image;
//    }
//
//    //사진을 파일로 만드는 메소드 (출처: 안드로이드 디벨로퍼)
//    private void dispatchTakePictureIntent() {
//        Intent takePictureIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
//        // Ensure that there's a camera activity to handle the intent
//        if (takePictureIntent.resolveActivity(getPackageManager()) != null) {
//            // Create the File where the photo should go
//            File photoFile = null;
//            try {
//                photoFile = createImageFile();
//            } catch (IOException ex) {
//                // Error occurred while creating the File
//
//            }
//            // Continue only if the File was successfully created
//            if (photoFile != null) {
//                Uri photoURI = FileProvider.getUriForFile(this, "com.example.android.fileprovider", photoFile);
//                takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT, photoURI);
//                startActivityForResult(takePictureIntent, REQUEST_TAKE_PHOTO);
//            }
//        }
//    }
//
//    //갤러리에 사진 추가하는 메소드(출처: 안드로이드 디벨로퍼)
//    private void galleryAddPic() {
//        Intent mediaScanIntent = new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE);
//        File f = new File(imageFilePath);
//        Uri contentUri = Uri.fromFile(f);
//        mediaScanIntent.setData(contentUri);
//        this.sendBroadcast(mediaScanIntent);
//    }
//
//
//    private int exifOrientationToDegress(int exifOrientation) {
//        if(exifOrientation == ExifInterface.ORIENTATION_ROTATE_90) {
//            return 90;
//        } else if (exifOrientation == ExifInterface.ORIENTATION_ROTATE_180) {
//            return 180;
//        } else if (exifOrientation == ExifInterface.ORIENTATION_ROTATE_270) {
//            return 270;
//        }
//        return 0;
//    }
//
//    private Bitmap rotate(Bitmap bitmap, float degree) {
//        Matrix matrix = new Matrix();
//        matrix.postRotate(degree);
//        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true);
//    }

}