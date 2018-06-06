package sth.com.heartwork;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.hardware.Camera;
import android.os.Bundle;
import android.os.PowerManager;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicBoolean;

public class MainActivity extends Activity implements View.OnClickListener {



    private TextView currentHeartRate ; // 当前心率
    private Button btnStart ; //按钮 开始
    private Button btnStop ; //按钮 停止


    private  final AtomicBoolean processing = new AtomicBoolean(false);
    //Android手机预览控件
    private  SurfaceView preview = null;
    //预览设置信息
    private  SurfaceHolder previewHolder = null;
    //Android手机相机句柄
    private  Camera camera = null;
    private  PowerManager.WakeLock wakeLock = null;
    private  int averageIndex = 0;
    private  final int averageArraySize = 4;
    private  final int[] averageArray = new int[averageArraySize];


    /**
     * 类型枚举
     */
    public static enum TYPE {
        GREEN, RED
    };

    //设置默认类型
    private static TYPE currentType = TYPE.GREEN;
    //获取当前类型
    public static TYPE getCurrent() {
        return currentType;
    }
    //心跳下标值
    private static int beatsIndex = 0;
    //心跳数组的大小
    private static final int beatsArraySize = 3;
    //心跳数组
    private static final int[] beatsArray = new int[beatsArraySize];
    //心跳脉冲
    private static double beats = 0;
    //开始时间
    private static long startTime = 0;

    public static int heartRate = 0;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // 定义控件
        initView();
        // 定义控件的点击事件
        initEvent();
        initConfig();

    }

    private void initConfig() {


    }

    private void initEvent() {
        btnStart.setOnClickListener(this);
        btnStop.setOnClickListener(this);
    }

    private void initView() {
        currentHeartRate = (TextView) findViewById(R.id.currentHeartRate);
        btnStart = (Button) findViewById(R.id.btnStart);
        btnStop = (Button) findViewById(R.id.btnStop);

        //获取SurfaceView控件
        preview = (SurfaceView) findViewById(R.id.id_preview);
        previewHolder = preview.getHolder();
        previewHolder.addCallback(surfaceCallback);
        previewHolder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);

        PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
        wakeLock = pm.newWakeLock(PowerManager.FULL_WAKE_LOCK, "DoNotDimScreen");

    }


    @Override
    public void onClick(View v) {
        switch (v.getId()){
            case R.id.btnStart:
                // 开始按钮 处理时间
                // 开始隐藏，结束按钮显示
                btnStart.setVisibility(View.GONE);
                btnStop.setVisibility(View.VISIBLE);
                startTime = System.currentTimeMillis();
                // 开始处理心率值
                camera.startPreview();
                break;
            case R.id.btnStop:
                // 结束按钮 处理逻辑
                // 开始按钮显示 结束按钮隐藏
                btnStart.setVisibility(View.VISIBLE);
                btnStop.setVisibility(View.GONE);
                // 不再处理心率值
                camera.stopPreview();
                break;

        }
    }

    @Override
    public void onResume() {
        super.onResume();
        // 开启闪光灯和摄像头
        wakeLock.acquire();
        camera = Camera.open();
    }

    @Override
    public void onPause() {
        super.onPause();
        wakeLock.release();
        camera.setPreviewCallback(null);
//        camera.stopPreview();
        camera.release();
        camera = null;
    }


    //	曲线
    @Override
    public void onDestroy() {
        //当结束程序时关掉Timer
        if (camera!=null) {
            camera.release();
            camera = null;
        }
        super.onDestroy();
    };

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
    }



    /**
     * 相机预览方法  无需理解
     * 这个方法中实现动态更新界面UI的功能，
     * 通过获取手机摄像头的参数来实时动态计算平均像素值、脉冲数，从而实时动态计算心率值。
     */
    private  Camera.PreviewCallback previewCallback = new Camera.PreviewCallback() {
        public void onPreviewFrame(byte[] data, Camera cam) {
            if (data == null) {
                throw new NullPointerException();
            }
            Camera.Size size = cam.getParameters().getPreviewSize();
            if (size == null) {
                throw new NullPointerException();
            }
            if (!processing.compareAndSet(false, true)) {
                return;
            }
            int width = size.width;
            int height = size.height;

            //图像处理
            int imgAvg = ImageProcessing.decodeYUV420SPtoRedAvg(data.clone(),height,width);

            if (imgAvg == 0 || imgAvg == 255) {
                processing.set(false);
                return;
            }
            //计算平均值
            int averageArrayAvg = 0;
            int averageArrayCnt = 0;
            for (int i = 0; i < averageArray.length; i++) {
                if (averageArray[i] > 0) {
                    averageArrayAvg += averageArray[i];
                    averageArrayCnt++;
                }
            }

            //计算平均值
            int rollingAverage = (averageArrayCnt > 0)?(averageArrayAvg/averageArrayCnt):0;
            TYPE newType = currentType;
            if (imgAvg < rollingAverage) {
                newType = TYPE.RED;
                if (newType != currentType) {
                    beats++;
                }
            } else if (imgAvg > rollingAverage) {
                newType = TYPE.GREEN;
            }

            if(averageIndex == averageArraySize) {
                averageIndex = 0;
            }
            averageArray[averageIndex] = imgAvg;
            averageIndex++;

            if (newType != currentType) {
                currentType = newType;
            }

            //获取系统结束时间（ms）
            long endTime = System.currentTimeMillis();
            double totalTimeInSecs = (endTime - startTime) / 1000d;
            if (totalTimeInSecs >= 2) {
                double bps = (beats / totalTimeInSecs);
                int dpm = (int) (bps * 60d);
                if (dpm < 30 || dpm > 180|| imgAvg < 200) {
                    //获取系统开始时间（ms）
                    startTime = System.currentTimeMillis();
                    //beats心跳总数
                    beats = 0;
                    processing.set(false);
                    return;
                }

                if(beatsIndex == beatsArraySize) {
                    beatsIndex = 0;
                }
                beatsArray[beatsIndex] = dpm;
                beatsIndex++;

                int beatsArrayAvg = 0;
                int beatsArrayCnt = 0;
                for (int i = 0; i < beatsArray.length; i++) {
                    if (beatsArray[i] > 0) {
                        beatsArrayAvg += beatsArray[i];
                        beatsArrayCnt++;
                    }
                }
                int beatsAvg = (beatsArrayAvg / beatsArrayCnt);

                // 这里是获取到的心率值 ：currentHeartRate
                currentHeartRate.setText("当前心率"+String.valueOf(beatsAvg)+"次/分" );
                heartRate = beatsAvg ;


                //获取系统时间（ms）
                startTime = System.currentTimeMillis();
                beats = 0;
            }
            processing.set(false);
        }
    };



    /**
     * 预览回调接口  无需理解  固定代码
     */
    private  SurfaceHolder.Callback surfaceCallback = new SurfaceHolder.Callback() {
        //创建时调用
        @Override
        public void surfaceCreated(SurfaceHolder holder) {
            try {
                camera.setPreviewDisplay(previewHolder);
                camera.setPreviewCallback(previewCallback);
            } catch (Throwable t) {

            }
        }

        //当预览改变的时候回调此方法
        @Override
        public void surfaceChanged(SurfaceHolder holder, int format, int width,int height) {
            Camera.Parameters parameters = camera.getParameters();
            parameters.setFlashMode(Camera.Parameters.FLASH_MODE_TORCH);
            Camera.Size size = getSmallestPreviewSize(width, height, parameters);
            if (size != null) {
                parameters.setPreviewSize(size.width, size.height);
            }
            camera.setParameters(parameters);
//            camera.startPreview();
        }

        //销毁的时候调用
        @Override
        public void surfaceDestroyed(SurfaceHolder holder) {

        }
    };

    /**
     * 获取相机最小的预览尺寸  无需理解， 固定代码
     */
    private  Camera.Size getSmallestPreviewSize(int width, int height, Camera.Parameters parameters) {
        Camera.Size result = null;
        for (Camera.Size size : parameters.getSupportedPreviewSizes()) {
            if (size.width <= width && size.height <= height) {
                if (result == null) {
                    result = size;
                }
                else {
                    int resultArea = result.width * result.height;
                    int newArea = size.width * size.height;
                    if (newArea < resultArea) {
                        result = size;
                    }
                }
            }
        }
        return result;
    }
}














