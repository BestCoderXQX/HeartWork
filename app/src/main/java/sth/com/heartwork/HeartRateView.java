package sth.com.heartwork;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.os.Handler;
import android.util.AttributeSet;
import android.view.View;

import java.util.ArrayList;
import java.util.List;

/**
 * 自定义控件 ， 心率波动图，当有最新的心率值的时候  波动一次
 */
public class HeartRateView extends View {

    //坐标轴原点的位置
    private int xPoint=5;
    private int yPoint=160;
    //刻度长度
    private int xScale=20;  //3个单位构成一个刻度
    private int yScale=20;
    //x与y坐标轴的长度
    private int xLength=900;
    private int yLength=160;

    private int MaxDataSize=xLength/xScale;   //横坐标  最多可绘制的点

    private List<Double> data=new ArrayList<Double>();   //存放 纵坐标 所描绘的点


    private Handler mh=new Handler(){
        public void handleMessage(android.os.Message msg) {
            if(msg.what==0){                //判断接受消息类型
                HeartRateView.this.invalidate();  //刷新View
            }
        };
    };

    // 当前心率值
    private int currentHearRate = 0;

    public HeartRateView(Context context, AttributeSet attrs) {
        super(context, attrs);

        //  开启线程 500毫秒判断一次是否有最新的且与当前心率值不同的值，有的话  显示一次波动图
        new Thread(new Runnable() {
            @Override
            public void run() {
                while(true){
                    //在线程中不断往集合中增加数据
                    try {
                        // 休眠500毫秒
                        Thread.sleep(500);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                    // 有最新的心率 更新
                    if (currentHearRate != MainActivity.heartRate) {
                        if (data.size() > MaxDataSize) {  //判断集合的长度是否大于最大绘制长度
                            data.remove(0);  //删除头数据
                        }
                        currentHearRate = MainActivity.heartRate ;

                        data.add(1.00);  //生成1-6的随机数  1.00  标记为 有新的且不同的值
                        mh.sendEmptyMessage(0);   //发送空消息通知刷新
                    }else {
                        if (data.size() > MaxDataSize) {  //判断集合的长度是否大于最大绘制长度
                            data.remove(0);  //删除头数据
                        }
                        data.add(2.00);  //生成1-6的随机数  2.00  标记为 有新的且相同的值
                        mh.sendEmptyMessage(0);   //发送空消息通知刷新
                    }
                }
            }
        }).start();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        // 图标绘制类
        Paint paint=new Paint();
        paint.setStyle(Paint.Style.STROKE);
        paint.setAntiAlias(true);
        paint.setColor(Color.BLACK);
        paint.setStrokeWidth(6);



        if(data.size()>1){
            for (int i = 1; i < data.size()-3; i++) {  //依次取出数据进行绘制
                if (data.get(i) == 1.00) {
                    // 1.00 表示有新的且不同数值的心率值 ，绘制波动图
                    canvas.drawLine(xPoint + (i - 1) * xScale, 100, xPoint + i * xScale, 20, paint);
                    canvas.drawLine(xPoint + i * xScale, 20, xPoint + (1 + i) * xScale, 140, paint);
                    canvas.drawLine(xPoint + (1 + i) * xScale, 140, xPoint + (i + 2) * xScale, 100, paint);
                }else if (data.get(i) == 2.00){
                    // 2.00 表示有新的且数据相同的心率值， 绘制直线，不波动
                    if (i>2) {
                        if (data.get(i - 2) != 1.00 && data.get(i - 1) != 1.00) {
                            canvas.drawLine(xPoint + (i - 1) * xScale, 100, xPoint + i * xScale, 100, paint);
                        }
                    }
                }
//                canvas.drawLine(5 , 100 ,1200 ,100 , paint1);

            }
        }

    }
}

