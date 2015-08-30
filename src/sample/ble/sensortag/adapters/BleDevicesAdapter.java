package sample.ble.sensortag.adapters;

import android.bluetooth.BluetoothDevice;
import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;

import sample.ble.sensortag.R;

/** Adapter for holding devices found through scanning.
 *  Created by steven on 9/5/13.
 */
public class BleDevicesAdapter extends BaseAdapter {
	private static int buffer_number = 10;
    private final LayoutInflater inflater;

    private final ArrayList<BluetoothDevice> leDevices;
    private final HashMap<BluetoothDevice, Integer> rssiMap = new HashMap<BluetoothDevice, Integer>();
    private final HashMap<String, LinkedList<Integer>> rssiHistoryMap = 
    			new HashMap<String, LinkedList<Integer>>();
    
    public BleDevicesAdapter(Context context) {
        leDevices = new ArrayList<BluetoothDevice>();
        inflater = LayoutInflater.from(context);
    }

    public void addDevice(BluetoothDevice device, int rssi) {
        if (!leDevices.contains(device)) {
            leDevices.add(device);
        }
        rssiMap.put(device, rssi);
        if (device != null && device.getAddress() != null && 
        	!rssiHistoryMap.containsKey( device.getAddress() ) )
        {
        	rssiHistoryMap.put(device.getAddress(), new LinkedList<Integer>() );
        }
    }

    public BluetoothDevice getDevice(int position) {
        return leDevices.get(position);
    }

    public void clear() {
        leDevices.clear();
    }

    @Override
    public int getCount() {
        return leDevices.size();
    }

    @Override
    public Object getItem(int i) {
        return leDevices.get(i);
    }

    @Override
    public long getItemId(int i) {
        return i;
    }

    @Override
    public View getView(int i, View view, ViewGroup viewGroup) {
        ViewHolder viewHolder;
        // General ListView optimization code.
        if (view == null) {
            view = inflater.inflate(R.layout.li_device, null);
            viewHolder = new ViewHolder();
            viewHolder.deviceAddress = (TextView) view.findViewById(R.id.device_address);
            viewHolder.deviceName = (TextView) view.findViewById(R.id.device_name);
            viewHolder.deviceRssi = (TextView) view.findViewById(R.id.device_rssi);
            view.setTag(viewHolder);
        } else {
            viewHolder = (ViewHolder) view.getTag();
        }

        BluetoothDevice device = leDevices.get(i);
        final String deviceName = device.getName();
        if (deviceName != null && deviceName.length() > 0)
            viewHolder.deviceName.setText(deviceName);
        else
            viewHolder.deviceName.setText(R.string.unknown_device);
        viewHolder.deviceAddress.setText(device.getAddress());
        
        
        LinkedList<Integer> history;
        if ( !rssiHistoryMap.containsKey(device.getAddress()) )
        {
        	rssiHistoryMap.put(device.getAddress(), new LinkedList<Integer>());
        }
        
	    history = rssiHistoryMap.get( device.getAddress() );
        if (history.size() == buffer_number )
	    {
	     	history.remove();
	    }
	    history.add( rssiMap.get(device) );
	    double distance = rssiToDistance( history );
	    //viewHolder.deviceRssi.setText(""+Math.pow(10,-(rssiMap.get(device)+80.0)/17.0)) ;
	    
	    viewHolder.deviceRssi.setText(""+distance + " m");
	    //viewHolder.deviceRssi.setText(""+rssiMap.get(device)+" dBm");
        return view;
    }

    private double rssiToDistance( LinkedList<Integer> history )
    {
    	double average = 0.0;
    	int minimum = 100000000;
    	int maximum = -100000000;
    	for (Integer rssi : history )
    	{
    		if (rssi < minimum)
    			minimum = rssi;
    		if (rssi > maximum)
    			maximum = rssi;
    		average += rssi;
    	}
    	
    	if (history.size() > 2)
    	{
    		average = average - minimum - maximum;
    		average /= (history.size() - 2);
    	} else {
    		average /= history.size();
    	}
    	    	
    	double distance = 12*2.54/100.0*Math.pow(10,-(average+62.0)/23.0);
    	return distance;
    }
    
    private static class ViewHolder {
        TextView deviceName;
        TextView deviceAddress;
        TextView deviceRssi;
    }
}
