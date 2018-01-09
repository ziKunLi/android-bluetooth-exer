package com.example.newbies.bluetoothtest.view;

import android.content.Context;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.widget.FrameLayout;

/**
 * 步骤一：自定义可移动组件
 * @author NewBies
 * @date 2017/12/26
 */
public class VertexView extends android.support.v7.widget.AppCompatTextView{

    private int startX;
    private int startY;
    private int endX;
    private int endY;
    private FrameLayout.LayoutParams layoutParams;

    public VertexView(Context context) {
        super(context);
    }

    public VertexView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public VertexView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    public VertexView(Context context,int id){
        super(context);

    }

    /**
     * 步骤二：重写onTouchEvent事件
     * @param event
     * @return
     */
    @Override
    public boolean onTouchEvent(MotionEvent event){
        //步骤三：获取手机触摸点的横坐标和纵坐标
        endX = (int)event.getX();
        endY = (int)event.getY();

        //步骤四：获取布局参数实例，注意：xx.LayoutParams这里的xx应该是该组件的父布局类型
        //注意：这句话必须在该组件已经添加到父布局中才会起作用，所以这句话我没有写在构造函数中，而是写在这里
        layoutParams = (FrameLayout.LayoutParams) this.getLayoutParams();

        switch (event.getAction()){
            //监听按下去的事件，这个事件在每次拖动时，必定会执行，也只执行一次
            case MotionEvent.ACTION_DOWN:
                //将按下去的点记录为起始点
                startX = endX;
                startY = endY;
                break;
            //步骤五：监听移动事件，该事件会在拖动时执行N次
            case MotionEvent.ACTION_MOVE:
                //计算移动的距离
                int offsetX = endX - startX;
                int offsetY = endY - startY;

                //调用layout方法来重新放置它的位置
                layoutParams.setMargins(getLeft() + offsetX, getTop() + offsetY, getRight() + offsetX, getBottom() + offsetY);
                //刷新
                requestLayout();
                break;
            //监听抬起事件，该事件同按下去的时间一样，只执行一次
            case MotionEvent.ACTION_UP:
                break;
            default:break;
        }
        //这里应该返回true，这里涉及到了android的事件拦截机制，大致意思是，我的事件是在哪里处理，就在那里的事件返回TRUE
        return  true;
    }
}
