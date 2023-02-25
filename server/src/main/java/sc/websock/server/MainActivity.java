package sc.websock.server;

import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;

import android.media.MediaCodec;
import android.media.MediaFormat;
import android.os.Build;
import android.os.Bundle;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.widget.Button;

import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.ByteBuffer;

import sc.websock.common.Constants;

public class MainActivity extends AppCompatActivity implements Constants {

    private SurfaceView surfaceView;
    private Button button;
    private WebSocketClient client;
    private MediaCodec codec;


    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        surfaceView = findViewById(R.id.surfaceView);
        button = findViewById(R.id.button);
        init();
    }

    private void init() {
        DisplayMetrics dm = getResources().getDisplayMetrics();
        surfaceView.getHolder().setFixedSize(dm.widthPixels, dm.heightPixels);
        surfaceView.getHolder().setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);
        surfaceView.getHolder().addCallback(new SurfaceHolder.Callback() {
            @Override
            public void surfaceCreated(SurfaceHolder holder) {
                initCodec(holder.getSurface());
            }

            @Override
            public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {

            }

            @Override
            public void surfaceDestroyed(SurfaceHolder holder) {

            }
        });

        new Thread() {
            @Override
            public void run() {
                try {
                    URI uri = new URI("ws://" + ADDRESS + ":" + PORT);
                    client = new WebSocketClient(uri) {
                        @Override
                        public void onOpen(ServerHandshake handshakedata) {
                            Log.d(TAG_SERVER, "MainActivity ===> onOpen() ");
                            button.post(new Runnable() {
                                @Override
                                public void run() {
                                    button.setVisibility(View.GONE);
                                }
                            });
                        }

                        @Override
                        public void onMessage(String message) {
                            Log.d(TAG_SERVER, "MainActivity ===> onMessage() 1 ");
                        }

                        @RequiresApi(api = Build.VERSION_CODES.M)
                        @Override
                        public void onMessage(ByteBuffer bytes) {
                            super.onMessage(bytes);
                            byte[] data = new byte[bytes.remaining()];
                            bytes.get(data);
                            Log.d(TAG_SERVER, "MainActivity ===> onMessage() 2 , data = " + data.length);
                            doCallbackData(data);
                        }

                        @Override
                        public void onClose(int code, String reason, boolean remote) {
                            Log.d(TAG_SERVER, "MainActivity ===> onClose() , reason = " + reason);
                            button.post(new Runnable() {
                                @Override
                                public void run() {
                                    button.setVisibility(View.VISIBLE);
                                }
                            });
                        }

                        @Override
                        public void onError(Exception ex) {
                            Log.d(TAG_SERVER, "MainActivity ===> onError() , ex = " + ex.getMessage());
                            button.post(new Runnable() {
                                @Override
                                public void run() {
                                    button.setVisibility(View.VISIBLE);
                                }
                            });
                        }
                    };
                } catch (URISyntaxException e) {
                    Log.e(TAG_SERVER, "MainActivity ===> init socket , e = " + e.toString());
                }
            }
        }.start();
    }

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    private void initCodec(Surface surface) {
        int width, height, dpi;
        DisplayMetrics metrics = new DisplayMetrics();
        getWindowManager().getDefaultDisplay().getMetrics(metrics);
        width = metrics.widthPixels;
        height = metrics.heightPixels;
        dpi = metrics.densityDpi;
        int formatWidth = width;
        int formatHeight = height;
        if ((formatWidth & 1) == 1) {
            formatWidth--;
        }
        if ((formatHeight & 1) == 1) {
            formatHeight--;
        }
        try {
            MediaFormat format = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_HEVC, 720, 1080);
            // 指定比特率
            format.setInteger(MediaFormat.KEY_BIT_RATE, 720 * 1080);
            // 指定帧率
            format.setInteger(MediaFormat.KEY_FRAME_RATE, 20);
            // 指定关键帧时间间隔
            format.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1);
//            //竖屏
//            byte[] header_sps = {0, 0, 0, 1, 103, 66, -128, 31, -38, 2, -48, 40, 104, 6, -48, -95, 53};
//            byte[] header_pps = {0, 0 ,0, 1, 104, -50, 6, -30};
//            /*//横屏
//            byte[] header_sps = {0, 0, 0, 1, 103, 66, -128, 31, -38, 1, 64, 22, -24, 6, -48, -95, 53};
//            byte[] header_pps = {0, 0 ,0, 1, 104, -50, 6, -30};*/
//            format.setByteBuffer("csd-0", ByteBuffer.wrap(header_sps));
//            format.setByteBuffer("csd-1", ByteBuffer.wrap(header_pps));
            codec = MediaCodec.createDecoderByType(MediaFormat.MIMETYPE_VIDEO_HEVC);
            codec.configure(format, surface, null, 0);
            codec.start();
        } catch (IOException e) {
            e.printStackTrace();
            Log.e(TAG_SERVER, "MainActivity ===> initCodec , e = " + e.toString());
        }
    }

    public void onClick(View view) {
        client.connect();
    }

    @RequiresApi(api = Build.VERSION_CODES.M)
    private void doCallbackData(byte[] data) {
        if (data == null) {
            return;
        }
        Log.d(TAG_SERVER, "MainActivity ===> doCallbackData ===> data = " + data.length);
        //dequeue可以存储的有效索引
        int id = codec.dequeueInputBuffer(100_000);
        if (id >= 0) {
            //获取可以存储的input buffer数组
            ByteBuffer buffer = codec.getInputBuffer(id);
            buffer.clear();
            //将传过来的buf放入有效的buffer索引中
            buffer.put(data, 0, data.length);
            //将数据queue到需要渲染的input buffer中
            codec.queueInputBuffer(id, 0, data.length, System.currentTimeMillis(), 0);

            MediaCodec.BufferInfo bufferInfo = new MediaCodec.BufferInfo();
            //dequeue一块已经存好数据[a步queueInputBuffer的数据]的 输出buffer索引
            int outId = codec.dequeueOutputBuffer(bufferInfo, 100_000);
            while (outId >= 0) {
                final int finalOutId = outId;
                //将数据在surface上渲染[surfaceview上显示]
                codec.releaseOutputBuffer(finalOutId, true);
                //查询是否还有数据，因为服务端传输的时候I帧前面，拼接了vps帧
                //不断dequeue，以备渲染
                outId = codec.dequeueOutputBuffer(bufferInfo, 0);
            }
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    @Override
    protected void onDestroy() {
        if (client != null) {
            try {
                client.close();
            } catch (Exception e) {
                Log.e(TAG_SERVER, "client ===> onDestroy() , e1 = " + e.toString());
            }
        }
        if (codec != null) {
            try {
                codec.release();
                codec.flush();
                codec.stop();
            } catch (Exception e) {
                Log.e(TAG_SERVER, "client ===> MainActivity onDestroy() code e2 = " + e.toString());
            }
        }
        super.onDestroy();
    }
}