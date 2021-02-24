package com.example.cameraexample;

public class Singleton {

    //이 변수들은 카메라 메인액티비티에서 필터의 종류를 변경할때 그 변수들의 값을 고정시키고 싶어서 만들었음
    int filterBasicMode = 0;
    int filterGrayMode = 1;
    int filterHSVMode = 2;
    int filterLuvMode = 3;
    int filterSunglasses = 4;


    //싱글톤 클래스의 생성자, private으로 접근제한을 걸어서 생성을 제한함
    private Singleton() {}

    //싱글톤 홀더 - JVM의 class loader의 매커니즘과 class의 load 시점을 이용하여 내부 class를 생성시킴으로 thread 간의 동기화 문제를 해결 한다고함
    //TODO : 클래스 로더에 대해 공부해야하나 ?
    private static class SingletonHolder {
        public static final Singleton INSTANCE = new Singleton();
    }

    //실제 코드에서 싱글톤 클래스의 객체를 만들때 사용하는 메소드
    public static Singleton getInstance() {
        return SingletonHolder.INSTANCE;
    }
}
