#include <jni.h>
#include <opencv2/opencv.hpp>
#include <android/log.h>

using namespace cv;
using namespace std;

//extern "C"란 c로 만들어진 라이브러리를 c++에서 가져다 쓸때 사용
//c로 만든 메소드들을 c++에서 사용할때 extern "c"를 해줘야함, 이 키워드를 적어주지 않으면 에러발생
extern "C"
JNIEXPORT void JNICALL
//JNIEnv *env는 자바와 네이티브 메소드(c언어의 메소드)를 연결하는 인터페이스 포인터
//c언어에서 포인터는 해당위치를 가르쳐주는것 정도로 알고있음.
Java_com_example_cameraexample_MainActivity_ConvertRGBtoGray(JNIEnv *env, jobject thiz, jlong matAddrInput, jlong matAddrResult) {

    //matInput의 주소는(&) = *(Mat *)matAddrInput 여기 이다.
    Mat &matInput = *(Mat *)matAddrInput;
    Mat &matResult = *(Mat *)matAddrResult;

    //Gray버튼 눌렀을때 로그 실행되는것 확인함, 로그가 0.1초마다 반복되기 때문에 주석처리함
    //__android_log_print(ANDROID_LOG_DEBUG, "!!@@ native-lib :: ", "ConvertRGBtoGray 실행 위치 확인");

    //cvtColor함수는 컬러를 변환해주는 함수, 아래 사용된 함수는 회색으로 바꿔줌 (RGBA to GRAY)
    cvtColor(matInput, matResult, COLOR_BGR2GRAY);
}

extern "C"
JNIEXPORT void JNICALL
Java_com_example_cameraexample_MainActivity_ConvertRGBtoHSV(JNIEnv *env, jobject thiz, jlong matAddrInput, jlong matAddrResult) {
    Mat &matInput = *(Mat *)matAddrInput;
    Mat &matResult = *(Mat *)matAddrResult;

    cvtColor(matInput, matResult, COLOR_RGB2HSV);
}

extern "C"
JNIEXPORT void JNICALL
Java_com_example_cameraexample_MainActivity_ConvertRGBtoLuv(JNIEnv *env, jobject thiz, jlong matAddrInput, jlong matAddrResult) {
    Mat &matInput = *(Mat *)matAddrInput;
    Mat &matResult = *(Mat *)matAddrResult;

    cvtColor(matInput, matResult, COLOR_RGB2Luv);
}




//객체의 크기를 조절하기 위해 사용하는 메소드 (이미지의 크기를 조절할때 비율이 필요한데 그 비율을 만드는 메소드)
float resize(Mat img_src, Mat &img_resize, int resize_width) {

    float scale = resize_width / (float)img_src.cols ;

    if (img_src.cols > resize_width)
    {
        int new_height = cvRound(img_src.rows * scale);
        resize(img_src, img_resize, Size(resize_width, new_height));
    }

    else
    {
        img_resize = img_src;
    }

    return scale;
}

extern "C"
JNIEXPORT jlong JNICALL
//assets 폴더에 만든 xml파일을 로드 시켜주는 메소드 - 여기에 얼굴인식과 눈인식을 학습해놓은 상태라고함
Java_com_example_cameraexample_MainActivity_loadCascade(JNIEnv *env, jobject thiz, jstring cascade_file_name) {


    //const char *cascadeFileName = env->GetStringUTFChars(cascadeFileName_, 0);

    //const - 변수에 선언된 값을 변경하지 못하게 하는것, 즉 상수화 하는것 (java의 final과 비슷한것같음)
    const char *nativeFileNameString = env->GetStringUTFChars(cascade_file_name, 0);

    string baseDir("/storage/emulated/0/");
    baseDir.append(nativeFileNameString);
    const char *pathDir = baseDir.c_str();

    jlong ret = 0;
    ret = (jlong) new CascadeClassifier(pathDir);
    if (((CascadeClassifier *) ret)->empty())
    {
        __android_log_print(ANDROID_LOG_DEBUG, "native-lib :: ", "CascadeClassifier로 로딩 실패  %s", nativeFileNameString);
    }

    else
    {
        __android_log_print(ANDROID_LOG_DEBUG, "native-lib :: ", "CascadeClassifier로 로딩 성공 %s", nativeFileNameString);
    }


    env->ReleaseStringUTFChars(cascade_file_name, nativeFileNameString);
    return ret;

    //env->ReleaseStringUTFChars(cascadeFileName_, cascadeFileName);
}

void overlayImage(const Mat &background, const Mat &foreground, Mat &output, Point2i location)
{
    //입력으로 받은 background를 output에 복사해놓기
    background.copyTo(output);


    // start at the row indicated by location, or at row 0 if location.y is negative. --> location에 나타난 행부터 시작하거나, location.y 좌표가 음수일경우 0행부터 시작
    for (int y = std::max(location.y, 0); y < background.rows; ++y)
    {
        int fY = y - location.y; // because of the translation --> 선글라스 이미지의 크기를 변경해야하기 때문에

        // we are done of we have processed all rows of the foreground image. --> 선글라스의 크기가 입력값보다 크면 안됨
        if (fY >= foreground.rows)
        {
            break;
        }

        // start at the column indicated by location, or at column 0 if location.x is negative. --> location에 나타난 열부터 시작하거나, location.x 좌표가 음수일경우 0행부터 시작
        for (int x = std::max(location.x, 0); x < background.cols; ++x)
        {
            int fX = x - location.x; // because of the translation.

            // we are done with this row if the column is outside of the foreground image.
            if (fX >= foreground.cols)
            {
                break;
            }

            // determine the opacity of the foregrond pixel, using its fourth (alpha) channel. --> 선글라스 이미지의 불투명도를 정의함
            double opacity = ((double)foreground.data[fY * foreground.step + fX * foreground.channels() + 3]) / 255.f;

            // and now combine the background and foreground pixel, using the opacity --> 불투명도를 사용해서 입력받은 카메라 프레임 위에 선글라스 이미지 합쳐보자

            // but only if opacity > 0.
            for (int c = 0; opacity > 0 && c < output.channels(); ++c)
            {
                unsigned char foregroundPx = foreground.data[fY * foreground.step + fX * foreground.channels() + c];
                unsigned char backgroundPx = background.data[y * background.step + x * background.channels() + c];
                output.data[y*output.step + output.channels()*x + c] = backgroundPx * (1. - opacity) + foregroundPx * opacity;
            }
        }
    }
}


extern "C"
JNIEXPORT void JNICALL
//얼굴과 눈을 검출해주는 메소드
Java_com_example_cameraexample_MainActivity_detectFaceAndEye(JNIEnv *env, jobject instance, jlong cascade_classifier_face, jlong cascade_classifier_eye,
                                                   jlong mat_addr_input, jlong mat_addr_result) {


    //기존 img_input의 주소에 있는 값에  이 메소드의 매개변수로 들어가는 mat_addr_input값을 넣어줌
    Mat &img_input = *(Mat *) mat_addr_input;
    Mat &img_result = *(Mat *) mat_addr_result;

    // clone() - 행렬 데이터와 같은 값을 복사해서 새로운 행렬을 반환한다.
    img_result = img_input.clone();

    //std는 standard의 약자, C++의 표준 라이브러리를 사용하기위함, 여기선 표준라이브러리의 vector라는 구조를 사용, vector구조는 java의 list와 비슷한느낌 - 유동적으로 크기조절이 가능한 배열
    std::vector<Rect> faces;

    // 그레이스케일의 이미지는 equalizeHist 메소드를 바로 적용가능하기 때문에 사용함
    Mat img_gray;
    cvtColor(img_input, img_gray, COLOR_BGR2GRAY);
    //히스토그램 평활화 - 이미지를 인식할때 (너무 밝거나 어두운) 이미지를 선명하게 해주기위해 사용하는 메소드
    equalizeHist(img_gray, img_gray);

    //그레이스케일로 바꾼 이미지의 비율을 만드는 메소드
    Mat img_resize;
    float resizeRatio = resize(img_gray, img_resize, 640);


    //카메라 프레임에서 얼굴위치 검출하는 부분
    // & : 교집합, | : 합집합
    //detectMultiScale - input이미지(img_resize)에서 크기가 다른 object(faces)를 검출하는 메소드
    //detectMultiScale(원본이미지, 검출하고싶은 object, scaleFactor - ??, minNeighbors - ??, flags - ??, minSize - (30,30)보다 작은사이즈는 검출하지 않을것, maxSize - 검출하려는 최대사이즈(여기서는 생략함))
    ((CascadeClassifier *) cascade_classifier_face)->detectMultiScale( img_resize, faces, 1.1, 2, 0|CASCADE_SCALE_IMAGE, Size(30, 30) );

    __android_log_print(ANDROID_LOG_DEBUG, (char *) "native-lib :: ", (char *) "face %d found ", faces.size());

    for (int i = 0; i < faces.size(); i++)
    {
        double real_facesize_x = faces[i].x / resizeRatio;
        double real_facesize_y = faces[i].y / resizeRatio;
        double real_facesize_width = faces[i].width / resizeRatio;
        double real_facesize_height = faces[i].height / resizeRatio;

        //point 클래스 - 좌표를 나타내기 위한 클래스 (이차원(point2D), 3차원(point3D))
        //center라는 좌표를 생성함
        Point center( real_facesize_x + real_facesize_width / 2, real_facesize_y + real_facesize_height/2);

        //ellipse 메소드는 타원을 그리는 메소드 - 실제 얼굴부분에 원을 그려주는 부분
        //ellipse(타원이 그려질 이미지, 중심좌표, size(x축 반지름, y축 반지름), 회전각, 원의 시작각, 원의 끝각, 타원의 색(B,G,R), 선의 굵기(default = 1), lineType(default = 8), shift (default = 0)
        //회전각은 타원 자체가 회전되어있는 정도
        //원의 시작각과 끝각 - 원의 가장 오른쪽 부분이 시작(이부분이 시작각 = 0)하고 시계방향으로 그린다
        ellipse(img_result, center, Size( real_facesize_width / 2, real_facesize_height / 2), 0, 0, 360, Scalar(255, 0, 255), 30, 8, 0);

        //Rect 클래스 - 사각형을 그려주는 메소드
        //rect(x, y, x의 길이, y의 길이)
        Rect face_area(real_facesize_x, real_facesize_y, real_facesize_width,real_facesize_height);

        //검출된 얼굴 영역을 다시 이미지화 시켜줌
        Mat faceROI = img_gray( face_area );
        std::vector<Rect> eyes;

        //검출한 얼굴에서 눈 검출하는부분
        ((CascadeClassifier *) cascade_classifier_eye)->detectMultiScale( faceROI, eyes, 1.1, 2, 0 |CASCADE_SCALE_IMAGE, Size(15, 15) );

        //size_t는 0과 양의 정수를 나타냄, 주로 카운팅하는 부분에서 사용됨
        for ( size_t j = 0; j < eyes.size(); j++ )
        {
            //눈의 좌표를 Point클래스로 나타냄
            Point eye_center(real_facesize_x + eyes[j].x + eyes[j].width / 2,
                             real_facesize_y + eyes[j].y + eyes[j].height / 2);

            //실수형 변수를 정수형으로 바꿀때 사용하는 메소드, cvRound - 정수형으로 변환할때 반올림을 한다.
            int radius = cvRound((eyes[j].width + eyes[j].height) * 0.25);
            //이부분이 검출된 눈 부분에 실제 원을 그려주는 부분
            circle(img_result, eye_center, radius, Scalar(255, 0, 0), 30, 8, 0);
        }
    }
}





//얼굴 인식예제에 선글라스 씌우는것을 추가해서 만든 메소드
extern "C"
JNIEXPORT void JNICALL
//얼굴과 눈을 검출해주는 메소드
Java_com_example_cameraexample_MainActivity_wearSunglasses(JNIEnv *env, jobject instance, jlong cascade_classifier_face, jlong cascade_classifier_eye,
                                                   jlong mat_addr_input, jlong mat_addr_result, jlong mat_addr_sunglasses) {

    //기존 img_input의 주소에 있는 값에  이 메소드의 매개변수로 들어가는 mat_addr_input값을 넣어줌
    Mat &img_input = *(Mat *) mat_addr_input;
    Mat &img_result = *(Mat *) mat_addr_result;
    Mat &img_sunglasses = *(Mat *) mat_addr_sunglasses;

    // clone() - 행렬 데이터와 같은 값을 복사해서 새로운 행렬을 반환한다.
    img_result = img_input.clone();

    //std는 standard의 약자, C++의 표준 라이브러리를 사용하기위함, 여기선 표준라이브러리의 vector라는 구조를 사용, vector구조는 java의 list와 비슷한느낌 - 유동적으로 크기조절이 가능한 배열
    std::vector<Rect> faces;

    // 그레이스케일의 이미지는 equalizeHist 메소드를 바로 적용가능하기 때문에 사용함
    Mat img_gray;
    cvtColor(img_input, img_gray, COLOR_BGR2GRAY);
    //히스토그램 평활화 - 이미지를 인식할때 (너무 밝거나 어두운) 이미지를 선명하게 해주기위해 사용하는 메소드
    equalizeHist(img_gray, img_gray);

    //그레이스케일로 바꾼 이미지의 비율을 만드는 메소드
    Mat img_resize;
    float resizeRatio = resize(img_gray, img_resize, 640);


    //카메라 프레임에서 얼굴위치 검출하는 부분
    // & : 교집합, | : 합집합
    //detectMultiScale - 원본이미지(img_resize)에서 크기가 다른 object(faces)를 검출하는 메소드
    //detectMultiScale(원본이미지, 검출하고싶은 object, scaleFactor - ??, minNeighbors - ??, flags - ??, minSize - (30,30)보다 작은사이즈는 검출하지 않을것, maxSize - 검출하려는 최대사이즈(여기서는 생략함))
    ((CascadeClassifier *) cascade_classifier_face)->detectMultiScale( img_resize, faces, 1.1, 2, 0|CASCADE_SCALE_IMAGE, Size(30, 30) );

    __android_log_print(ANDROID_LOG_DEBUG, (char *) "native-lib :: ", (char *) "face %d found ", faces.size());

    Mat tmp_result;

    for (int i = 0; i < faces.size(); i++)
    {
        double real_facesize_x = faces[i].x / resizeRatio;
        double real_facesize_y = faces[i].y / resizeRatio;
        double real_facesize_width = faces[i].width / resizeRatio;
        double real_facesize_height = faces[i].height / resizeRatio;

        //point 클래스 - 좌표를 나타내기 위한 클래스 (이차원(point2D), 3차원(point3D))
        //center라는 좌표를 생성함
        Point center( real_facesize_x + real_facesize_width / 2, real_facesize_y + real_facesize_height/2);

        //ellipse 메소드는 타원을 그리는 메소드 - 실제 얼굴부분에 원을 그려주는 부분
        //ellipse(타원이 그려질 이미지, 중심좌표, size(x축 반지름, y축 반지름), 회전각, 원의 시작각, 원의 끝각, 타원의 색(B,G,R), 선의 굵기(default = 1), lineType(default = 8), shift (default = 0)
        //회전각은 타원 자체가 회전되어있는 정도
        //원의 시작각과 끝각 - 원의 가장 오른쪽 부분이 시작(이부분이 시작각 = 0)하고 시계방향으로 그린다
        //ellipse(img_result, center, Size( real_facesize_width / 2, real_facesize_height / 2), 0, 0, 360, Scalar(255, 0, 255), 30, 8, 0);

        //Rect 클래스 - 사각형을 그려주는 메소드
        //rect(x, y, x의 길이, y의 길이)
        Rect face_area(real_facesize_x, real_facesize_y, real_facesize_width,real_facesize_height);

        //검출된 얼굴 영역을 다시 이미지화 시켜줌
        Mat faceROI = img_gray( face_area );
        std::vector<Rect> eyes;

        //검출한 얼굴에서 눈 검출하는부분
        ((CascadeClassifier *) cascade_classifier_eye)->detectMultiScale( faceROI, eyes, 1.1, 2, 0 |CASCADE_SCALE_IMAGE, Size(10, 10) );

        //검출한 눈의 좌표를 저장할 vector 생성
        vector<Point> points;

        //size_t는 0과 양의 정수를 나타냄, 주로 카운팅하는 부분에서 사용됨
        for ( size_t j = 0; j < eyes.size(); j++ )
        {
            //눈의 좌표를 Point클래스로 나타냄
            Point eye_center(real_facesize_x + eyes[j].x + eyes[j].width / 2,
                             real_facesize_y + eyes[j].y + eyes[j].height / 2);

            //실수형 변수를 정수형으로 바꿀때 사용하는 메소드, cvRound - 정수형으로 변환할때 반올림을 한다.
            int radius = cvRound((eyes[j].width + eyes[j].height) * 0.25);
            //이부분이 검출된 눈 부분에 실제 원을 그려주는 부분
            //circle(img_result, eye_center, radius, Scalar(255, 0, 0), 30, 8, 0);

            //검출한 눈 영역의 중점을 Point클래스 p에 저장
            Point p(eye_center.x, eye_center.y);
            //p를 새로만든 points vector(리스트)에 저장
            points.push_back(p);
        }

        //눈 위치가 2개로 검출됐을 경우 x좌표 기준으로 정렬 시켜줌
        if ( points.size() == 2)
        {
            Point center1 = points[0];
            Point center2 = points[1];

            if (center1.x > center2.x)
            {
                Point temp;
                temp = center1;
                center1 = center2;
                center2 = temp;
            }


            //눈 위치가 아닌경우 필터링하기위함, abs는 절대값을 나타내주는 메소드
            int width = abs(center2.x - center1.x);
            int height = abs(center2.y - center1.y);

            if (width > height)
            {

                //눈 사이의 간격과 안경알 사이간격 비율을 계산
                float imgScale = width / 330.0;

                //안경의 비율을 조정하는 부분
                int w, h;
                w = img_sunglasses.cols * imgScale;
                h = img_sunglasses.rows * imgScale;

                int offsetX = 150 * imgScale;
                int offsetY = 160 * imgScale;

                //안경 이미지를 조절한 비율에 맞게 다시 그려주는 부분
                Mat resized_glasses;
                resize(img_sunglasses, resized_glasses, cv::Size(w, h), 0, 0);

                //TODO 변수타입을 공부하던가, 메소드를 만들어서 하나씩 변수를 확인해야될것같음.
                //C++에서 메소드를 어떻게 정의하고 사용하는지
                //에러 자체에 대한 검색 필요
                overlayImage(img_input, resized_glasses, tmp_result, Point(center1.x - offsetX, center1.y - offsetY));

                img_result = tmp_result;
            }
        }
    }
}


//선글라스 검출예제  원본
//extern "C"
//JNIEXPORT void JNICALL
//void detectAndDraw(Mat& img, CascadeClassifier& cascade,CascadeClassifier& nestedCascade,double scale, bool tryflip, Mat glasses )
//{
//
//    // 결과값을 받을 동영상객체 생성
//    Mat output2;
//    // 입력으로 받은 img이미지를 output2에 복사함,  copyTo() - 원본이 바뀌어도 영향을 미치지 않음. 즉 초기값을 빽업해놓는 상황 이라고 보면 될것같음
//    img.copyTo(output2);
//
//    //double t는 얼굴 검출할때 걸리는 시간을 측정하기위해 사용하는 변수
//    double t = 0;
//
//    //얼굴을 검출하는 사각형 영역에 대해 vector생성
//    vector<Rect> faces;
//
//    //단순히 영역 표시하는 원이나 사각형을 그려줄때 사용할 색
//    const static Scalar colors[] =
//            {
//                    Scalar(255,0,0),
//                    Scalar(255,128,0),
//                    Scalar(255,255,0),
//                    Scalar(0,255,0),
//                    Scalar(0,128,255),
//                    Scalar(0,255,255),
//                    Scalar(0,0,255),
//                    Scalar(255,0,255)
//            };
//    //gray는 회색 필터의 결과값을 담으려는것이고, smallImg는 얼굴부분을 사각형 형태의 이미지로 저장할 객체
//    Mat gray, smallImg;
//
//    //가장 처음에 입력받은 Mat img를 회색필터로 변환후 gray에 저장
//    cvtColor( img, gray, COLOR_BGR2GRAY );
//
//    //입력받은 scale값으로 새로운 비율을 나타낼 변수 fx 생성 ---> 이거 왜해주는지 모르겠음
//    double fx = 1 / scale;
//
//    //resize - 이미지의 크기 재조정 (위에서 만든 float resize메소드랑은 다른 메소드)
//    //resize(원본이미지, 결과저장할것, 원하는 사이즈, fx - 원본 이미지 너비와의 비율, fx - 원본 이미지 높이와의 비율, 보간법)
//    //TODO 보간법은 따로 공부 필요
//    resize( gray, smallImg, Size(), fx, fx, INTER_LINEAR_EXACT );
//
//    //히스토그램 평활화 - 이미지를 인식할때 (너무 밝거나 어두운) 이미지를 선명하게 해주기위해 사용하는 메소드
//    equalizeHist( smallImg, smallImg );
//
//    //getTickCont() : 시간 측정할때 사용하는 함수, 측정 시작 시각
//    //getTickCount() - t  <-- 이부분과 함께 사용됨
//    t = (double)getTickCount();
//
//    //얼굴 위치를 검출하는부분
//    cascade.detectMultiScale( smallImg, faces, 1.1, 2, 0
//                                      //|CASCADE_FIND_BIGGEST_OBJECT
//                                      //|CASCADE_DO_ROUGH_SEARCH
//                                      |CASCADE_SCALE_IMAGE, Size(30, 30) );
//
//    //측정 완료 시각
//    t = (double)getTickCount() - t;
//
//    //fps - 초당 몇번의 화면을 보여주는지 검출하는것
//    printf( "detection time = %g ms\n", t*1000/getTickFrequency());
//
//    //결과 이미지를 저장할 mat객체 생성
//    Mat result;
//
//    for ( size_t i = 0; i < faces.size(); i++ )
//    {
//        Rect r = faces[i];
//
//        //눈 영역을 검출할때 그부분을 사각형으로 받을 객체
//        Mat smallImgROI;
//
//        //눈을 검출할 영역을 관리할 vector
//        vector<Rect> nestedObjects;
//
//        //눈 영역의 중점 좌표를 저장할 객체
//        Point center;
//        Scalar color = colors[i%8];
//        int radius;
//
//
//        double aspect_ratio = (double)r.width/r.height;
//
//        //얼굴위치에 원을 그려주는 부분
//        if( 0.75 < aspect_ratio && aspect_ratio < 1.3 )
//        {
//            center.x = cvRound((r.x + r.width*0.5)*scale);
//            center.y = cvRound((r.y + r.height*0.5)*scale);
//            radius = cvRound((r.width + r.height)*0.25*scale);
//            circle( img, center, radius, color, 3, 8, 0 );
//        }
//        else
//            rectangle( img, Point(cvRound(r.x*scale), cvRound(r.y*scale)), Point(cvRound((r.x + r.width-1)*scale), cvRound((r.y + r.height-1)*scale)), color, 3, 8, 0);
//        if( nestedCascade.empty() ){
//            cout<<"nestedCascade.empty()"<<endl;
//            continue;
//        }
//
//        //얼굴을 검출한 부분에서 눈을 검출하는 부분
//        smallImgROI = smallImg( r );
//        nestedCascade.detectMultiScale( smallImgROI, nestedObjects, 1.1, 2, 0
//                                                //|CASCADE_FIND_BIGGEST_OBJECT
//                                                //|CASCADE_DO_ROUGH_SEARCH
//                                                //|CASCADE_DO_CANNY_PRUNING
//                                                |CASCADE_SCALE_IMAGE, Size(20, 20) );
//
//
//        cout << nestedObjects.size() << endl;
//
//        //검출한 눈의 좌표를 저장할 vector 생성
//        vector<Point> points;
//
//        //눈 위치에 원을 그려주는 부분
//        for ( size_t j = 0; j < nestedObjects.size(); j++ )
//        {
//            //눈의 영역을 사각형 클래스로 만들어줌
//            Rect nr = nestedObjects[j];
//
//            //실수형 변수를 정수형으로 바꿀때 사용하는 메소드, cvRound() - 정수형으로 변환할때 반올림을 한다.
//            //눈의 좌표를 Point클래스로 나타내고 정수형태로 저장, 그후 바로 아래 원을 그려주는 메소드의 매개변수로 사용
//            center.x = cvRound((r.x + nr.x + nr.width*0.5)*scale);
//            center.y = cvRound((r.y + nr.y + nr.height*0.5)*scale);
//
//            //radius는 반지름을 뜻함
//            //눈 영역의 반지름을 정수형태로 저장, 그후 바로 아래 원을 그려주는 메소드의 매개변수로 사용
//            radius = cvRound((nr.width + nr.height)*0.25*scale);
//
//            //눈영역에 원을 그려주는 부분
//            circle( img, center, radius, color, 3, 8, 0 );
//
//            //검출한 눈 영역의 중점을 Point클래스 p에 저장
//            Point p(center.x, center.y);
//            //p를 새로만든 points vector(리스트)에 저장
//            points.push_back(p);
//        }
//
//        //눈 위치가 2개로 검출됐을 경우 x좌표 기준으로 정렬 시켜줌
//        if ( points.size() == 2){
//
//            Point center1 = points[0];
//            Point center2 = points[1];
//
//            if ( center1.x > center2.x ){
//                Point temp;
//                temp = center1;
//                center1 = center2;
//                center2 = temp;
//            }
//
//
//            //눈 위치가 아닌경우 필터링하기위함, abs는 절대값을 나타내주는 메소드
//            int width = abs(center2.x - center1.x);
//            int height = abs(center2.y - center1.y);
//
//            if ( width > height){
//
//                //눈 사이의 간격과 안경알 사이간격 비율을 계산
//                float imgScale = width/330.0;
//
//                //안경의 비율을 조정하는 부분
//                int w, h;
//                w = glasses.cols * imgScale;
//                h = glasses.rows * imgScale;
//
//                int offsetX = 150 * imgScale;
//                int offsetY = 160 * imgScale;
//
//                //안경 이미지를 조절한 비율에 맞게 다시 그려주는 부분
//                Mat resized_glasses;
//                resize( glasses, resized_glasses, cv::Size( w, h), 0, 0 );
//
//                //TODO 변수타입을 공부하던가, 메소드를 만들어서 하나씩 변수를 확인해야될것같음.
//                //C++에서 메소드를 어떻게 정의하고 사용하는지
//                //에러 자체에 대한 검색 필요
//                overlayImage(output2, resized_glasses, result, Point(center1.x-offsetX, center1.y-offsetY));
//                output2 = result;
//            }
//        }
//    }
//
//    if ( result.empty() )
//        imshow( "result", img );
//    else
//        imshow( "result", result );
//
//}