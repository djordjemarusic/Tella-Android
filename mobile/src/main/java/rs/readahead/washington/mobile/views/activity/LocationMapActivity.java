package rs.readahead.washington.mobile.views.activity;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.location.Location;
import android.os.Bundle;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.widget.Toolbar;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.CameraPosition;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;

import butterknife.BindView;
import butterknife.ButterKnife;
import rs.readahead.washington.mobile.R;
import rs.readahead.washington.mobile.domain.entity.MyLocation;
import rs.readahead.washington.mobile.mvp.contract.ILocationGettingPresenterContract;
import rs.readahead.washington.mobile.mvp.presenter.LocationGettingPresenter;


public class LocationMapActivity extends BaseLockActivity implements
        OnMapReadyCallback, GoogleMap.OnMapLongClickListener, GoogleMap.OnCameraMoveStartedListener,
        ILocationGettingPresenterContract.IView {
    public static final String SELECTED_LOCATION = "sl";
    public static final String CURRENT_LOCATION_ONLY = "ro";

    private GoogleMap mMap;

    @BindView(R.id.toolbar)
    Toolbar toolbar;
    @BindView(R.id.progressBar)
    ProgressBar progressBar;
    @BindView(R.id.info)
    TextView hint;

    @Nullable
    private MyLocation myLocation;
    private Marker selectedMarker;
    private boolean virginMap = true;
    private LocationGettingPresenter locationGettingPresenter;
    private boolean readOnly;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_location_map);
        ButterKnife.bind(this);

        myLocation = (MyLocation) getIntent().getSerializableExtra(SELECTED_LOCATION);
        readOnly = getIntent().getBooleanExtra(CURRENT_LOCATION_ONLY, true);
        locationGettingPresenter = new LocationGettingPresenter(this, readOnly);

        setSupportActionBar(toolbar);
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setDisplayHomeAsUpEnabled(true);
            actionBar.setTitle(R.string.collect_form_geopoint_app_bar);
            actionBar.setHomeAsUpIndicator(R.drawable.ic_close_white);
        }

        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.map);
        mapFragment.getMapAsync(this);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.location_map_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();

        if (id == android.R.id.home) {
            myLocation = null;
            setResultAndFinish();
            return true;
        }

        if (id == R.id.menu_item_select) {
            setResultAndFinish();
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        locationGettingPresenter.destroy();
    }

    @Override
    public void onMapReady(GoogleMap googleMap) {
        mMap = googleMap;
        mMap.setOnMapLongClickListener(this);

        initMapLocationAndCamera();
    }

    @Override
    public void onMapLongClick(LatLng latLng) {
        if (readOnly) {
            return;
        }

        myLocation = new MyLocation();
        myLocation.setLatitude(latLng.latitude);
        myLocation.setLongitude(latLng.longitude);
        showMyLocation(myLocation);
    }

    @Override
    public void onCameraMoveStarted(int i) {
        virginMap = false;
    }

    @Override
    public void onGettingLocationStart() {
        mMap.getUiSettings().setScrollGesturesEnabled(false);
        mMap.getUiSettings().setZoomGesturesEnabled(false);
        progressBar.setVisibility(View.VISIBLE);
    }

    @Override
    public void onGettingLocationEnd() {
        mMap.getUiSettings().setScrollGesturesEnabled(true);
        mMap.getUiSettings().setZoomGesturesEnabled(true);
        progressBar.setVisibility(View.GONE);
    }

    @Override
    public void onLocationSuccess(Location location) {
        if (location != null && virginMap) {
            virginMap = false;
            myLocation = MyLocation.fromLocation(location);
            showMyLocation(myLocation);
        }
    }

    @Override
    public void onNoLocationPermissions() {
    }

    @Override
    public void onGPSProviderDisabled() {
    }

    @Override
    public Context getContext() {
        return this;
    }

    private void showMyLocation(@NonNull MyLocation myLocation) {
        LatLng latLng = new LatLng(myLocation.getLatitude(), myLocation.getLongitude());

        if (selectedMarker == null) {
            selectedMarker = mMap.addMarker(new MarkerOptions().position(latLng).title(getString(R.string.collect_form_geopoint_marker_content_desc)));
        } else {
            selectedMarker.setPosition(latLng);
        }

        selectedMarker.setDraggable(!readOnly);

        float currentZoom = mMap.getCameraPosition().zoom;

        CameraPosition cameraPosition = new CameraPosition.Builder()
                .target(latLng)
                .bearing(0)
                .tilt(0)
                .zoom(Math.max(15f, currentZoom))
                .build();

        mMap.animateCamera(CameraUpdateFactory.newCameraPosition(cameraPosition));
    }

    private void initMapLocationAndCamera() {
        if (!readOnly) {
            hint.setVisibility(View.VISIBLE);
        }

        if (myLocation == null || readOnly) {
            locationGettingPresenter.startGettingLocation(!readOnly);
        }

        if (myLocation != null) {
            showMyLocation(myLocation);
        }
    }

    private void setResultAndFinish() {
        setResult(Activity.RESULT_OK, new Intent().putExtra(SELECTED_LOCATION, myLocation));
        finish();
    }
}
