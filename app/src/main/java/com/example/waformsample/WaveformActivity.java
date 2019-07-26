package com.example.waformsample;

import android.content.pm.PackageManager;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.support.design.widget.Snackbar;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

public class WaveformActivity extends AppCompatActivity {
    // The sampling rate for the audio recorder.
    private static final int SAMPLING_RATE = 44100;

    private WaveformView mWaveformView;
    private TextView mDecibelView;

    private RecordingThread mRecordingThread;
    private int mBufferSize;
    private short[] mAudioBuffer;
    private String mDecibelFormat;

    private static final int REQUEST_RECORD_AUDIO = 13;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        mWaveformView = (WaveformView) findViewById(R.id.waveform_view);
        mDecibelView = (TextView) findViewById(R.id.decibel_view);
        mDecibelFormat = getResources().getString(R.string.decibel_format);

        Button bt_recorder = (Button) findViewById(R.id.bt_recorder);
        bt_recorder.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (mRecordingThread==null) {
                    startAudioRecordingSafe();
                } else {
                    mRecordingThread.stopRunning();
                    mRecordingThread = null;
                }

            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
    }

    @Override
    protected void onPause() {
        super.onPause();

        if (mRecordingThread != null) {
            mRecordingThread.stopRunning();
            mRecordingThread = null;
        }
    }

    private void requestMicrophonePermission() {
        if (ActivityCompat.shouldShowRequestPermissionRationale(this, android.Manifest.permission.RECORD_AUDIO)) {
            Log.d("dongkyu", "shouldShowRequestPermissionRationale(this, android.Manifest.permission.RECORD_AUDIO");
            // Show dialog explaining why we need record audio
            Snackbar.make(mWaveformView, "Microphone access is required in order to record audio",
                    Snackbar.LENGTH_INDEFINITE).setAction("OK", new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    ActivityCompat.requestPermissions(WaveformActivity.this, new String[]{
                            android.Manifest.permission.RECORD_AUDIO}, REQUEST_RECORD_AUDIO);
                }
            }).show();
        } else {
            Log.d("dongkyu", "requestMicrophonePermission resquestPermission");
            ActivityCompat.requestPermissions(WaveformActivity.this, new String[]{
                    android.Manifest.permission.RECORD_AUDIO}, REQUEST_RECORD_AUDIO);
        }
    }


    private void startAudioRecordingSafe() {
        if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.RECORD_AUDIO)
                == PackageManager.PERMISSION_GRANTED) {
                mRecordingThread = new RecordingThread();
                mRecordingThread.start();
        } else {
            requestMicrophonePermission();
        }
    }


    /**
     * A background thread that receives audio from the microphone and sends it to the waveform
     * visualizing view.
     */
    private class RecordingThread extends Thread {

        private boolean mShouldContinue = true;

        @Override
        public void run() {
            android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_AUDIO);

            // Compute the minimum required audio buffer size and allocate the buffer.
            mBufferSize = AudioRecord.getMinBufferSize(SAMPLING_RATE, AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT);
            mAudioBuffer = new short[mBufferSize / 2];

            if (mBufferSize == AudioRecord.ERROR || mBufferSize == AudioRecord.ERROR_BAD_VALUE) {
                mBufferSize = SAMPLING_RATE * 2;
            }
            Log.d("dongkyu","bufferSize = "+mBufferSize);

            mAudioBuffer = new short[mBufferSize / 2];

            AudioRecord record = new AudioRecord(MediaRecorder.AudioSource.MIC, SAMPLING_RATE,
                    AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, mBufferSize);

            if (record.getState() != AudioRecord.STATE_INITIALIZED) {
                Log.e("dongkyu", "Audio Record can't initialize!");
                return;
            }
            record.startRecording();

            while (shouldContinue()) {
                record.read(mAudioBuffer, 0, mBufferSize / 2);
                mWaveformView.updateAudioData(mAudioBuffer);
                updateDecibelLevel();
            }

            record.stop();
            record.release();
            Log.d("dongkyu","stop recording");
        }

        /**
         * Gets a value indicating whether the thread should continue running.
         *
         * @return true if the thread should continue running or false if it should stop
         */
        private synchronized boolean shouldContinue() {
            return mShouldContinue;
        }

        /** Notifies the thread that it should stop running at the next opportunity. */
        public synchronized void stopRunning() {
            mShouldContinue = false;
        }

        /**
         * Computes the decibel level of the current sound buffer and updates the appropriate text
         * view.
         */
        private void updateDecibelLevel() {
            // Compute the root-mean-squared of the sound buffer and then apply the formula for
            // computing the decibel level, 20 * log_10(rms). This is an uncalibrated calculation
            // that assumes no noise in the samples; with 16-bit recording, it can range from
            // -90 dB to 0 dB.
            double sum = 0;

            for (short rawSample : mAudioBuffer) {
                double sample = rawSample / 32768.0;
                sum += sample * sample;
            }

            double rms = Math.sqrt(sum / mAudioBuffer.length);
            final double db = 20 * Math.log10(rms);

            // Update the text view on the main thread.
            mDecibelView.post(new Runnable() {
                @Override
                public void run() {
                    mDecibelView.setText(String.format(mDecibelFormat, db));
                }
            });
        }


    }
}
