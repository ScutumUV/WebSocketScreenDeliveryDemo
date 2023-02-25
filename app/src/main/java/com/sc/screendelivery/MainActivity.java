package com.sc.screendelivery;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.Build;
import android.os.Bundle;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Surface;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;

import static android.media.MediaCodec.CONFIGURE_FLAG_ENCODE;

import sc.websock.common.Constants;

public class MainActivity extends AppCompatActivity implements Constants {

    int width, height, dpi;
    private MediaCodec codec;
    private MediaProjectionManager projectionManager;
    private MediaProjection projection;
    private VirtualDisplay virtualDisplay;
    private ServerHandle server;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        DisplayMetrics metrics = new DisplayMetrics();
        getWindow().getWindowManager().getDefaultDisplay().getMetrics(metrics);
        width = metrics.widthPixels;
        height = metrics.heightPixels;
        dpi = metrics.densityDpi;
    }

    @RequiresApi(api = Build.VERSION_CODES.M)
    public void onClick(View view) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            requestPermissions(new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE, Manifest.permission.READ_EXTERNAL_STORAGE,
                    Manifest.permission.ACCESS_NETWORK_STATE,
                    Manifest.permission.ACCESS_WIFI_STATE,
                    Manifest.permission.CHANGE_WIFI_STATE,
                    Manifest.permission.READ_PHONE_STATE,
            }, 234);
        } else {
            initMedia();
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.M)
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == 234) {
            boolean tag = true;
            for (int i : grantResults) {
                if (i != PackageManager.PERMISSION_GRANTED) {
                    tag = false;
                    break;
                }
            }
            if (tag) {
                if (outputFile == null) {
                    outputFile = new File(getExternalFilesDir(null) + "testh265.txt");
                }
                if (outputFile.exists()) {
                    outputFile.delete();
                }
                try {
                    outputFile.createNewFile();
                } catch (IOException e) {
                    Log.e(TAG, "server ===> create file e = " + e.toString());
                }
                initMedia();
            }
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.M)
    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == 345 && resultCode == RESULT_OK) {
            projection = projectionManager.getMediaProjection(resultCode, data);
            if (projection != null) {
                start();
            }
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.M)
    private void initMedia() {
        projectionManager = (MediaProjectionManager) getSystemService(MEDIA_PROJECTION_SERVICE);
        Intent captureIntent = projectionManager.createScreenCaptureIntent();
        startActivityForResult(captureIntent, 345);
    }

    @RequiresApi(api = Build.VERSION_CODES.M)
    private void start() {
        try {
            int formatWidth = width;
            int formatHeight = height;
            if ((formatWidth & 1) == 1) {
                formatWidth--;
            }
            if ((formatHeight & 1) == 1) {
                formatHeight--;
            }
            MediaFormat format = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_HEVC, 720, 1080);
            //数据来源于Surface
            format.setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface);
            // 指定比特率
            format.setInteger(MediaFormat.KEY_BIT_RATE, 720 * 1080);
            // 指定帧率
            format.setInteger(MediaFormat.KEY_FRAME_RATE, 20);
            // 指定关键帧时间间隔
            format.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1);

            codec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_HEVC);
            codec.configure(format, null, null, CONFIGURE_FLAG_ENCODE);
            Surface surface = codec.createInputSurface();
            //创建场地
            virtualDisplay = projection.createVirtualDisplay(
                    "push-display",
                    720, 1080, 1,
                    DisplayManager.VIRTUAL_DISPLAY_FLAG_PUBLIC, surface, null, null);
            startRecorder();
            codec.start();
        } catch (Exception e) {
            e.printStackTrace();
            Log.e(TAG, "server start ===> exception , e = " + e.getMessage());
        }
    }

    private void startRecorder() {
        /*final File file = new File(Environment.getExternalStorageDirectory().getAbsolutePath() + "screen1.mp4");
        if (file.exists()) {
            file.delete();
        }
        if (!file.exists()) {
            file.mkdirs();
        }*/
        new Thread(new Runnable() {
            @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
            @Override
            public void run() {
                MediaCodec.BufferInfo bufferInfo = new MediaCodec.BufferInfo();
                while (true) {
                    int id = 0;
                    try {
                        id = codec.dequeueOutputBuffer(bufferInfo, 10000);
                        if (id >= 0) {
                            //获取编码好的H265的数据
                            ByteBuffer buffer = codec.getOutputBuffer(id);
                            dealFrame(buffer, bufferInfo);
//                            byte[] data = new byte[bufferInfo.size];
//                            buffer.get(data);
//                            if (server == null) {
//                                server = new ServerHandle();
//                            }
//                            server.sendData(data);
                        }
                    } catch (Exception e) {
                        Log.e(TAG, e.toString());
                    } finally {
                        try {
                            if (id > 0) {
                                codec.releaseOutputBuffer(id, false);
                            }
                        } catch (Exception e) {
                            Log.e(TAG, e.toString());
                        }
                    }
                }
            }
        }).start();
    }


    private static final int NAL_I = 19;
    private static final int NAL_VPS = 32;

    private byte[] vps_sps_pps_buf;
    private File outputFile;

    private void dealFrame(ByteBuffer bb, MediaCodec.BufferInfo bufferInfo) {
        if (bb == null) {
            return;
        }
        int offset = 4;
        if (bb.get(2) == 0x01) {
            offset = 3;
        }
        int type = (bb.get(offset) & 0x7E) >> 1;
        byte[] sendData = null;
        Log.d(TAG, "server ===> dealFrame() , type = " + type + " , type=NAL_I = " + (type == NAL_I));
        //VPS数据直接记录下来，拼接到I帧前面，一起发送
        if (type == NAL_VPS) {
            vps_sps_pps_buf = new byte[bufferInfo.size];
            bb.get(vps_sps_pps_buf);
        }
        //网络传输中，I帧数据，前面拼接VPS，SPS和PPS等数据
        else if (type == NAL_I) {
            final byte[] bytes = new byte[bufferInfo.size];
            bb.get(bytes);

            sendData = new byte[vps_sps_pps_buf.length + bytes.length];
            System.arraycopy(vps_sps_pps_buf, 0, sendData, 0, vps_sps_pps_buf.length);
            System.arraycopy(bytes, 0, sendData, vps_sps_pps_buf.length, bytes.length);
        }
        //其它B帧或者P帧数据，直接传输
        else {
            sendData = new byte[bufferInfo.size];
            bb.get(sendData);
        }
        if (server == null) {
            server = new ServerHandle();
        }
        if (sendData != null) {
            /*FileOutputStream os = null;
            try {
                os = new FileOutputStream(outputFile);
                os.write(sendData);
            } catch (Exception e) {
                Log.e(TAG, "server ===> byte save to file error = " + e.toString());
            } finally {
                if (os != null) {
                    try {
                        os.close();
                    } catch (Exception e) {
                        Log.e(TAG, "server ===> os close error = " + e.toString());
                    }
                }
            }*/
            server.sendData(sendData);
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    @Override
    protected void onDestroy() {
        if (server != null) {
            server.destroy();
        }
        if (codec != null) {
            try {
                codec.release();
                codec.flush();
                codec.stop();
            } catch (Exception e) {
                Log.e(TAG, "server ===> MainActivity onDestroy() code e1 = " + e.toString());
            }
        }
        if (projection != null) {
            try {
                projection.stop();
            } catch (Exception e) {
                Log.e(TAG, "server ===> MainActivity onDestroy() code e2 = " + e.toString());
            }
        }
        projectionManager = null;
        super.onDestroy();
    }
}