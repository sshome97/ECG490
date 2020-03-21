package com.example.ecg490;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.graphics.Color;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.Spinner;

import androidx.appcompat.app.AppCompatActivity;

import com.jjoe64.graphview.GraphView;
import com.jjoe64.graphview.series.DataPoint;
import com.jjoe64.graphview.series.LineGraphSeries;

public class GraphActivity extends AppCompatActivity {

    private LineGraphSeries<DataPoint> mSeries1;
    //private Runnable mTimer1;
    float samples[] = new float[250];
    static float plotData[] = new float[1000];
    public static int currentTimeSec = 0;
    public static int min = 0;
    public static int max = 1000;
    public static int flag = 0;
    float in[] = new float[3];
    float fb[] = new float[2];
    float data[] = new float[250];
    int temp_sample;
    int position = 0;
    int dataInt;
    public static final String TAG = "nRFUART";
    public GraphView graph;

    private UartService mService;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_graph);

        Intent intent = new Intent(this,UartService.class);
        bindService(intent,mServiceConnection, Context.BIND_AUTO_CREATE);

        Button disconnectButton = (Button) findViewById(R.id.disconnectButton);
        Spinner mySpinner = (Spinner) findViewById(R.id.spinner1);
        GraphView graph = (GraphView) findViewById(R.id.graph);

        disconnectButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mService.disconnect();
                setContentView(R.layout.activity_main);
            }
        });
        ArrayAdapter<String> myAdapter = new ArrayAdapter<String>(this, android.R.layout.simple_list_item_1, getResources().getStringArray(R.array.leads));
        myAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        mySpinner.setAdapter(myAdapter);
        String dataString = getIntent().getStringExtra("Data");
        Log.d(TAG, "blah blah" + dataString);

//        Integer dataInt = !dataString.equals(null) ? Integer.parseInt(dataString) : 0;
        try {
            if (dataString != null && dataString != "") {
                dataInt = !dataString.equals("") ? Integer.parseInt(dataString) : 0;
            }
        } catch (NumberFormatException e){
            dataInt = 0;
        }

        //GraphView graph = (GraphView) findViewById(R.id.graph);
        //graph.setVisibility(View.VISIBLE);

        //graph.setVisibility(View.INVISIBLE);
        mSeries1 = new LineGraphSeries<>();
        //setting the graph bounds
        graph.getViewport().setXAxisBoundsManual(true);
        graph.getViewport().setMinX(min);
        graph.getViewport().setMaxX(max);

        graph.getViewport().setYAxisBoundsManual(true);
        graph.getViewport().setMinY(-2.5);
        graph.getViewport().setMaxY(100);

        //enable scaling and scrolling
        graph.getViewport().setScrollable(true);
        graph.getViewport().setScrollableY(true);
        graph.getViewport().setScalable(true);
        graph.getViewport().setScalableY(true);

        //other parameters
        graph.setKeepScreenOn(true);
        graph.getGridLabelRenderer().setHorizontalAxisTitle("Time");
        graph.getGridLabelRenderer().setVerticalAxisTitle("mV");
        graph.getGridLabelRenderer().setHumanRounding(true);
        mSeries1.setColor(Color.BLUE);

        try {
            data[position] = dataInt;
            position++;
            if (position % 250 == 0) {


                for (int i = 0; i < 250; i++) {
                    samples[i] = data[i];
                }
                if (flag < 4) {
                    for (int i = 0; i < 250; i++)
                        plotData[flag * 250 + i] = samples[i];
                } else {
                    for (int i = 0; i < 750; i++)
                        plotData[i] = plotData[i + 250];

                    for (int i = 0; i < 250; i++)
                        plotData[i + 750] = samples[i];
                }
                flag++;

                //dataFiltering();
                //createPlotArray(samples);
                //channelFilter(samples, in, fb);


                //Get UartService data and append them on the graph
                if (flag == 1) {
                    for (int i = 10; i < 250; i++)
                        mSeries1.appendData(new DataPoint(i, plotData[i]), false, 249);//1st 250 samples - 1 sec
                } else if (flag == 2) {
                    for (int i = 10; i < 500; i++) {
                        if (i % 250 == 0)
                            i = i + 10;
                        mSeries1.appendData(new DataPoint(i, plotData[i]), false, 499);//1st 500 samples - 2 secs
                    }
                } else if (flag == 3) {
                    for (int i = 10; i < 750; i++) {
                        if (i % 250 == 0)
                            i = i + 10;
                        mSeries1.appendData(new DataPoint(i, plotData[i]), false, 749);//1st 750 samples - 3 secs
                    }
                } else if (flag == 4) {
                    for (int i = 10; i < 1000; i++) {
                        if (i % 250 == 0)
                            i = i + 10;
                        mSeries1.appendData(new DataPoint(i, plotData[i]), false, 999);// 1st 1000 samples - 4 secs
                    }
                } else {
                    for (int i = 10; i < 1000; i++) {
                        if (i % 250 == 0)
                            i = i + 10;
                        mSeries1.appendData(new DataPoint(min + i, plotData[i]), false, 1000);//refresh to graph the last 1000 samples - 4 last secs
                    }
                }

                currentTimeSec = currentTimeSec + 250;

                if (currentTimeSec == max) {
                    min = min + 250;
                    max = max + 250;
                }

                graph.addSeries(mSeries1);
                position = 0;
            }
//                RealTimeUpdate realTimeData = new RealTimeUpdate();
//                Log.d(TAG,"frag success");
//                FragmentTransaction showFrag = getSupportFragmentManager().beginTransaction();
//                showFrag.replace(R.id.frameLayout, realTimeData);
//                showFrag.commit();
//                position = 0;
        } catch (Exception e) {
        }

    }



    public void onDestroy() {
        super.onDestroy();
//        try {
//            LocalBroadcastManager.getInstance(this).unregisterReceiver(UARTStatusChangeReceiver);
//        } catch (Exception ignore) {
//            Log.e(TAG, ignore.toString());
//        }
        unbindService(mServiceConnection);
        mService.stopSelf();
        mService = null;
    }
    private ServiceConnection mServiceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            UartService.LocalBinder binder = (UartService.LocalBinder) service;
//            mService = ((UartService.LocalBinder) rawBinder).getService();

            mService = binder.getService();
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
        mService = null;
        }
    };


}
