package com.example.ecg490;

import android.content.Context;
import android.graphics.Color;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.Spinner;

import androidx.fragment.app.Fragment;

import com.jjoe64.graphview.GraphView;
import com.jjoe64.graphview.series.DataPoint;
import com.jjoe64.graphview.series.LineGraphSeries;

public class RealTimeUpdate extends Fragment {
    //private final Handler mHandler = new Handler();
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

    private UartService mService = null;

    //public native void channelFilter(float[] input, float[] _in, float[] _fb);

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {

        View rootView = inflater.inflate(R.layout.graph_viewer, container, false);
        GraphView graph = (GraphView) rootView.findViewById(R.id.graph);
        mSeries1 = new LineGraphSeries<>();
        Spinner mySpinner = (Spinner) rootView.findViewById(R.id.spinner1);
        ArrayAdapter<String> myAdapter = new ArrayAdapter<String>(this.getActivity(), android.R.layout.simple_list_item_1, getResources().getStringArray(R.array.leads));
        myAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        mySpinner.setAdapter(myAdapter);

        Button disconnectButton = (Button) rootView.findViewById(R.id.disconnectButton);
        disconnectButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                MainActivity activity = (MainActivity) getActivity();
                activity.disconnect();
            }
        });


        //setting the graph bounds
        graph.getViewport().setXAxisBoundsManual(true);
        graph.getViewport().setMinX(min);
        graph.getViewport().setMaxX(max);
        /*graph.getViewport().setYAxisBoundsManual(true);
        graph.getViewport().setMinY(-2.5);
        graph.getViewport().setMaxY(2.5);*/
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
        mSeries1.setColor(Color.BLUE);

        //Get UartService data and append them on the graph
        if (flag == 1) {
            for (int i = 10; i < 100; i++)
                mSeries1.appendData(new DataPoint(i, plotData[i]), false, 249);//1st 250 samples - 1 sec
        } else if (flag == 2) {
            for (int i = 10; i < 200; i++) {
                if (i % 250 == 0)
                    i = i + 10;
                mSeries1.appendData(new DataPoint(i, plotData[i]), false, 499);//1st 500 samples - 2 secs
            }
        } else if (flag == 3) {
            for (int i = 10; i < 300; i++) {
                if (i % 250 == 0)
                    i = i + 10;
                mSeries1.appendData(new DataPoint(i, plotData[i]), false, 749);//1st 750 samples - 3 secs
            }
        } else if (flag == 4) {
            for (int i = 10; i < 400; i++) {
                if (i % 250 == 0)
                    i = i + 10;
                mSeries1.appendData(new DataPoint(i, plotData[i]), false, 999);// 1st 1000 samples - 4 secs
            }
        } else {
            for (int i = 10; i < 500; i++) {
                if (i % 250 == 0)
                    i = i + 10;
                mSeries1.appendData(new DataPoint(min + i, plotData[i]), false, 1000);//refresh to graph the last 1000 samples - 4 last secs
            }
        }

        currentTimeSec = currentTimeSec + 100;

        if (currentTimeSec == max) {
            min = min + 100;
            max = max + 100;
        }

        graph.addSeries(mSeries1);

        return rootView;
    }

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
        for (int i = 0; i < 100; i++) {
            samples[i] = ((MainActivity) getActivity()).data[i];
        }

        //dataFiltering();
        //createPlotArray(samples);
        //channelFilter(samples, in, fb);
        createPlotArray(samples);
    }

    public void dataFiltering() {

    }

    public void createPlotArray(float[] temp) {
        if (flag < 4) {
            for (int i = 0; i < 100; i++)
                plotData[flag * 100 + i] = temp[i];
        } else {
            for (int i = 0; i < 300; i++)
                plotData[i] = plotData[i + 100];

            for (int i = 0; i < 100; i++)
                plotData[i + 300] = temp[i];
        }
        flag++;
    }
}
