package info.geopost.geopost;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.drawable.Drawable;
import android.location.Location;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.app.ActionBar;
import android.support.v7.app.ActionBarActivity;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TabHost;
import android.widget.TextView;
import android.widget.Toast;

import com.afollestad.materialdialogs.MaterialDialog;
import com.gc.materialdesign.views.ButtonFloatSmall;
import com.getbase.floatingactionbutton.FloatingActionButton;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.CameraPosition;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.parse.FindCallback;
import com.parse.LogOutCallback;
import com.parse.ParseException;
import com.parse.ParseGeoPoint;
import com.parse.ParseObject;
import com.parse.ParseQuery;
import com.parse.ParseUser;
import com.rey.material.widget.Button;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class MapsActivity extends ActionBarActivity
            implements NavigationDrawerFragment.NavigationDrawerCallbacks {

    // Used to pass location from MainActivity to PostActivity
    public static final String INTENT_EXTRA_LOCATION = "location";
    private static final int MAX_POST_SEARCH_DISTANCE = 100;
    private static final int MAX_POST_SEARCH_RESULTS = 50;
    private static final Double DISTANCE_BEFORE_PARSE_UPDATE = 0.5;
    private static final String PREF_CURRENT_LAT = "mCurrentLat";
    private static final String PREF_CURRENT_LON = "mCurrentLon";
    private static final String PREF_LAST_LAT = "mLastLat";
    private static final String PREF_LAST_LON = "mLastLon";
    // Conversion from feet to meters
    private static final float METERS_PER_FEET = 0.3048f;

    // Conversion from kilometers to meters
    private static final int METERS_PER_KILOMETER = 1000;
    private static final float DEFAULT_SEARCH_DISTANCE = METERS_PER_KILOMETER * 11;
    private static final long PARSE_QUERY_TIMEOUT = 30000;

    private GoogleMap mMap;
    private static final String TAG = MapsActivity.class.getSimpleName();
    private FloatingActionButton mPostButton;
    private LatLng mCurrentLocation = new LatLng(0.0, 0.0);
    private LatLng mLastLocation = new LatLng(0.0, 0.0);
//    private HashMap<String, Marker> mMapMarkers = new HashMap<>();
    private HashMap<String, GeoPostMarker> mGeoPostMarkers = new HashMap<>();
    private String mSelectedPostObjectId;
    private long mLastParseQueryTime;
    private LatLng mLastParseQueryLocation;
    // Fields for the map radius in feet
    private float mRadius = DEFAULT_SEARCH_DISTANCE;

    //hold on to the list returned by a Parse Query
    private List<GeoPostObj> geoPostObjList;

    private ListViewForGeoPost geoList;

    //navigation drawer
    private NavigationDrawerFragment mNavigationDrawerFragment;

    private CharSequence mTitle;

    // Access basic application info
    private SharedPreferences mPrefs;

    // Set map to current user location on first location event.
    private boolean zoomToUserLocation = true;

    //dialog buttons
    com.rey.material.widget.FloatingActionButton mUpvoteButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_maps);

        mNavigationDrawerFragment = (NavigationDrawerFragment)getSupportFragmentManager().findFragmentById(R.id.navigation_drawer);
        mTitle = getTitle();

        mNavigationDrawerFragment.setUp(R.id.navigation_drawer, (DrawerLayout)findViewById(R.id.drawer_layout));

        TabHost tabHost = (TabHost) findViewById(R.id.tabHost);
        tabHost.setup();

        TabHost.TabSpec tabSpec = tabHost.newTabSpec("map");
        tabSpec.setContent(R.id.tabMap);
        tabSpec.setIndicator("Map");
        tabHost.addTab(tabSpec);

        tabSpec = tabHost.newTabSpec("table");
        tabSpec.setContent(R.id.tabTable);
        tabSpec.setIndicator("Table");
        tabHost.addTab(tabSpec);

        tabSpec = tabHost.newTabSpec("profile");
        tabSpec.setContent(R.id.tabProfile);
        tabSpec.setIndicator("Profile");
        tabHost.addTab(tabSpec);

        setupParse();
        Log.e(TAG, "Current user is: " + ParseUser.getCurrentUser().getUsername());
        mPostButton = (FloatingActionButton) findViewById(R.id.map_post_button);
        mPostButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                // 2
                LatLng myLoc = (mCurrentLocation == null) ? mLastLocation : mCurrentLocation;
                if (myLoc == null) {
                    Toast.makeText(MapsActivity.this,
                            "Please try again after your location appears on the map.", Toast.LENGTH_LONG).show();
                    return;
                }
                // 3
                Intent intent = new Intent(MapsActivity.this, PostActivity.class);
                intent.putExtra(MapsActivity.INTENT_EXTRA_LOCATION, myLoc);
                startActivity(intent);
            }
        });

        mPrefs = getSharedPreferences("GeoNote_prefs", MODE_PRIVATE);

        if (savedInstanceState == null) {
            double currentLat = mPrefs.getFloat(PREF_CURRENT_LAT, 0);
            double currentLon = mPrefs.getFloat(PREF_CURRENT_LON, 0);
            double lastLat = mPrefs.getFloat(PREF_LAST_LAT, 0);
            double lastLon = mPrefs.getFloat(PREF_LAST_LON, 0);
            mCurrentLocation = new LatLng(currentLat, currentLon);
            mLastLocation = new LatLng(lastLat, lastLon);
            Log.d(TAG, "onCreate getting saved prefs:\n" +
                    "curLat: " + mCurrentLocation.latitude +
                    " curLon: " + mCurrentLocation.longitude +
                    " lastLat: " + mLastLocation.latitude +
                    " lastLon: " + mLastLocation.longitude);
        }

        setUpMapIfNeeded();

//        Testing modal
        boolean wrapInScrollView = true;
        MaterialDialog m = new MaterialDialog.Builder(this)
                .customView(R.layout.activity_modal, wrapInScrollView)
                .show();
        mUpvoteButton = (com.rey.material.widget.FloatingActionButton) m.findViewById(R.id.upvote_button);
        mUpvoteButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {

                mUpvoteButton.setBackgroundColor(getResources().getColor(R.color.green));
            }
        });

    }

    @Override
    public void onBackPressed() {
        finish();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putDouble(PREF_CURRENT_LAT, mCurrentLocation.latitude);
        outState.putDouble(PREF_CURRENT_LON, mCurrentLocation.longitude);
        outState.putDouble(PREF_LAST_LAT, mLastLocation.latitude);
        outState.putDouble(PREF_LAST_LON, mLastLocation.longitude);
        Log.d(TAG, "Saving instance state:\n" +
                "curLat: " + mCurrentLocation.latitude +
                " curLon: " + mCurrentLocation.longitude +
                " lastLat: " + mLastLocation.latitude +
                " lastLon: " + mLastLocation.longitude);
    }

    @Override
    protected void onRestoreInstanceState(Bundle outState) {
        super.onRestoreInstanceState(outState);
        double currentLat = outState.getDouble(PREF_CURRENT_LAT, 0.0);
        double currentLon = outState.getDouble(PREF_CURRENT_LON, 0.0);
        double lastLat = outState.getDouble(PREF_LAST_LAT, 0.0);
        double lastLon = outState.getDouble(PREF_LAST_LON, 0.0);

        mCurrentLocation = new LatLng(currentLat, currentLon);
        mLastLocation = new LatLng(lastLat, lastLon);
        Log.d(TAG, "Restoring instance state:\n" +
                "curLat: " + mCurrentLocation.latitude +
                " curLon: " + mCurrentLocation.longitude +
                " lastLat: " + mLastLocation.latitude +
                " lastLon: " + mLastLocation.longitude);

    }

    private void setupParse() {
          ParseObject.registerSubclass(GeoPostObj.class);
    }

    @Override
    protected void onResume() {
        super.onResume();
        double currentLat = mPrefs.getFloat(PREF_CURRENT_LAT, 0);
        double currentLon = mPrefs.getFloat(PREF_CURRENT_LON, 0);
        double lastLat = mPrefs.getFloat(PREF_LAST_LAT, 0);
        double lastLon = mPrefs.getFloat(PREF_LAST_LON, 0);
        mCurrentLocation = new LatLng(currentLat, currentLon);
        mLastLocation = new LatLng(lastLat, lastLon);
        setUpMapIfNeeded();
        doMapQuery();
        Log.d(TAG, "onResume restoring saved prefs:\n" +
                "curLat: " + mCurrentLocation.latitude +
                " curLon: " + mCurrentLocation.longitude +
                " lastLat: " + mLastLocation.latitude +
                " lastLon: " + mLastLocation.longitude);

    }

    @Override
    protected void onStop() {
        super.onStop();
        SharedPreferences.Editor ed = mPrefs.edit();
        ed.putFloat(PREF_CURRENT_LAT, (float) mCurrentLocation.latitude);
        ed.putFloat(PREF_CURRENT_LON, (float) mCurrentLocation.longitude);
        ed.putFloat(PREF_LAST_LAT, (float) mLastLocation.latitude);
        ed.putFloat(PREF_LAST_LON, (float) mLastLocation.longitude);
        ed.apply();

        Log.d(TAG, "onStop saving prefs:\n" +
                "curLat: " + mCurrentLocation.latitude +
                " curLon: " + mCurrentLocation.longitude +
                " lastLat: " + mLastLocation.latitude +
                " lastLon: " + mLastLocation.longitude);
    }

    /**
     * Sets up the map if it is possible to do so (i.e., the Google Play services APK is correctly
     * installed) and the map has not already been instantiated.. This will ensure that we only ever
     * call {@link #setUpMap()} once when {@link #mMap} is not null.
     * <p/>
     * If it isn't installed {@link SupportMapFragment} (and
     * {@link com.google.android.gms.maps.MapView MapView}) will show a prompt for the user to
     * install/update the Google Play services APK on their device.
     * <p/>
     * A user can return to this FragmentActivity after following the prompt and correctly
     * installing/updating/enabling the Google Play services. Since the FragmentActivity may not
     * have been completely destroyed during this process (it is likely that it would only be
     * stopped or paused), {@link #onCreate(Bundle)} may not be called again so we should call this
     * method in {@link #onResume()} to guarantee that it will be called.
     */
    private void setUpMapIfNeeded() {
        // Do a null check to confirm that we have not already instantiated the map.
        if (mMap == null) {
            // Try to obtain the map from the SupportMapFragment.
            mMap = ((SupportMapFragment) getSupportFragmentManager().findFragmentById(R.id.map))
                    .getMap();
            // Check if we were successful in obtaining the map.
            if (mMap != null) {
                setUpMap();
            }

        }
    }

    /**
     * This is where we can add markers or lines, add listeners or move the camera. In this case, we
     * just add a marker near Africa.
     * <p/>
     * This should only be called once and when we are sure that {@link #mMap} is not null.
     */
    private void setUpMap() {
        mMap.setMyLocationEnabled(true);
        mMap.setOnMyLocationChangeListener(myLocationChangeListener);
        // Move map camera to saved location if one exists.
        if(mCurrentLocation.latitude != 0.0 && mCurrentLocation.longitude != 0.0) {
            CameraPosition pos = new CameraPosition(mCurrentLocation, 16.0f, 0f, 0f);
            mMap.moveCamera(CameraUpdateFactory.newCameraPosition(pos));
            Log.d(TAG, "setUpMap restoring camera position to:\n" +
                    "curLat: " + mCurrentLocation.latitude +
                    " curLon: " + mCurrentLocation.longitude);
        }
        geoList = new ListViewForGeoPost();
        mMap.setOnCameraChangeListener(new GoogleMap.OnCameraChangeListener() {
            public void onCameraChange(CameraPosition position) {
                // TODO possibly get new markers when moving map?
                //doMapQuery();
            }
        });
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        super.onCreateOptionsMenu(menu);
//        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    private void doMapQuery() {
        // 1
        LatLng myLoc = (mCurrentLocation == null) ? mLastLocation : mCurrentLocation;
        if (myLoc == null) {
            cleanUpMarkers(new HashSet<String>());
            return;
        }
        // 2
        Log.d(TAG, "doMapQuery called.");
        final ParseGeoPoint myPoint = geoPointFromLocation(myLoc);
        // 3
        ParseQuery<GeoPostObj> mapQuery = GeoPostObj.getQuery();
        // 4
        mapQuery.whereWithinKilometers("location", myPoint, MAX_POST_SEARCH_DISTANCE);
        // 5
        mapQuery.include("user");
        mapQuery.orderByDescending("createdAt");
        mapQuery.setLimit(MAX_POST_SEARCH_RESULTS);
        // 6
        mapQuery.findInBackground(new FindCallback<GeoPostObj>() {
            @Override
            public void done(List<GeoPostObj> objects, ParseException e) {
                geoPostObjList = objects;
                mLastParseQueryTime = System.currentTimeMillis();
                mLastParseQueryLocation = mCurrentLocation;
                Set<String> toKeep = new HashSet<>();
                if (objects != null)
                    Log.d(TAG, "doMapQuery finished: " + objects.size() + " GeoPost items retrieved.");
                for (GeoPostObj post : objects) {

                    toKeep.add(post.getObjectId());
                    GeoPostMarker oldMarker = mGeoPostMarkers.get(post.getObjectId());
                    LatLng loc = latLngFromParseGeoPoint(post.getLocation());

                    if(oldMarker == null) {
                        GeoPostMarker newMarker;
                        if(getDistanceInMeters(loc, mCurrentLocation) <= mRadius) {
                            newMarker = new GeoPostMarker(post, newEnabledMarker(post),  true);
                        } else {
                            newMarker = new GeoPostMarker(post, newDisabledMarker(post),  false);
                        }
                        mGeoPostMarkers.put(post.getObjectId(), newMarker);
                    }
                }

                // We call the cleanUpMarkers() method and pass in the toKeep variable to remove any unwanted markers from the map.
                cleanUpMarkers(toKeep);
            }
        });
    }

    private ParseGeoPoint geoPointFromLocation(LatLng loc) {
        return new ParseGeoPoint(loc.latitude, loc.longitude);
    }

    private LatLng latLngFromParseGeoPoint(ParseGeoPoint point) {
        return new LatLng(point.getLatitude(), point.getLongitude());
    }

    private void cleanUpMarkers(Set<String> markersToKeep) {
        for (String objId : new HashSet<>(mGeoPostMarkers.keySet())) {
            if (!markersToKeep.contains(objId)) {
                mGeoPostMarkers.get(objId).marker.remove();
                mGeoPostMarkers.remove(objId);
            }
        }
    }

    private GoogleMap.OnMyLocationChangeListener myLocationChangeListener = new GoogleMap.OnMyLocationChangeListener() {
        @Override
        public void onMyLocationChange(Location location) {
            if(mCurrentLocation != null) {
                mLastLocation = mCurrentLocation;
            }

            mCurrentLocation = new LatLng(location.getLatitude(), location.getLongitude());

            // Set camera location if this is first location event received (map has just been opened)
            if(mMap != null && mCurrentLocation != null && zoomToUserLocation){
                mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(mCurrentLocation, 16.0f));
                zoomToUserLocation = false;
            }
            Log.d(TAG, "OnLocationChanged event - Lat: " + mCurrentLocation.latitude  +"Lon: " + mCurrentLocation.longitude);

            // disable markers now out of range, enable markers in range.
            recalculateUserMarkerDistances();
            // Perform mapQuery if current vs last location within certain distance interval.
            if(mLastParseQueryLocation != null) {
                ParseGeoPoint lastLoc = new ParseGeoPoint(mLastParseQueryLocation.latitude, mLastParseQueryLocation.longitude);
                ParseGeoPoint curLoc = new ParseGeoPoint(mCurrentLocation.latitude, mCurrentLocation.longitude);
                if(curLoc.distanceInKilometersTo(lastLoc) > DISTANCE_BEFORE_PARSE_UPDATE || ((System.currentTimeMillis() - mLastParseQueryTime) > PARSE_QUERY_TIMEOUT) ) {
                    doMapQuery();
                }
            }
        }
    };

    private void recalculateUserMarkerDistances(){
        Log.d(TAG, "Recalculating user markers.");
        for(Map.Entry entry : mGeoPostMarkers.entrySet()) {
            GeoPostMarker geoPostMarker = (GeoPostMarker) entry.getValue();
            if(getDistanceInMeters(geoPostMarker.marker.getPosition(), mCurrentLocation) <= mRadius) {
                enableMarker(geoPostMarker);
            } else {
                disableMarker(geoPostMarker);
            }
        }
    }

    private void enableMarker(GeoPostMarker geoPostMarker) {
        if(!geoPostMarker.enabled) {
            Log.d(TAG, "Enabling marker. post_id: " + geoPostMarker.geoPostObj.getObjectId());
            geoPostMarker.marker.remove();
            geoPostMarker.marker = newEnabledMarker(geoPostMarker.geoPostObj);
            geoPostMarker.enabled = true;
        }
    }

//    private Marker newEnabledMarker(String postId) {
//        //TODO: REDO TITLE AND SNIPPET LOGIC.
//        GeoPostMarker geoPostMarker = mGeoPostMarkers.get(postId);
//        LatLng loc = geoPostMarker.marker.getPosition();
//        MarkerOptions markerOpts =
//                new MarkerOptions().position(loc);
//        markerOpts =
//                markerOpts.title(geoPostMarker.mGeoPostObj.getText())
//                        .snippet(geoPostMarker.mGeoPostObj.getUser().getUsername())
//                        .icon(BitmapDescriptorFactory.defaultMarker(
//                                BitmapDescriptorFactory.HUE_GREEN));
//        return
//    }

    private Marker newEnabledMarker(GeoPostObj post) {
        //TODO: REDO TITLE AND SNIPPET LOGIC.
        LatLng loc = latLngFromParseGeoPoint(post.getLocation());
        MarkerOptions markerOpts =
                new MarkerOptions().position(loc);
        markerOpts =
                markerOpts.title(post.getTitle())
                        .snippet(post.getUser().getUsername())
                        .icon(BitmapDescriptorFactory.defaultMarker(
                                BitmapDescriptorFactory.HUE_GREEN));
        return mMap.addMarker(markerOpts);
    }

    private void disableMarker(GeoPostMarker geoPostMarker) {
        if(geoPostMarker.enabled) {
            Log.d(TAG, "Disabling marker post_id: " + geoPostMarker.geoPostObj.getObjectId());
            LatLng loc = geoPostMarker.marker.getPosition();
            geoPostMarker.marker.remove();
            geoPostMarker.marker = newDisabledMarker(loc);
        }
    }

    private Marker newDisabledMarker(LatLng loc) {
        MarkerOptions markerOpts =
                new MarkerOptions().position(loc);
        markerOpts =
                markerOpts.title(getResources().getString(R.string.post_out_of_range))
                        .icon(BitmapDescriptorFactory.defaultMarker(
                                BitmapDescriptorFactory.HUE_RED));
        return mMap.addMarker(markerOpts);
    }

    private Marker newDisabledMarker(GeoPostObj post) {
        return newDisabledMarker(latLngFromParseGeoPoint(post.getLocation()));
    }

    private float getDistanceInMeters(LatLng loc1, LatLng loc2) {
        float[] results = new float[1];
        Location.distanceBetween(
                loc1.latitude,
                loc1.longitude,
                loc2.latitude,
                loc2.longitude,
                results);
        return results[0];
    }



    //Called when item selected.
    @Override
    public void onNavigationDrawerItemSelected(View v, int position, long id) {
        FragmentManager fragmentManager = getSupportFragmentManager();
        Log.e(TAG, "Position is: " + position);
        switch(position){
            case 0:
//                TextView v = (TextView) mNavigationDrawerFragment.mDrawerListView.getChildAt(position);
                TextView tv = (TextView) v;
                tv.setText("Username: " + ParseUser.getCurrentUser().getUsername());
                Toast.makeText(getApplicationContext(),"Username: " + ParseUser.getCurrentUser().getUsername(), Toast.LENGTH_SHORT).show();
                break;
            case 1:
                logoutParseUser();
                break;
        }
        fragmentManager.beginTransaction()
                .replace(R.id.container, PlaceholderFragment.newInstance(position + 1))
                .commit();
    }

    private void logoutParseUser(){
        ParseUser.logOutInBackground(new LogOutCallback() {
            @Override
            public void done(ParseException e) {
                if (e == null){
                    Toast.makeText(getApplicationContext(), "Logged out", Toast.LENGTH_SHORT).show();
                    startActivity(new Intent(MapsActivity.this , DispatchActivity.class));
                    finish();
            } //TODO Add error checking.
            }
        });
    }

    /**
     * A placeholder fragment containing a simple view.
     */
    public static class PlaceholderFragment extends Fragment {
        /**
         * The fragment argument representing the section number for this
         * fragment.
         */
        private static final String ARG_SECTION_NUMBER = "section_number";

        /**
         * Returns a new instance of this fragment for the given section
         * number.
         */
        public static PlaceholderFragment newInstance(int sectionNumber) {
            PlaceholderFragment fragment = new PlaceholderFragment();
            Bundle args = new Bundle();
            args.putInt(ARG_SECTION_NUMBER, sectionNumber);
            fragment.setArguments(args);
            return fragment;
        }

        public PlaceholderFragment() {
        }

        @Override
        public View onCreateView(LayoutInflater inflater, ViewGroup container,
                                 Bundle savedInstanceState) {
            View rootView = inflater.inflate(R.layout.fragment_main, container, false);
            return rootView;
        }

        @Override
        public void onAttach(Activity activity) {
            super.onAttach(activity);
            ((MapsActivity) activity).onSectionAttached(
                    getArguments().getInt(ARG_SECTION_NUMBER));
        }
    }

    public void restoreActionBar() {
        ActionBar actionBar = getSupportActionBar();
        actionBar.setNavigationMode(ActionBar.NAVIGATION_MODE_STANDARD);
        actionBar.setDisplayShowTitleEnabled(true);
        actionBar.setTitle(mTitle);
    }

    private void onSectionAttached(int number) {
        switch (number) {
            case 1:
                mTitle = getString(R.string.title_section1);
                break;
            case 2:
                mTitle = getString(R.string.title_section2);
                break;
        }
    }


    public class ListViewForGeoPost extends Activity{

        @Override
        protected void onCreate(Bundle savedInstanceState){
            super.onCreate(savedInstanceState);
            setContentView(R.layout.activity_maps);

            final ListView listView = (ListView) findViewById(R.id.listView);
            final ArrayList<GeoPostObj> list = (ArrayList<GeoPostObj>) geoPostObjList;
            String[] values = {};
            final GeoPostArrayAdapter adapter = new GeoPostArrayAdapter(this, values);
            listView.setAdapter(adapter);
//            LayoutInflater inflater = (LayoutInflater) this.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
//            inflater.inflate(R.id.listView, null, false);
//            listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
//                @Override
//                public void onItemClick(AdapterView<?> parent, final View view, int position, long id) {
//                    final String item = (String)parent.getItemAtPosition(position);
//                    view.animate().setDuration(2000).alpha(0).withEndAction(new Runnable() {
//                        @Override
//                        public void run() {
//                            list.remove(item);
//                            adapter.notifyDataSetChanged();
//                            view.setAlpha(1);
//                        }
//                    });
//                }
//            });
        }



    }

    public class GeoPostArrayAdapter extends ArrayAdapter<String>{
        private final Context context;
        private final String[] values;

        public GeoPostArrayAdapter(Context context, String[] values){
            super(context, R.layout.row_fragment, values);
            this.context = context;
            this.values = values;
        }

        public View getView(int position, View convertView, ViewGroup parent){
            LayoutInflater inflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
            View rowView = inflater.inflate(R.layout.row_fragment, parent, false);
            TextView title = (TextView) rowView.findViewById(R.id.row_title);
            TextView description = (TextView) rowView.findViewById(R.id.row_description);
            GeoPostObj tmp = geoPostObjList.get(position);
            title.setText(tmp.getTitle());
            description.setText(tmp.getText());
            return rowView;
        }



    }


}
