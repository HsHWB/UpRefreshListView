package example.com.downfreshlinearlayout;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.preference.PreferenceManager;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.animation.RotateAnimation;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;

import example.com.downfreshlistview.R;


/**
 * Created by Hs on 2015/2/16.
 */
public class MyLinearLayout extends LinearLayout implements View.OnTouchListener {

    private static final int STATUS_PULL_TO_REFRESH = 0;//下拉状态
    private static final int STATUS_RELEASE_TO_REFRESH = 1;//释放立即更新状态
    private static final int STATUS_REFRESHING = 2;//正在刷新状态
    private static final int STATUS_REFRESH_FINISHED = 3;//刷新完成或未刷新状态
    private static final int SCROLL_SPEED = -20;//下拉头部回滚速度
    private static final long ONE_MINUTE = 60 * 1000;//一分钟的毫秒值，用于判断上次的更新时间
    private static final long ONE_HOUR = 60 * ONE_MINUTE;//一小时的毫秒值，用于判断上次的更新时间
    private static final long ONE_DAY = 24 * ONE_HOUR;//一天的毫秒值，用于判断上次的更新时间
    private static final long ONE_MONTH = 30 * ONE_DAY;//一月的毫秒值，用于判断上次的更新时间
    private static final long ONE_YEAR = 12 * ONE_MONTH;//一年的毫秒值，用于判断上次的更新时间
    private static final String UPDATED_AT = "updated_at";//上次更新时间的字符串常量，用于作为SharedPreferences的键值
    private PullToRefreshListener mListener;//拉刷新的回调接口
    private SharedPreferences preferences;//用于存储上次更新时间
    private View header;//下拉头的View
    private ListView listView;//需要去下拉刷新的ListView
//    private ProgressBar progressBar;//刷新时显示的进度条
    private ImageView arrow;//指示下拉和释放的箭头
    private TextView description;//指示下拉和释放的文字描述
    private TextView updateAt;//上次更新时间的文字描述
    private MarginLayoutParams headerLayoutParams;//下拉头的布局参数
    private long lastUpdateTime;//上次更新时间的毫秒值
    private int mId = -1;//为了防止不同界面的下拉刷新在上次更新时间上互相有冲突，使用id来做区分
    private int hideHeaderHeight;//下拉头的高度
    private int currentStatus = STATUS_REFRESH_FINISHED;;//当前处理什么状态，可选值有STATUS_PULL_TO_REFRESH, STATUS_RELEASE_TO_REFRESH,STATUS_REFRESHING 和 STATUS_REFRESH_FINISHED
    private int lastStatus = currentStatus;//记录上一次的状态是什么，避免进行重复操作
    private float yDown;//手指按下时的屏幕纵坐标
    private int touchSlop;//在被判定为滚动之前用户手指可以移动的最大值。
    private boolean loadOnce;//是否已加载过一次layout，这里onLayout中的初始化只需加载一次
    private boolean ableToPull;//当前是否可以下拉，只有ListView滚动到头的时候才允许下拉


    public MyLinearLayout(Context context, AttributeSet attr){

        super(context, attr);
        preferences = PreferenceManager.getDefaultSharedPreferences(context);

        /**
         * header
         * 在应用中定义了自定义view，需要获取这个view布局，需要用到此方法
         * 一般的资料中的第二个参数会是一个null。通常情况下没有问题，但是如果我想给这个view设置一个对应的类，然后通过这个类来操作的话就会出问题。
         * 参考http://www.cnblogs.com/HighFun/p/3281674.html
         */
        header = LayoutInflater.from(context).inflate(R.layout.refrensh_message_layout, null, true);
        arrow = (ImageView) header.findViewById(R.id.refresh_message_image);
        description = (TextView) header.findViewById(R.id.refresh_message_tough_state);
        updateAt = (TextView) header.findViewById(R.id.refresh_message_tv_time);
        touchSlop = ViewConfiguration.get(context).getScaledTouchSlop();
        refreshUpdatedAtValue();
        setOrientation(VERTICAL);
        addView(header, 0);
    }

    /**
     *进行初始化操作：将布局的下拉箭头refresh_message_layout向上偏移，实现隐藏。给ListView注册tough事件
     * onLayout调用场景：在view给其孩子设置尺寸和位置时被调用。子view，包括孩子在内，
     * 必须重写onLayout(boolean, int, int, int, int)方法，并且调用各自的layout(int, int, int, int)方法。
     */
    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        super.onLayout(changed, l, t, r, b);
        /**
         * 参数changed表示view有新的尺寸或位置；参数l表示相对于父view的Left位置；参数t表示相对于父view的Top位置；
         * 参数r表示相对于父view的Right位置；参数b表示相对于父view的Bottom位置。
         */
        if (changed && !loadOnce){//loadeOnce:没有加载过布局
            hideHeaderHeight = -header.getHeight();//隐藏的高度
            /**
             * 利用getLayoutParams()方法，获取控件的LayoutParams。
             * LayoutParams相当于一个Layout的信息包，它封装了Layout的位置、高、宽等信息。
             */
            headerLayoutParams = (MarginLayoutParams)header.getLayoutParams();
            /**
             * topMargin:上边距，设置为隐藏的高度(整个refresh_message_layout布局高度)
             * 由于上定义为负数，所以这里为向上偏移
             */
            headerLayoutParams.topMargin = hideHeaderHeight;
            /**
             * 在很多时候ListView列表数据不需要全部刷新，只需刷新有数据变化的那一条，
             * 这时可以用getChildAt(index)获取某个指定position的view，并对该view进行刷新。
             * 注意：在ListView中，使用getChildAt(index)的取值，只能是当前可见区域（列表可滚动）的子项！
             * 即取值范围在 >= ListView.getFirstVisiblePosition() &&  <= ListView.getLastVisiblePosition();
             */
            listView = (ListView) getChildAt(1);//获取MyLinearLayout的第二个布局
            listView.setOnTouchListener(this);
            loadOnce = true;
        }
    }


    @Override
    public boolean onTouch(View v, MotionEvent event) {
        setIsAbleToPull(event);
        if (ableToPull) {
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    yDown = event.getRawY();
                    break;
                case MotionEvent.ACTION_MOVE:
                    float yMove = event.getRawY();
                    int distance = (int) (yMove - yDown);
                    // 如果手指是下滑状态，并且下拉头是完全隐藏的，就屏蔽下拉事件
                    if (distance <= 0 && headerLayoutParams.topMargin <= hideHeaderHeight) {
                        return false;
                    }
                    if (distance < touchSlop) {
                        return false;
                    }
                    if (currentStatus != STATUS_REFRESHING) {
                        if (headerLayoutParams.topMargin > 0) {
                            currentStatus = STATUS_RELEASE_TO_REFRESH;
                        } else {
                            currentStatus = STATUS_PULL_TO_REFRESH;
                        }
                        // 通过偏移下拉头的topMargin值，来实现下拉效果
                        headerLayoutParams.topMargin = (distance / 2) + hideHeaderHeight;
                        header.setLayoutParams(headerLayoutParams);
                    }
                    break;
                case MotionEvent.ACTION_UP:
                default:
                    if (currentStatus == STATUS_RELEASE_TO_REFRESH) {
                        // 松手时如果是释放立即刷新状态，就去调用正在刷新的任务
                        new RefreshingTask().execute();
                    } else if (currentStatus == STATUS_PULL_TO_REFRESH) {
                        // 松手时如果是下拉状态，就去调用隐藏下拉头的任务
                        new HideHeaderTask().execute();
                    }
                    break;
            }
            // 时刻记得更新下拉头中的信息
            if (currentStatus == STATUS_PULL_TO_REFRESH
                    || currentStatus == STATUS_RELEASE_TO_REFRESH) {
                updateHeaderView();
                // 当前正处于下拉或释放状态，要让ListView失去焦点，否则被点击的那一项会一直处于选中状态
                listView.setPressed(false);
                listView.setFocusable(false);
                listView.setFocusableInTouchMode(false);
                lastStatus = currentStatus;
                // 当前正处于下拉或释放状态，通过返回true屏蔽掉ListView的滚动事件
                return true;
            }
        }
        return false;
    }

    /**
     * @param listener
     * 监听器的实现。
     * @param id
     * 为了防止不同界面的下拉刷新在上次更新时间上互相有冲突，不同界面在注册下拉刷新监听器时一定要传入不同的id。
     *
     */
    public void setOnRefreshListener(PullToRefreshListener listener, int id){
        mListener = listener;
        mId = id;
    }

    /**
     * 当所有的刷新逻辑完成后，记录调用一下，否则ListView将一直处于正在刷新状态。
     */
    public void finishRefreshing() {
        currentStatus = STATUS_REFRESH_FINISHED;
        preferences.edit().putLong(UPDATED_AT + mId, System.currentTimeMillis()).commit();
        new HideHeaderTask().execute();//隐藏下拉箭头执行
    }

    /**
     * 根据当前ListView的滚动状态来设定 {@link #ableToPull}
     * 的值，每次都需要在onTouch中第一个执行，这样可以判断出当前应该是滚动ListView，还是应该进行下拉。
     *
     * @param event
     *
     * getRawX()和getRawY()获得的是相对屏幕的位置，getX()和getY()获得的永远是view的触摸位置坐标 （这两个值不会超过view的长度和宽度）
     */
    private void setIsAbleToPull(MotionEvent event){
        View firstChild = listView.getChildAt(0);//获取listview的第0项
        if(firstChild != null){
            int firstVisiblePos = listView.getFirstVisiblePosition();//当前listview显示在界面的第一个子项的下标
            if (firstVisiblePos == 0 && firstChild.getTop() == 0){
                if (!ableToPull){
                    yDown = event.getRawY();
                }
                /**
                 * 如果首个元素的上边缘，距离父布局值为0，就说明ListView滚动到了最顶部，此时应该允许下拉刷新
                 */
                ableToPull = true;
            }else{//缩上去
                if (headerLayoutParams.topMargin != hideHeaderHeight){
                    headerLayoutParams.topMargin = hideHeaderHeight;
                    header.setLayoutParams(headerLayoutParams);
                }
            }
        }else{//ListView为空
            ableToPull = true;
        }

    }
    /**
     * 更新下拉头中的信息。
     */
    private void updateHeaderView(){
        if(lastStatus != currentStatus){
            if (currentStatus == STATUS_PULL_TO_REFRESH){
                description.setText(getResources().getString(R.string.pull_refresh));
                arrow.setVisibility(View.VISIBLE);
                rotateArrow();
            }else if (currentStatus == STATUS_RELEASE_TO_REFRESH) {
                description.setText(getResources().getString(R.string.release_refresh));
                arrow.setVisibility(View.VISIBLE);
                rotateArrow();
            }else if (currentStatus == STATUS_REFRESHING) {
                description.setText(getResources().getString(R.string.refreshing_refresh));
                arrow.clearAnimation();
                arrow.setVisibility(View.GONE);
            }
            refreshUpdatedAtValue();
        }
    }
    /**
     * 根据当前的状态来旋转箭头。
     */
    private void rotateArrow() {
        float pivotX = arrow.getWidth() / 2f;
        float pivotY = arrow.getHeight() / 2f;
        float fromDegrees = 0f;
        float toDegrees = 0f;
        if (currentStatus == STATUS_PULL_TO_REFRESH) {
            fromDegrees = 180f;
            toDegrees = 360f;
        } else if (currentStatus == STATUS_RELEASE_TO_REFRESH) {
            fromDegrees = 0f;
            toDegrees = 180f;
        }
        RotateAnimation animation = new RotateAnimation(fromDegrees, toDegrees, pivotX, pivotY);
        animation.setDuration(100);
        animation.setFillAfter(true);
        arrow.startAnimation(animation);
    }
    /**
     * 刷新下拉头中上次更新时间的文字描述。
     */
    private void refreshUpdatedAtValue() {
        lastUpdateTime = preferences.getLong(UPDATED_AT + mId, -1);
        long currentTime = System.currentTimeMillis();
        long timePassed = currentTime - lastUpdateTime;
        long timeIntoFormat;
        String updateAtValue;
        if (lastUpdateTime == -1) {
            updateAtValue = getResources().getString(R.string.have_not_updated);
        } else if (timePassed < 0) {
            updateAtValue = getResources().getString(R.string.time_error);
        } else if (timePassed < ONE_MINUTE) {
            updateAtValue = getResources().getString(R.string.updated_just_now);
        } else if (timePassed < ONE_HOUR) {
            timeIntoFormat = timePassed / ONE_MINUTE;
            String value = timeIntoFormat + "分钟";
            updateAtValue = String.format(getResources().getString(R.string.updated_at), value);
        } else if (timePassed < ONE_DAY) {
            timeIntoFormat = timePassed / ONE_HOUR;
            String value = timeIntoFormat + "小时";
            updateAtValue = String.format(getResources().getString(R.string.updated_at), value);
        } else if (timePassed < ONE_MONTH) {
            timeIntoFormat = timePassed / ONE_DAY;
            String value = timeIntoFormat + "天";
            updateAtValue = String.format(getResources().getString(R.string.updated_at), value);
        } else if (timePassed < ONE_YEAR) {
            timeIntoFormat = timePassed / ONE_MONTH;
            String value = timeIntoFormat + "个月";
            updateAtValue = String.format(getResources().getString(R.string.updated_at), value);
        } else {
            timeIntoFormat = timePassed / ONE_YEAR;
            String value = timeIntoFormat + "年";
            updateAtValue = String.format(getResources().getString(R.string.updated_at), value);
        }
        updateAt.setText(updateAtValue);
    }
    /**
     * 正在刷新的任务，在此任务中会去回调注册进来的下拉刷新监听器。
     *
     */
        class RefreshingTask extends AsyncTask<Void, Integer, Void> {
            @Override
            protected Void doInBackground(Void... params) {
                int topMargin = headerLayoutParams.topMargin;
                while (true) {
                    topMargin = topMargin + SCROLL_SPEED;
                    if (topMargin <= 0) {
                        topMargin = 0;
                        break;
                    }
                    publishProgress(topMargin);
                    sleep(10);
                }
                currentStatus = STATUS_REFRESHING;
                publishProgress(0);
                if (mListener != null) {
                    mListener.onRefresh();
                }
                return null;
            }
            @Override
            protected void onProgressUpdate(Integer... topMargin) {
                updateHeaderView();
                headerLayoutParams.topMargin = topMargin[0];
                header.setLayoutParams(headerLayoutParams);
            }
        }
        /**
         * 隐藏下拉头的任务，当未进行下拉刷新或下拉刷新完成后，此任务将会使下拉头重新隐藏。
         *
         * @author guolin
         */
        class HideHeaderTask extends AsyncTask<Void, Integer, Integer> {
            @Override
            protected Integer doInBackground(Void... params) {
                int topMargin = headerLayoutParams.topMargin;
                while (true) {
                    topMargin = topMargin + SCROLL_SPEED;
                    if (topMargin <= hideHeaderHeight) {
                        topMargin = hideHeaderHeight;
                        break;
                    }
                    publishProgress(topMargin);
                    sleep(10);
                }
                return topMargin;
            }
            @Override
            protected void onProgressUpdate(Integer... topMargin) {
                headerLayoutParams.topMargin = topMargin[0];
                header.setLayoutParams(headerLayoutParams);
            }
            @Override
            protected void onPostExecute(Integer topMargin) {
                headerLayoutParams.topMargin = topMargin;
                header.setLayoutParams(headerLayoutParams);
                currentStatus = STATUS_REFRESH_FINISHED;
            }
        }
            /**
         * 使当前线程睡眠指定的毫秒数。
         *
         * @param time
         *            指定当前线程睡眠多久，以毫秒为单位
         */
        private void sleep(int time) {
            try {
                Thread.sleep(time);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
        /**
         * 下拉刷新的监听器，使用下拉刷新的地方应该注册此监听器来获取刷新回调。
         *
         * @author guolin
         */
        public interface PullToRefreshListener {
            /**
             * 刷新时会去回调此方法，在方法内编写具体的刷新逻辑。注意此方法是在子线程中调用的， 你可以不必另开线程来进行耗时操作。
             */
            void onRefresh();
        }
}
