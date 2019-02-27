package com.example.franklin.sensorcapture;

import android.media.MediaRecorder;
import android.os.Environment;

import java.io.IOException;
import java.util.Calendar;

public class SoundMeter {

    private MediaRecorder mRecorder = null;

    public void start() {
        if (mRecorder == null) {
            mRecorder = new MediaRecorder();
            mRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
            mRecorder.setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP);
            mRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB);
            mRecorder.setOutputFile(Environment.getExternalStorageDirectory().getAbsolutePath() + "/" + Calendar.getInstance().getTime().toString() + ".wav");
            mRecorder.setMaxDuration(360000000);
            mRecorder.setMaxFileSize(1000000000);
            try{
            mRecorder.prepare();
            mRecorder.start();}
            catch(IllegalStateException e)
            {
                e.printStackTrace();
            }
            catch (IOException e) {
                e.printStackTrace();;
            }

        }
    }

    public void stop() {
        if (mRecorder != null) {
            mRecorder.stop();
            mRecorder.release();
            mRecorder = null;
        }
    }

    public double getAmplitude() {
        if (mRecorder != null)
            return  mRecorder.getMaxAmplitude();
        else
            return 0;

    }
}