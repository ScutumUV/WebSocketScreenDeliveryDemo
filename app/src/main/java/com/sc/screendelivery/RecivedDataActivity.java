package com.sc.screendelivery;

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

import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;

import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.ByteBuffer;

import sc.websock.common.Constants;

public class RecivedDataActivity extends AppCompatActivity implements Constants {

    private SurfaceView surfaceView;
    private Button button;
    private WebSocketClient client;
    private MediaCodec codec;


    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_received);
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
                            Log.d(TAG, "client ===> onOpen() ");
                            button.post(new Runnable() {
                                @Override
                                public void run() {
                                    button.setVisibility(View.GONE);
                                }
                            });
                        }

                        @Override
                        public void onMessage(String message) {
                            Log.d(TAG, "client ===> onMessage() 1 ");
                        }

                        @RequiresApi(api = Build.VERSION_CODES.M)
                        @Override
                        public void onMessage(ByteBuffer bytes) {
                            super.onMessage(bytes);
                            byte[] data = new byte[bytes.remaining()];
                            bytes.get(data);
                            Log.d(TAG, "client ===> onMessage() 2 , data = " + data.length);
                            doCallbackData(data);
                        }

                        @Override
                        public void onClose(int code, String reason, boolean remote) {
                            Log.d(TAG, "client ===> onClose() , reason = " + reason);
                            button.post(new Runnable() {
                                @Override
                                public void run() {
                                    button.setVisibility(View.VISIBLE);
                                }
                            });
                        }

                        @Override
                        public void onError(Exception ex) {
                            Log.d(TAG, "client ===> onError() , ex = " + ex.getMessage());
                            button.post(new Runnable() {
                                @Override
                                public void run() {
                                    button.setVisibility(View.VISIBLE);
                                }
                            });
                        }
                    };
                } catch (URISyntaxException e) {
                    Log.e(TAG, "client ===> init socket , e = " + e.toString());
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
            // ???????????????
            format.setInteger(MediaFormat.KEY_BIT_RATE, 720 * 1080);
            // ????????????
            format.setInteger(MediaFormat.KEY_FRAME_RATE, 20);
            // ???????????????????????????
            format.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1);
//            //??????
//            byte[] header_sps = {0, 0, 0, 1, 103, 66, -128, 31, -38, 2, -48, 40, 104, 6, -48, -95, 53};
//            byte[] header_pps = {0, 0 ,0, 1, 104, -50, 6, -30};
//            /*//??????
//            byte[] header_sps = {0, 0, 0, 1, 103, 66, -128, 31, -38, 1, 64, 22, -24, 6, -48, -95, 53};
//            byte[] header_pps = {0, 0 ,0, 1, 104, -50, 6, -30};*/
//            format.setByteBuffer("csd-0", ByteBuffer.wrap(header_sps));
//            format.setByteBuffer("csd-1", ByteBuffer.wrap(header_pps));
            codec = MediaCodec.createDecoderByType(MediaFormat.MIMETYPE_VIDEO_HEVC);
            codec.configure(format, surface, null, 0);
            codec.start();
        } catch (IOException e) {
            e.printStackTrace();
            Log.e(TAG, "client ===> initCodec , e = " + e.toString());
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
        Log.d(TAG, "client ===> doCallbackData ===> data = " + data.length);
        //dequeue???????????????????????????
        int id = codec.dequeueInputBuffer(100_000);
        if (id >= 0) {
            //?????????????????????input buffer??????
            ByteBuffer buffer = codec.getInputBuffer(id);
            buffer.clear();
            //???????????????buf???????????????buffer?????????
            buffer.put(data, 0, data.length);
            //?????????queue??????????????????input buffer???
            codec.queueInputBuffer(id, 0, data.length, System.currentTimeMillis(), 0);

            MediaCodec.BufferInfo bufferInfo = new MediaCodec.BufferInfo();
            //dequeue????????????????????????[a???queueInputBuffer?????????]??? ??????buffer??????
            int outId = codec.dequeueOutputBuffer(bufferInfo, 100_000);
            while (outId >= 0) {
                final int finalOutId = outId;
                //????????????surface?????????[surfaceview?????????]
                codec.releaseOutputBuffer(finalOutId, true);
                //?????????????????????????????????????????????????????????I?????????????????????vps???
                //??????dequeue???????????????
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
                Log.e(TAG, "client ===> onDestroy() , e1 = " + e.toString());
            }
        }
        if (codec != null) {
            try {
                codec.release();
                codec.flush();
                codec.stop();
            } catch (Exception e) {
                Log.e(TAG, "client ===> MainActivity onDestroy() code e2 = " + e.toString());
            }
        }
        super.onDestroy();
    }

}
