/*
 * Copyright (c) 2011-2020 HERE Europe B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.here.android.example.search;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import com.here.android.mpa.common.GeoCoordinate;
import com.here.android.mpa.common.Image;
import com.here.android.mpa.common.OnEngineInitListener;
import com.here.android.mpa.mapping.Map;
import com.here.android.mpa.mapping.AndroidXMapFragment;
import com.here.android.mpa.mapping.MapMarker;
import com.here.android.mpa.mapping.MapObject;
import com.here.android.mpa.search.AroundRequest;
import com.here.android.mpa.search.Category;
import com.here.android.mpa.search.CategoryFilter;
import com.here.android.mpa.search.DiscoveryResult;
import com.here.android.mpa.search.DiscoveryResultPage;
import com.here.android.mpa.search.ErrorCode;
import com.here.android.mpa.search.ExploreRequest;
import com.here.android.mpa.search.GeocodeRequest;
import com.here.android.mpa.search.GeocodeResult;
import com.here.android.mpa.search.HereRequest;
import com.here.android.mpa.search.Location;
import com.here.android.mpa.search.PlaceLink;
import com.here.android.mpa.search.ResultListener;
import com.here.android.mpa.search.ReverseGeocodeRequest;
import com.here.android.mpa.search.SearchRequest;

import androidx.appcompat.app.AppCompatActivity;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.util.Log;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import static android.content.Context.LAYOUT_INFLATER_SERVICE;

/**
 * This class encapsulates the properties and functionality of the Map view.It also implements 4
 * types of discovery requests that HERE Android SDK provides as example.
 */
public class MapFragmentView {
    public static List<DiscoveryResult> s_ResultList;
    private AndroidXMapFragment m_mapFragment;
    private AppCompatActivity m_activity;
    private Map m_map;
    private Button m_placeDetailButton;
    private List<MapObject> m_mapObjectList = new ArrayList<>();
    private static final String TAG = MapFragmentView.class.getSimpleName();
    private LinearLayout m_placeDetailLayout;
    private TextView m_placeName;
    private TextView m_placeLocation;
 //   protected LocationManager locationManager;
    //private int mapMarkerCount = 0;

    public MapFragmentView(AppCompatActivity activity) {
        m_activity = activity;
        /*
         * The map fragment is not required for executing search requests. However in this example,
         * we will put some markers on the map to visualize the location of the search results.
         */

        initMapFragment();
        initSearchControlButtons();
        /* We use a list view to present the search results */
        initResultListButton();


    }

    private AndroidXMapFragment getMapFragment() {
        return (AndroidXMapFragment) m_activity.getSupportFragmentManager().findFragmentById(R.id.mapfragment);
    }

    private void initMapFragment() {
        /* Locate the mapFragment UI element */
        //info on getting current Geocordinate: https://javapapers.com/android/get-current-location-in-android/
        m_mapFragment = getMapFragment();
        loading();

        if (m_mapFragment != null) {
            /* Initialize the AndroidXMapFragment, results will be given via the called back. */
            m_mapFragment.init(new OnEngineInitListener() {
                @Override
                public void onEngineInitializationCompleted(OnEngineInitListener.Error error) {
                    ProgressBar pgsBar = (ProgressBar)m_activity.findViewById(R.id.pBar);
                    if (error == Error.NONE) {
                        m_map = m_mapFragment.getMap();
                        m_map.setCenter(new GeoCoordinate(52.5200, 13.4050),
                                Map.Animation.NONE);
                        m_map.setZoomLevel(10);
                        m_map.setProjectionMode(Map.Projection.MERCATOR);
                        m_mapFragment.setMapMarkerDragListener(new OnDragListenerHandler());
                       unloading();
                    } else {
                        new AlertDialog.Builder(m_activity).setMessage(
                                "Error : " + error.name() + "\n\n" + error.getDetails())
                                .setTitle(R.string.engine_init_error)
                                .setNegativeButton(android.R.string.cancel,
                                        new DialogInterface.OnClickListener() {
                                            @Override
                                            public void onClick(
                                                    DialogInterface dialog,
                                                    int which) {
                                                m_activity.finish();
                                            }
                                        }).create().show();
                    }
                }
            });
        }
    }


    private void initSearchControlButtons() {

        Button geocodeButton = (Button) m_activity.findViewById(R.id.geocodeRequestBtn);
        geocodeButton.setOnClickListener(new View.OnClickListener(){
            @Override
            public void onClick(View v) {

                triggerGeocodeRequest();
                loading();
                closeKeyboard();

            }

        });

    }

    private ResultListener<DiscoveryResultPage> discoveryResultPageListener = new ResultListener<DiscoveryResultPage>() {
        @Override
        public void onCompleted(DiscoveryResultPage discoveryResultPage, ErrorCode errorCode) {
            if (errorCode == ErrorCode.NONE) {
                /* No error returned,let's handle the results */
                m_placeDetailButton.setVisibility(View.VISIBLE);

                /*
                 * The result is a DiscoveryResultPage object which represents a paginated
                 * collection of items.The items can be either a PlaceLink or DiscoveryLink.The
                 * PlaceLink can be used to retrieve place details by firing another
                 * PlaceRequest,while the DiscoveryLink is designed to be used to fire another
                 * DiscoveryRequest to obtain more refined results.
                 */
                s_ResultList = discoveryResultPage.getItems();
                for (DiscoveryResult item : s_ResultList) {
                    /*
                     * Add a marker for each result of PlaceLink type.For best usability, map can be
                     * also adjusted to display all markers.This can be done by merging the bounding
                     * box of each result and then zoom the map to the merged one.
                     */
                    if (item.getResultType() == DiscoveryResult.ResultType.PLACE) {
                        PlaceLink placeLink = (PlaceLink) item;
                        addMarkerAtPlace(placeLink);
                    }
                }
            } else {
                Toast.makeText(m_activity,
                        "ERROR:Discovery search request returned return error code+ " + errorCode,
                        Toast.LENGTH_SHORT).show();
            }
        }
    };

    private void addMarkerAtPlace(PlaceLink placeLink) {
        Image img = new Image();
        try {
            img.setImageResource(R.drawable.marker);
        } catch (IOException e) {
            e.printStackTrace();
        }

        MapMarker mapMarker = new MapMarker();
        mapMarker.setIcon(img);
        mapMarker.setCoordinate(new GeoCoordinate(placeLink.getPosition()));
        m_map.addMapObject(mapMarker);
        m_mapObjectList.add(mapMarker);
    }

    private void cleanMap() {
        if (!m_mapObjectList.isEmpty()) {
            m_map.removeMapObjects(m_mapObjectList);
            m_mapObjectList.clear();
        }
        m_placeDetailButton.setVisibility(View.GONE);
    }
    private void triggerGeocodeRequest() {
        final int[] mapMarkerCount = {0};
        /*
         * Create a GeocodeRequest object with the desired query string, then set the search area by
         * providing a GeoCoordinate and radius before executing the request.
         */
        //start
        cleanMap();
        EditText editText = (EditText) m_activity.findViewById(R.id.addsearch);
        TextView mText = (TextView) m_activity.findViewById(R.id.textView1);
        // mText.setText("editText.getText().toString()");
        String query = editText.getText().toString();
        if(query.isEmpty()==true)
        {
            unloading();
            Toast.makeText(m_activity,"Address is empty",Toast.LENGTH_SHORT).show();
            return ;
        }

        GeocodeRequest geocodeRequest = new GeocodeRequest(query);
        GeoCoordinate coordinate = new GeoCoordinate(52.5200, 13.4050);
        geocodeRequest.setSearchArea(coordinate, 5000);
        geocodeRequest.execute(new ResultListener<List<GeocodeResult>>() {
            @Override
            public void onCompleted(List<GeocodeResult> results, ErrorCode errorCode) {
                if (errorCode == ErrorCode.NONE) {
                    m_activity.runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            // Do something on UiThread
                            unloading();
                        }
                    });

                    /*
                     * From the result object, we retrieve the location and its coordinate and
                     * display to the screen. Please refer to HERE Android SDK doc for other
                     * supported APIs.
                     */


                    for (GeocodeResult result : results) {

                        Image img = new Image();
                        try {
                            img.setImageResource(R.drawable.marker);
                        } catch (IOException e) {
                            e.printStackTrace();
                        }

                        //Checking Geocode Request Result
                        Log.d("Geocode", "Result: Geocode Request Result " + result.getLocation().getCoordinate().toString());
                        MapMarker mapMarker = new MapMarker();
                        mapMarker.setIcon(img);
                        mapMarker.setCoordinate(new GeoCoordinate(result.getLocation().getCoordinate()));
                        mapMarker.setDraggable(true);
                        mapMarker.setTitle("MapMarker id: " + mapMarkerCount[0]++);
                        m_map.addMapObject(mapMarker);
                        m_mapObjectList.add(mapMarker);
                        m_map.setCenter(new GeoCoordinate(result.getLocation().getCoordinate()),
                                Map.Animation.NONE);
                        m_map.setZoomLevel(10);


                    }
                    if (results.isEmpty()){
                        m_activity.runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                // Do something on UiThread
                                Toast.makeText(m_activity,"No place found",Toast.LENGTH_SHORT).show();
                                unloading();
                            }
                        });

                    }


                } else {
                    //remove
                    Toast.makeText(m_activity,"Error: Geocode Request returned error code",Toast.LENGTH_SHORT).show();
                    Log.d("Error", "Error: Geocode Request returned error code");
                    unloading();

                }
            }
        });

    }
    private void triggerRevGeocodeRequest(GeoCoordinate coordinate) {

        ReverseGeocodeRequest revGeocodeRequest = new ReverseGeocodeRequest(coordinate);
        revGeocodeRequest.execute(new ResultListener<Location>() {
            @Override
            public void onCompleted(Location location, ErrorCode errorCode) {
                if (errorCode == ErrorCode.NONE) {
                    /*
                     * From the location object, we retrieve the address and display to the screen.
                     * Please refer to HERE Android SDK doc for other supported APIs.
                     */
                    Log.d("success", "location: Geocode Request returned error code " + location.getAddress().toString());

                    LayoutInflater inflater = (LayoutInflater)
                            m_activity.getSystemService(LAYOUT_INFLATER_SERVICE);
                    View popupView = inflater.inflate(R.layout.result_list, null);
                    int width = LinearLayout.LayoutParams.WRAP_CONTENT;
                    int height = LinearLayout.LayoutParams.WRAP_CONTENT;
                    boolean focusable = true; // lets taps outside the popup also dismiss it
                    final PopupWindow popupWindow = new PopupWindow(popupView, width, height, focusable);
                    popupWindow.showAtLocation(m_activity.findViewById(R.id.pBar), Gravity.CENTER, 0, 0);
                    TextView mText = (TextView) popupView.findViewById(R.id.placeName);
                    mText.setText(location.getAddress().toString());
                    // dismiss the popup window when touched
                    popupView.setOnTouchListener(new View.OnTouchListener() {
                        @Override
                        public boolean onTouch(View v, MotionEvent event) {
                            popupWindow.dismiss();
                            return true;
                        }
                    });
                    Button closePlaceDetailButton = (Button) popupView.findViewById(R.id.closeLayoutButton);
                    closePlaceDetailButton.setOnClickListener(new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                                popupWindow.dismiss();

                        }
                    });

                } else {
                    Toast.makeText(m_activity,"Text!",Toast.LENGTH_SHORT).show();
                    Log.d("Error", "Error: Geocode Request returned error code ");
                }
            }
        });

    }

    private class OnDragListenerHandler implements MapMarker.OnDragListener {
        @Override
        public void onMarkerDrag(MapMarker mapMarker) {
            Log.i(TAG, "onMarkerDrag: " + mapMarker.getTitle() + " -> " +mapMarker
                    .getCoordinate());
        }

        @Override
        public void onMarkerDragEnd(MapMarker mapMarker) {
            Log.i(TAG, "onMarkerDragEnd: " + mapMarker.getTitle() + " -> " +mapMarker
                    .getCoordinate());
            triggerRevGeocodeRequest(mapMarker
                    .getCoordinate());

        }

        @Override
        public void onMarkerDragStart(MapMarker mapMarker) {
            Log.i(TAG, "onMarkerDragStart: " + mapMarker.getTitle() + " -> " +mapMarker
                    .getCoordinate());
        }
    }

    private void closeKeyboard()
    {
        // this will give us the view
        // which is currently focus
        // in this layout
        View view = (View) m_activity.findViewById(R.id.geocodeRequestBtn);

        // if nothing is currently
        // focus then this will protect
        // the app from crash
        if (view != null) {

            // now assign the system
            // service to InputMethodManager
            InputMethodManager manager
                    = (InputMethodManager)
                    m_activity.getSystemService(
                            Context.INPUT_METHOD_SERVICE);
            manager
                    .hideSoftInputFromWindow(
                            view.getWindowToken(), 0);
        }
    }

    private void initResultListButton() {
        m_placeDetailButton = (Button) m_activity.findViewById(R.id.resultListBtn);
        m_placeDetailButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                /* Open the ResultListActivity */
                Intent intent = new Intent(m_activity, ResultListActivity.class);
                m_activity.startActivity(intent);
            }
        });
    }

    private void loading(){
        ProgressBar pgsBar = (ProgressBar)m_activity.findViewById(R.id.pBar);
        pgsBar.setVisibility(View.VISIBLE);
    }

    private void unloading(){
        ProgressBar pgsBar = (ProgressBar)m_activity.findViewById(R.id.pBar);
        pgsBar.setVisibility(View.GONE);
    }

}


