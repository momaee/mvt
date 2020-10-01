/*
 * Copyright (c) 2018, Nordic Semiconductor
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this list of conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright notice, this list of conditions and the following disclaimer in the
 * documentation and/or other materials provided with the distribution.
 *
 * 3. Neither the name of the copyright holder nor the names of its contributors may be used to endorse or promote products derived from this
 * software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
 * LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT
 * HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT
 * LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON
 * ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE
 * USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package no.nordicsemi.android.mvt_ppg;

import android.content.Intent;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.provider.MediaStore;
import android.util.Log;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;

import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.components.Legend;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.components.YAxis;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;
import com.github.mikephil.charting.interfaces.datasets.ILineDataSet;
import com.google.android.material.appbar.MaterialToolbar;

import java.io.File;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Vector;

import butterknife.ButterKnife;
import butterknife.OnClick;
import no.nordicsemi.android.ble.livedata.state.ConnectionState;
import no.nordicsemi.android.mvt_ppg.adapter.DiscoveredBluetoothDevice;
import no.nordicsemi.android.mvt_ppg.viewmodels.BlinkyViewModel;
import no.nordicsemi.android.mvt_ppg.viewmodels.SaveCSV;

@SuppressWarnings("ConstantConditions")
public class BlinkyActivity extends AppCompatActivity {
	public static final String EXTRA_DEVICE = "no.nordicsemi.android.mvt_ppg.EXTRA_DEVICE";

	private BlinkyViewModel viewModel;
	private LineChart mChart;

	String baseDir;
	String fileName;
	String fileFormat;
	String filePath;
	List<String[]> list;
	File file;
	SaveCSV sCSV;

	long start = java.lang.System.currentTimeMillis();
	int max_x = 0;
	float max_y = 0;
	int min_x = 0;
	float min_y = 0;

	int first_x = 0;
	float first_y = 0;
	int second_x = 0;
	float second_y = 0;
	int third_x = 0;
	float third_y = 0;

	Vector<Integer> maximums_x = new Vector<Integer>();
	Vector<Float>  maximums_y = new Vector<Float>();
	Vector<Integer>  minimums_x = new Vector<Integer>();
	Vector<Float>  minimums_y = new Vector<Float>();

	int max_visible_range = 200;

	@RequiresApi(api = Build.VERSION_CODES.O)
	@Override
	protected void onCreate(final Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		setContentView(R.layout.activity_blinky);
		ButterKnife.bind(this);

		final Intent intent = getIntent();
		final DiscoveredBluetoothDevice device = intent.getParcelableExtra(EXTRA_DEVICE);
		final String deviceName = device.getName();
		final String deviceAddress = device.getAddress();

		final MaterialToolbar toolbar = findViewById(R.id.toolbar);
		toolbar.setTitle(deviceName != null ? deviceName : getString(R.string.unknown_device));
		toolbar.setSubtitle(deviceAddress);
		setSupportActionBar(toolbar);
		getSupportActionBar().setDisplayHomeAsUpEnabled(true);

		baseDir = "mvtPath";
		fileName = "mvt";
		fileFormat = ".csv";
		filePath = fileName + new Date().toString() + fileFormat;
		list = new ArrayList<String[]>();
		file = new File(getExternalFilesDir(baseDir), filePath);
		sCSV = new SaveCSV(file);

		// Configure the view model.
		viewModel = new ViewModelProvider(this).get(BlinkyViewModel.class);
		viewModel.connect(device);

		// Set up views.
		final LinearLayout progressContainer = findViewById(R.id.progress_container);
		final TextView connectionState = findViewById(R.id.connection_state);
		final View content = findViewById(R.id.device_container);
		final View notSupported = findViewById(R.id.not_supported);

		viewModel.getConnectionState().observe(this, state -> {
			switch (state.getState()) {
				case CONNECTING:
					progressContainer.setVisibility(View.VISIBLE);
					notSupported.setVisibility(View.GONE);
					connectionState.setText(R.string.state_connecting);
					break;
				case INITIALIZING:
					connectionState.setText(R.string.state_initializing);
					break;
				case READY:
					progressContainer.setVisibility(View.GONE);
					content.setVisibility(View.VISIBLE);
					onConnectionStateChanged(true);
					break;
				case DISCONNECTED:
					if (state instanceof ConnectionState.Disconnected) {
						final ConnectionState.Disconnected stateWithReason = (ConnectionState.Disconnected) state;
						if (stateWithReason.isNotSupported()) {
							progressContainer.setVisibility(View.GONE);
							notSupported.setVisibility(View.VISIBLE);
						}
					}
					// fallthrough
				case DISCONNECTING:
					onConnectionStateChanged(false);
					break;
			}
		});
		viewModel.getRxState().observe(this,
				rxData -> {
					addEntry(rxData);
				});

		chart_init();
	}

	private void chart_init(){
		mChart = (LineChart) findViewById(R.id.chart1);
		// enable description text
		mChart.getDescription().setEnabled(true);
		mChart.getDescription().setText("mvt project");
		// enable touch gestures
		mChart.setTouchEnabled(true);
		// enable scaling and dragging
		mChart.setDragEnabled(true);
		mChart.setScaleEnabled(true);
		mChart.setDrawGridBackground(false);
		// if disabled, scaling can be done on x- and y-axis separately
		mChart.setPinchZoom(true);
		// set an alternative background color
		mChart.setBackgroundColor(Color.WHITE);
		LineData data = new LineData();
		data.setValueTextColor(Color.BLACK);
		data.setDrawValues(false);
		// add empty data
		mChart.setData(data);
		// get the legend (only possible after setting data)
		Legend l = mChart.getLegend();
		// modify the legend ...
		l.setForm(Legend.LegendForm.LINE);
		l.setTextColor(Color.WHITE);

		XAxis xl = mChart.getXAxis();
		xl.setTextColor(Color.WHITE);
		xl.setDrawGridLines(true);
		xl.setAvoidFirstLastClipping(true);
		xl.setEnabled(true);

		YAxis leftAxis = mChart.getAxisLeft();
		leftAxis.setTextColor(Color.BLACK);
		leftAxis.setDrawGridLines(false);
		leftAxis.setAxisMaximum(300f);
		leftAxis.setAxisMinimum(-300f);
		leftAxis.setDrawGridLines(true);

		YAxis rightAxis = mChart.getAxisRight();
		rightAxis.setEnabled(false);

		mChart.getAxisLeft().setDrawGridLines(true);
		mChart.getXAxis().setDrawGridLines(false);
		mChart.setDrawBorders(false);
	}

	// adding new entry to chart and csv file
	private void addEntry(byte[] rxData) {

		if(rxData.length != 5 && rxData.length != 4)
			return;
		if(rxData.length == 5)
			if (rxData[4] != 13) //enter character
				return;

		LineData data = mChart.getData();

		if (data != null) {

			ILineDataSet set = data.getDataSetByIndex(0);

			if (set == null) {
				set = createSet();
				data.addDataSet(set);
			}
			float tmp = (float)ByteBuffer.wrap(rxData).getInt();

			data.addEntry(new Entry(set.getEntryCount(),  tmp), 0);
			data.notifyDataChanged();

			setAxisRange(tmp, set);

			// let the chart know it's data has changed
			mChart.notifyDataSetChanged();

			// limit the number of visible entries
			mChart.setVisibleXRangeMaximum(max_visible_range);
			// mChart.setVisibleYRange(30, AxisDependency.LEFT);

			// move to the latest entry
			mChart.moveViewToX(data.getEntryCount());

			//store data to csv file
			String[] row = new String[]{String.valueOf(set.getEntryCount()), String.valueOf(java.lang.System.currentTimeMillis()-start),
					String.valueOf(ByteBuffer.wrap(rxData).getInt()), String.valueOf(tmp)};
			list.add(row);
		}
	}

	// setting axises range automatically
	private void setAxisRange(float tmp, ILineDataSet set){
		first_x = second_x;
		first_y = second_y;
		second_x = third_x;
		second_y = third_y;
		third_x = set.getEntryCount();
		third_y = tmp;

		if(second_y > first_y && second_y > third_y){
			maximums_x.add(second_x);
			maximums_y.add(second_y);
		}
		if(second_y < first_y && second_y < third_y){
			minimums_x.add(second_x);
			minimums_y.add(second_y);
		}
		for (int i=minimums_x.size()-1; i >= 0; i--){
			if(set.getEntryCount() - minimums_x.get(i) > max_visible_range){
				minimums_x.remove(i);
				minimums_y.remove(i);
			}
		}
		for (int i=maximums_x.size()-1; i >= 0; i--){
			if(set.getEntryCount() - maximums_x.get(i) > max_visible_range){
				maximums_x.remove(i);
				maximums_x.remove(i);
			}
		}
		for(int i =0; i<minimums_y.size(); i++){
			if(minimums_y.get(i) < min_y){
				min_y = minimums_y.get(i);
			}
		}
		for(int i=0; i<maximums_y.size(); i++){
			if(maximums_y.get(i) > max_y){
				max_y = maximums_y.get(i);
			}

		}
		if(max_y > 0){
			mChart.getAxisLeft().setAxisMaximum(1.1f * max_y);
		} else {
			mChart.getAxisLeft().setAxisMaximum(0.9f * max_y);
		}

		if(min_y > 0){
			mChart.getAxisLeft().setAxisMinimum(0.9f * tmp);
		} else {
			mChart.getAxisLeft().setAxisMinimum(1.1f * tmp);
		}


//		if(set.getEntryCount() == 1){
//			min_y = tmp - 3000;
//			max_y = tmp + 3000;
//		}
//
//		// zoom out
//		if(tmp > 0){
//			if ( mChart.getAxisLeft().getAxisMaximum() < 1.1f * tmp )
//				mChart.getAxisLeft().setAxisMaximum(1.1f * tmp);
//			if ( mChart.getAxisLeft().getAxisMinimum() > 0.9f * tmp )
//				mChart.getAxisLeft().setAxisMinimum(0.9f * tmp);
//		} else{
//			if ( mChart.getAxisLeft().getAxisMaximum() < 0.9f * tmp )
//				mChart.getAxisLeft().setAxisMaximum(0.9f * tmp);
//			if ( mChart.getAxisLeft().getAxisMinimum() > 1.1f * tmp )
//				mChart.getAxisLeft().setAxisMinimum(1.1f * tmp);
//		}
//
//		if (tmp > max_y){
//			max_y = tmp;
//			max_x = set.getEntryCount();
//		}
//		if(set.getEntryCount() - max_x > max_visible_range){
//			max_x = set.getEntryCount();
//			if (max_y > 0)
//				max_y = max_y/3;
//			else
//				max_y = max_y*3 + 1f;
//		}
//		// zoom in -- decreasing maximum range
//		if(max_y > 0){
//			if(mChart.getAxisLeft().getAxisMaximum() > 3f * max_y )
//				mChart.getAxisLeft().setAxisMaximum(2 * max_y);
//		}else{
//			if(mChart.getAxisLeft().getAxisMaximum() > 0.3f * max_y )
//				mChart.getAxisLeft().setAxisMaximum(0.5f * max_y);
//		}
//
//		if(tmp < min_y){
//			min_x = set.getEntryCount();
//			min_y = tmp;
//		}
//		if(set.getEntryCount() - min_x > max_visible_range){
//			min_x = set.getEntryCount();
//			if (min_y < 0)
//				min_y = min_y/3;
//			else
//				min_y = min_y*3 + 1f;
//		}
//		// zoom in -- increasing minimum range
//		if(min_y < 0){
//			if ( mChart.getAxisLeft().getAxisMinimum() < 3f * min_y )
//				mChart.getAxisLeft().setAxisMinimum(2f * min_y);
//		}else {
//			if ( mChart.getAxisLeft().getAxisMinimum() < 0.3f * min_y )
//				mChart.getAxisLeft().setAxisMinimum(0.5f * min_y);
//		}
	}

	// creating new data set for using in chart
	private LineDataSet createSet() {
		LineDataSet set = new LineDataSet(null, "Dynamic Data");
		set.setAxisDependency(YAxis.AxisDependency.LEFT);
		set.setLineWidth(2f);
		set.setColor(Color.MAGENTA);
		set.setHighlightEnabled(false);
		set.setDrawValues(false);
		set.setDrawCircles(false);
		set.setMode(LineDataSet.Mode.CUBIC_BEZIER);
		set.setCubicIntensity(0.2f);
		return set;
	}

	@OnClick(R.id.action_clear_cache)
	public void onTryAgainClicked() {
		viewModel.reconnect();
	}

	private void onConnectionStateChanged(final boolean connected) {
	}

	@Override
	protected void onPause() {
		// write csv file on disk
		sCSV.save(list);
		super.onPause();
	}
}
