package sample.ble.sensortag;

import android.app.Activity;
import android.app.Fragment;
import android.app.ListActivity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Color;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.RotateAnimation;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.text.DecimalFormat;
import java.util.ArrayList;

import sample.ble.sensortag.adapters.BleDevicesAdapter;
import sample.ble.sensortag.ble.BleDevicesScanner;
import sample.ble.sensortag.ble.BleUtils;
import sample.ble.sensortag.config.AppConfig;
import sample.ble.sensortag.dialogs.EnableBluetoothDialog;
import sample.ble.sensortag.dialogs.ErrorDialog;
import sample.ble.sensortag.fusion.SensorFusionActivity;

/**
 * Activity for scanning and displaying available Bluetooth LE devices.
 */
public class DeviceScanActivity extends ListActivity
        implements ErrorDialog.ErrorDialogListener,
                   EnableBluetoothDialog.EnableBluetoothDialogListener {

    private static final int REQUEST_ENABLE_BT = 1;
    private static final long SCAN_PERIOD = 5000;

    private BleDevicesAdapter leDeviceListAdapter;
    private BluetoothAdapter bluetoothAdapter;
    private BleDevicesScanner scanner;
    public String RSSI;
    SensorManager mSensorManager;
    Sensor mOrientationSensor;
    ImageView image;
    float currentDegree = 0f;;
    float north;
    ArrayList<String> RSSI_Values = new ArrayList<String>();
    ArrayList<String> RSSI_list = new ArrayList<String>();
    double lastAngle = 0;
    int count = 0;
    float last_RSSI = 0; //Used to determine the Final RSSi value at the end of weighted RSSI algorithm

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setTitle(R.string.title_devices);

        setContentView(R.layout.device_scan_activity);


        mSensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        mOrientationSensor = mSensorManager.getDefaultSensor(Sensor.TYPE_ORIENTATION);

        mSensorManager.registerListener(orientationListener, mOrientationSensor, SensorManager.SENSOR_DELAY_NORMAL);


        final int bleStatus = BleUtils.getBleStatus(getBaseContext());
        switch (bleStatus) {
            case BleUtils.STATUS_BLE_NOT_AVAILABLE:
                ErrorDialog.newInstance(R.string.dialog_error_no_ble).show(getFragmentManager(), ErrorDialog.TAG);
                return;
            case BleUtils.STATUS_BLUETOOTH_NOT_AVAILABLE:
                ErrorDialog.newInstance(R.string.dialog_error_no_bluetooth).show(getFragmentManager(), ErrorDialog.TAG);
                return;
            default:
                bluetoothAdapter = BleUtils.getBluetoothAdapter(getBaseContext());
                bluetoothAdapter.startDiscovery();
        }

        if (bluetoothAdapter == null)
            return;

///////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
///////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// initialize scanner
        scanner = new BleDevicesScanner(bluetoothAdapter, new BluetoothAdapter.LeScanCallback() {
            @Override
            public void onLeScan(final BluetoothDevice device, final int rssi, byte[] scanRecord) {
                leDeviceListAdapter.addDevice(device, rssi);
                leDeviceListAdapter.notifyDataSetChanged();
                RSSI = Integer.toString(rssi);
                count++;
                push_rssi(RSSI);
            }
        });
        scanner.setScanPeriod(SCAN_PERIOD);


        /*

        // Create a BroadcastReceiver for ACTION_FOUND

       final BroadcastReceiver mReceiver = new BroadcastReceiver() {
            public void onReceive(Context context, Intent intent) {
                String action = intent.getAction();
                // When discovery finds a device
                if (BluetoothDevice.ACTION_FOUND.equals(action)) {
                    bluetoothAdapter.cancelDiscovery();
                    // Get the BluetoothDevice object from the Intent
                    BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                    // Add the name and address to an array adapter to show in a ListView
                    Log.i("Connected to", device.getName());
                    short rssi = intent.getShortExtra(BluetoothDevice.EXTRA_RSSI, (short) 0);
                    push_rssi(Short.toString(rssi));

                    TextView textView_dist = (TextView) findViewById(R.id.textView2);
                    double distance = 12*2.54/100.0*Math.pow(10,-(rssi+62.0)/23.0);

                    if (distance > 5){
                        textView_dist.setText(String.format("Distance: %.2f m", distance));
                        textView_dist.setTextColor(Color.RED);
                        image.setImageResource(R.drawable.arrowred);
                    }
                    else{
                        textView_dist.setText(String.format("Distance: %.2f m", distance));
                        textView_dist.setTextColor(Color.GREEN);
                        image.setImageResource(R.drawable.arrowgreen);
                    }

                    bluetoothAdapter.startDiscovery();

                }
                else if (BluetoothAdapter.ACTION_DISCOVERY_FINISHED.equals(action))
                {

                    bluetoothAdapter.startDiscovery();
                }
            }
        };

// Register the BroadcastReceiver
        IntentFilter filter = new IntentFilter(BluetoothDevice.ACTION_FOUND);
        registerReceiver(mReceiver, filter); // Don't forget to unregister during onDestroy

*/

///////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
///////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Calculate Distance and update the direction
        FrameLayout rlayout = (FrameLayout) findViewById(R.id.mainlayout);
        rlayout.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View v) {



                TextView textView_dist = (TextView) findViewById(R.id.textView2);
                TextView textView_update = (TextView) findViewById(R.id.textView);




                if (RSSI_list.size() != 0){
                    float rssi = average_rssi();
                    try {
                        writeToFile(rssi);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                    double distance = Math.pow(10,(rssi+76.3846)/(-14.76));



                    DecimalFormat df = new DecimalFormat("#.##");
                    distance = Double.valueOf(df.format(distance));
                    double angle;

                    if (RSSI_Values.size() < 1) {
                        RSSI_Values.add(String.valueOf(distance));
                        textView_update.setText("Pointing to walking direction");
                        angle = 0;
                        image = (ImageView) findViewById(R.id.imageView);

                        // create a rotation animation (reverse turn degree degrees)
                        RotateAnimation ra = new RotateAnimation(
                                currentDegree,
                                (float) angle,
                                Animation.RELATIVE_TO_SELF, 0.5f,
                                Animation.RELATIVE_TO_SELF,
                                0.5f);

                        // how long the animation will take place
                        ra.setDuration(210);

                        // set the animation after the end of the reservation status
                        ra.setFillAfter(true);

                        // Start the animation
                        image.startAnimation(ra);
                        currentDegree = (float) angle;
                    }

                    else{


                        RSSI_Values.add(String.valueOf(distance));
                        double dist1 = Float.parseFloat(RSSI_Values.get(0));
                        double dist2 = Float.parseFloat(RSSI_Values.get(1));
                        double dist3 = 1;
                        angle =  (Math.pow(dist2, 2)) + ( Math.pow(dist3, 2)) - (Math.pow(dist1, 2));
                        angle = angle/(2*dist2*dist3);
                        angle = Math.acos(angle) * (180/Math.PI);
                        angle = 180 - angle ;
                        //angle = 360 - angle;



                        RSSI_Values.remove(0);

                        if (!Double.isNaN(angle)){
                            textView_update.setText(String.format("Angle: %.2f deg", angle));

                            image = (ImageView) findViewById(R.id.imageView);

                            if (dist2 > 5){
                                textView_dist.setText(String.format("Distance: %.2f m", dist2));
                                textView_dist.setTextColor(Color.RED);
                                image.setImageResource(R.drawable.arrowred);
                            }
                            else{
                                textView_dist.setText(String.format("Distance: %.2f m", dist2));
                                textView_dist.setTextColor(Color.GREEN);
                                image.setImageResource(R.drawable.arrowgreen);
                            }






                            // create a rotation animation (reverse turn degree degrees)
                            RotateAnimation ra = new RotateAnimation(
                                    currentDegree,
                                    (float) angle,
                                    Animation.RELATIVE_TO_SELF, 0.5f,
                                    Animation.RELATIVE_TO_SELF,
                                    0.5f);

                            // how long the animation will take place
                            ra.setDuration(210);

                            // set the animation after the end of the reservation status
                            ra.setFillAfter(true);

                            // Start the animation
                            image.startAnimation(ra);
                            currentDegree = (float) angle;
                            lastAngle = angle;
                        }
                        else{
                            textView_update.setText("Error While calculating the angle.");
                            RSSI_Values.clear();
                        }
                    }

                }

            }

        });


    }

///////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
///////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Write the RSSI value to the file

    private void writeToFile(float rssi) throws IOException {
        File root = new File(Environment.getExternalStorageDirectory(), "/RSSI Data");
        if (!root.exists()) {
            root.mkdirs();
        }
        File out = new File(root, "/RSSIdata.txt");
        OutputStreamWriter outputStreamWriter = null;
        FileOutputStream outStream = null;

        if ( out.exists() == false ){
            out.createNewFile();
        }

        outStream = new FileOutputStream(out, true);
        outputStreamWriter = new OutputStreamWriter(outStream);
        outputStreamWriter.write("Starting Data collection at new point\n");

        for( int j=0;  j < RSSI_list.size() -1;  j++ )
        {
            outputStreamWriter.write(RSSI_list.get(j) + "\n");
        }

        outputStreamWriter.write("Ending Data Collection at this point\n");
        outputStreamWriter.write("====================================\n\n");

        outputStreamWriter.close();


    }


///////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
///////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Push Latest RSSI value into List
    private void push_rssi(String rssi) {
        if (rssi != null){
            if (RSSI_list.size() >= 8){
                RSSI_list.remove(0);
            }

            RSSI_list.add(rssi);
            if (count >= 8){
                weighted_rssi();
                TextView textView_update = (TextView) findViewById(R.id.textView);
                textView_update.setText("Weighted RSSI: " + String.valueOf(last_RSSI));
            }
        }

    }

///////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
///////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Start Magnetometer Sensor Listener
    SensorEventListener orientationListener = new SensorEventListener() {
        @Override
        public void onAccuracyChanged(Sensor sensor, int acc) {
        }

        @Override
        public void onSensorChanged(SensorEvent event) {

            //View view = LayoutInflater.from(getApplication()).inflate(R.layout.device_services_activity, null);


            TextView textView_update = (TextView) findViewById(R.id.textView);
            north = Math.round(event.values[0]);

            if (RSSI_Values.size() < 1) {
                image = (ImageView) findViewById(R.id.imageView);

                // create a rotation animation (reverse turn degree degrees)
                RotateAnimation ra = new RotateAnimation(
                        currentDegree,
                        -north,
                        Animation.RELATIVE_TO_SELF, 0.5f,
                        Animation.RELATIVE_TO_SELF,
                        0.5f);

                // how long the animation will take place
                ra.setDuration(210);

                // set the animation after the end of the reservation status
                ra.setFillAfter(true);

                // Start the animation
                image.startAnimation(ra);
                //textView_update.setText("Currently Pointing to North");
                currentDegree = -north;
            }


        }


    };





    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.gatt_scan, menu);

        if (!AppConfig.DEBUG)
            menu.findItem(R.id.menu_demo).setVisible(false);

        if (scanner == null || !scanner.isScanning()) {
            menu.findItem(R.id.menu_stop).setVisible(false);
            menu.findItem(R.id.menu_scan).setVisible(true);
            menu.findItem(R.id.menu_refresh).setActionView(null);
        } else {
            menu.findItem(R.id.menu_stop).setVisible(true);
            menu.findItem(R.id.menu_scan).setVisible(false);
            menu.findItem(R.id.menu_refresh).setActionView(
                    R.layout.ab_indeterminate_progress);
        }
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.menu_scan:
                leDeviceListAdapter.clear();
                if (scanner != null)
                    scanner.start();
                invalidateOptionsMenu();
                break;
            case R.id.menu_stop:
                if (scanner != null)
                    scanner.stop();
                invalidateOptionsMenu();
                break;
            case R.id.menu_demo:
                final Intent demoIntent = new Intent(
                        getBaseContext(), SensorFusionActivity.class);
                startActivity(demoIntent);
                break;
        }
        return true;
    }

    @Override
    protected void onResume() {
        super.onResume();

        if (bluetoothAdapter == null)
            return;

        // Ensures Bluetooth is enabled on the device.  If Bluetooth is not currently enabled,
        // fire an intent to display a dialog asking the user to grant permission to enable it.
        if (!bluetoothAdapter.isEnabled()) {
            final Fragment f = getFragmentManager().findFragmentByTag(EnableBluetoothDialog.TAG);
            if (f == null)
                new EnableBluetoothDialog().show(getFragmentManager(), EnableBluetoothDialog.TAG);
            return;
        }

        init();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        // User chose not to enable Bluetooth.
        if (requestCode == REQUEST_ENABLE_BT) {
            if (resultCode == Activity.RESULT_CANCELED) {
                finish();
            } else {
                init();
            }
        }
        super.onActivityResult(requestCode, resultCode, data);
    }

    @Override
    protected void onPause() {
        super.onPause();

        //if (scanner != null)
            //scanner.stop();

    }

    @Override
    protected void onListItemClick(ListView l, View v, int position, long id) {
        final BluetoothDevice device = leDeviceListAdapter.getDevice(position);
        if (device == null)
            return;

        final Intent intent = new Intent(this, DeviceServicesActivity.class);
        intent.putExtra(DeviceServicesActivity.EXTRAS_DEVICE_NAME, device.getName());
        intent.putExtra(DeviceServicesActivity.EXTRAS_DEVICE_ADDRESS, device.getAddress());
        intent.putExtra(DeviceServicesActivity.EXTRAS_DEVICE_rssi, RSSI);
        startActivity(intent);
    }


///////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
///////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Calculate the weighted RSSI
    private void weighted_rssi() {
        float tau = 1;
        double lambda = 1 + Math.pow(Math.E, (-tau)) + Math.pow(Math.E, (-2*tau)) + Math.pow(Math.E, (-3*tau)) + Math.pow(Math.E, (-4*tau)) + Math.pow(Math.E, (-5*tau)) + Math.pow(Math.E, (-6*tau)) + Math.pow(Math.E, (-7*tau));
        float RSSI_SUM = 0;
        double a = 0.5;

        for (int i = 0; i < 8; i ++){
            float curr_RSSI = Float.parseFloat(RSSI_list.get(i));
            float w = (float) (Math.pow(Math.E, (-i*tau))/lambda);
            curr_RSSI *= w;
            RSSI_SUM += curr_RSSI;
        }

        RSSI_SUM /= 1;

        if (last_RSSI != 0) {
            last_RSSI = (float) ((a*RSSI_SUM) + ((1-a)*last_RSSI));
        }
        else {
            last_RSSI = RSSI_SUM;
        }

    }



///////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
///////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Calculate the average RSSI
    private float average_rssi() {
        float curr;
        float sum;
        ArrayList<String> RSSI_sorted = new ArrayList<String>();
        RSSI_sorted = RSSI_list;

        int j;
        boolean flag = true;   // set flag to true to begin first pass
        int temp;   //holding variable

        while ( flag )
        {
            flag= false;    //set flag to false awaiting a possible swap
            for( j=0;  j < RSSI_sorted.size() -1;  j++ )
            {
                if ( Integer.parseInt(RSSI_sorted.get(j)) < Integer.parseInt(RSSI_sorted.get(j+1)) )   // change to > for ascending sort
                {
                    temp = Integer.parseInt(RSSI_sorted.get(j));                //swap elements
                    RSSI_sorted.set(j, RSSI_sorted.get(j+1));
                    RSSI_sorted.set(j+1, String.valueOf(temp));
                    flag = true;              //shows a swap occurred
                }
            }
        }

        sum = 0;
        j = 0;
        int min = RSSI_sorted.size() / 4;
        int max = RSSI_sorted.size() -  (RSSI_sorted.size() / 4);
        for (j = min; j<max; j++){
            curr = Float.parseFloat(RSSI_sorted.get(j));
            sum += curr;
        }

        /*for (String curr_rssi : RSSI_list){
            curr = Float.parseFloat(curr_rssi);
            sum += curr;

        }*/

        sum /= (max - min);

        return sum;
        //return Float.parseFloat(RSSI_list.get(RSSI_list.size() - 1));
    }



    private void init() {
        if (leDeviceListAdapter == null) {
            leDeviceListAdapter = new BleDevicesAdapter(getBaseContext());
            setListAdapter(leDeviceListAdapter);
        }

        //scanner.start();
        invalidateOptionsMenu();
    }

    @Override
    public void onEnableBluetooth(EnableBluetoothDialog f) {
        bluetoothAdapter.enable();
        init();
    }

    @Override
    public void onCancel(EnableBluetoothDialog f) {
        finish();
    }

    @Override
    public void onDismiss(ErrorDialog f) {
        finish();
    }
}