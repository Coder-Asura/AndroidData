package cc.tiro.csh.libmp3decoder;

import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.widget.TextView;


import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.StringReader;
import java.nio.ByteBuffer;

import csh.tiro.cc.FolderFilePicker;
import csh.tiro.cc.Libmad;

public class MainActivity extends AppCompatActivity implements View.OnClickListener
{

    private TextView srcPath;
    private Button loadSrcBtn;
    private Button decodeBtn;

    private String srcFilePath;
    private String decFilePath;

    MediaExtractor mediaExtractor;
    MediaCodec mediaDecode;
    ByteBuffer[] decodeInputBuffers;
    ByteBuffer[] decodeOutputBuffers;
    MediaCodec.BufferInfo decodeBufferInfo;

    String TAG = "MP3Decoder";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        srcPath = (TextView) findViewById(R.id.srcpath);
        loadSrcBtn = (Button) findViewById(R.id.srcpathLoad);
        loadSrcBtn.setOnClickListener(this);
        decodeBtn = (Button) findViewById(R.id.decode);
        decodeBtn.setOnClickListener(this);

    }

    @Override
    public void onClick(View view)
    {
        switch (view.getId()){
            case R.id.srcpathLoad:
                FolderFilePicker picker = new FolderFilePicker(this, new FolderFilePicker.PickPathEvent() {
                    @Override
                    public void onPickEvent(String resultPath) {
                        srcPath.setText(resultPath);
                        srcFilePath = resultPath;
                        decFilePath = resultPath.replace(".mp3",".pcm");

                    }
                },"mp3");
                picker.show();
                break;
            case R.id.decode:


                decodeBtn.setEnabled(false);

                long startTime=System.currentTimeMillis();   //获取开始时间

                Libmad.decodeFile(srcFilePath,decFilePath);

                long endTime=System.currentTimeMillis(); //获取结束时间


                Toast.makeText(this,"总共耗时(ms):"+(endTime-startTime),Toast.LENGTH_SHORT).show();


                decodeBtn.setEnabled(true);
                //DecodeFileInitial(srcFilePath);
                //srcAudioFormatToPCM();

                break;
        }
    }

    private void DecodeFileInitial(String srcPath)
    {
        try {
            mediaExtractor=new MediaExtractor();//此类可分离视频文件的音轨和视频轨道
            mediaExtractor.setDataSource(srcPath);//媒体文件的位置
            for (int i = 0; i < mediaExtractor.getTrackCount(); i++)
            {
                //遍历媒体轨道 此处我们传入的是音频文件，所以也就只有一条轨道
                MediaFormat format = mediaExtractor.getTrackFormat(i);
                String mime = format.getString(MediaFormat.KEY_MIME);
                if (mime.startsWith("audio"))
                {
                    //获取音频轨道
                    mediaExtractor.selectTrack(i);//选择此音频轨道
                    mediaDecode = MediaCodec.createDecoderByType(mime);//创建Decode解码器
                    mediaDecode.configure(format, null, null, 0);
                    break;
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

        if (mediaDecode == null) {
            Log.e(TAG, "create mediaDecode failed");
            return;
        }
        mediaDecode.start();//启动MediaCodec ，等待传入数据
        decodeInputBuffers=mediaDecode.getInputBuffers();//MediaCodec在此ByteBuffer[]中获取输入数据
        decodeOutputBuffers=mediaDecode.getOutputBuffers();//MediaCodec将解码后的数据放到此ByteBuffer[]中 我们可以直接在这里面得到PCM数据
        decodeBufferInfo=new MediaCodec.BufferInfo();//用于描述解码得到的byte[]数据的相关信息
    }


    private void srcAudioFormatToPCM()
    {
        boolean codeOver = false;
        long    decodeSize = 0;

        for (int i = 0; i < decodeInputBuffers.length-1; i++)
        {
            int inputIndex = mediaDecode.dequeueInputBuffer(-1);//获取可用的inputBuffer -1代表一直等待，0表示不等待 建议-1,避免丢帧
            if (inputIndex < 0) {
                codeOver =true;
                return;
            }

            ByteBuffer inputBuffer = decodeInputBuffers[inputIndex];//拿到inputBuffer
            inputBuffer.clear();//清空之前传入inputBuffer内的数据
            int sampleSize = mediaExtractor.readSampleData(inputBuffer, 0);//MediaExtractor读取数据到inputBuffer中
            if (sampleSize <0) {//小于0 代表所有数据已读取完成
                codeOver=true;
            }
            else {
                mediaDecode.queueInputBuffer(inputIndex, 0, sampleSize, 0, 0);//通知MediaDecode解码刚刚传入的数据
                mediaExtractor.advance();//MediaExtractor移动到下一取样处
                decodeSize+=sampleSize;
            }
        }

        //获取解码得到的byte[]数据 参数BufferInfo上面已介绍 10000同样为等待时间 同上-1代表一直等待，0代表不等待。此处单位为微秒
        //此处建议不要填-1 有些时候并没有数据输出，那么他就会一直卡在这 等待
        int outputIndex = mediaDecode.dequeueOutputBuffer(decodeBufferInfo, 10000);

        ByteBuffer outputBuffer;
        byte[] chunkPCM;

        try {
            FileOutputStream fileOutputStream = new FileOutputStream(decFilePath);
            while (outputIndex >= 0)
            {
                //每次解码完成的数据不一定能一次吐出 所以用while循环，保证解码器吐出所有数据
                outputBuffer = decodeOutputBuffers[outputIndex];//拿到用于存放PCM数据的Buffer
                chunkPCM = new byte[decodeBufferInfo.size];//BufferInfo内定义了此数据块的大小
                outputBuffer.get(chunkPCM);//将Buffer内的数据取出到字节数组中
                outputBuffer.clear();//数据取出后一定记得清空此Buffer MediaCodec是循环使用这些Buffer的，不清空下次会得到同样的数据
                fileOutputStream.write(chunkPCM);
                mediaDecode.releaseOutputBuffer(outputIndex, false);//此操作一定要做，不然MediaCodec用完所有的Buffer后 将不能向外输出数据
                outputIndex = mediaDecode.dequeueOutputBuffer(decodeBufferInfo, 10000);//再次获取数据，如果没有数据输出则outputIndex=-1 循环结束
            }
            fileOutputStream.close();
        }catch (Exception e){
            e.printStackTrace();
        }

    }

}