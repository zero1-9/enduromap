package com.example.enduromap;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.webkit.ConsoleMessage;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.widget.TextView;
import android.widget.Toast;

public class MainActivity extends Activity implements LocationListener {
    private WebView webView;
    private TextView speedText, gpsStatus, altText, odoText;
    private LocationManager locationManager;
    private boolean isMapLoaded = false;
    private boolean isFirstFix = true; 
    
    private NotificationManager notificationManager;
    private static final int NOTIF_ID = 101;
    private String lastNotifText = "";
    private StringBuilder debugLog = new StringBuilder();

    private final String MAP_HTML = 
        "<!DOCTYPE html><html><head>" +
        "<meta name='viewport' content='width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no' />" +
        "<link rel='stylesheet' href='https://cdn.jsdelivr.net/npm/leaflet@1.9.4/dist/leaflet.css' />" +
        "<script src='https://cdn.jsdelivr.net/npm/leaflet@1.9.4/dist/leaflet.js'></script>" +
        "<script src='https://cdn.jsdelivr.net/npm/localforage@1.10.0/dist/localforage.min.js'></script>" +
        "<style>" +
        "html, body { margin: 0; padding: 0; height: 100%; background: #121212; overflow: hidden; font-family: sans-serif; } " +
        "#map { width: 100%; height: 100%; position: absolute; top: 0; left: 0; z-index: 1; } " +
        "#draw-overlay { position: absolute; top: 0; left: 0; width: 100%; height: 100%; z-index: 450; display: none; touch-action: none; background: rgba(0,0,0,0.01); } " +
        ".leaflet-control-attribution { display: none !important; } " +
        ".leaflet-bar { border: none !important; box-shadow: none !important; } " +
        
        ".custom-btn { background: rgba(30, 30, 36, 0.85); color: #FFFFFF; width: 50px; height: 50px; border-radius: 25px; display: flex; align-items: center; justify-content: center; font-size: 22px; box-shadow: 0 4px 12px rgba(0,0,0,0.5); cursor: pointer; margin-bottom: 10px; border: 1px solid rgba(255,255,255,0.1); } " +
        ".custom-btn:active { transform: scale(0.92); background: rgba(0, 229, 255, 0.2); } " +
        ".btn-active { background: rgba(255, 87, 34, 0.9) !important; border-color: #FF5722 !important; box-shadow: 0 0 15px rgba(255,87,34,0.6); } " +
        ".btn-sos { background: rgba(255, 0, 0, 0.8) !important; font-size: 18px; font-weight: bold; } " +
        ".bike-icon-inner { font-size: 42px; filter: drop-shadow(0px 4px 6px rgba(0,0,0,0.8)); transition: transform 0.3s linear; } " +

        "#routes-panel { position: absolute; bottom: -100%; left: 0; width: 100%; height: 60%; background: #1E1E24; z-index: 2000; border-radius: 20px 20px 0 0; box-shadow: 0 -4px 20px rgba(0,0,0,0.5); transition: bottom 0.3s ease-in-out; display: flex; flex-direction: column; } " +
        "#routes-panel.open { bottom: 0; } " +
        ".panel-header { padding: 15px; font-size: 18px; font-weight: bold; color: #00E5FF; display: flex; justify-content: space-between; align-items: center; border-bottom: 1px solid #333; } " +
        ".close-btn { background: none; border: none; color: #FF5722; font-size: 24px; cursor: pointer; } " +
        "#routes-list { flex: 1; overflow-y: auto; padding: 10px; } " +
        ".route-item { background: #2A2A35; padding: 15px; border-radius: 10px; margin-bottom: 10px; display: flex; justify-content: space-between; align-items: center; } " +
        ".route-info { color: #FFF; font-size: 14px; } " +
        ".route-actions { display: flex; gap: 10px; } " +
        ".action-btn { background: #444; color: #FFF; border: none; padding: 8px 12px; border-radius: 8px; cursor: pointer; font-size: 16px; } " +
        ".action-btn.show { background: #00E5FF; color: #000; } " +
        ".action-btn.delete { background: #FF5722; } " +
        "</style>" +
        "</head><body>" +
        "<div id='map'><div id='draw-overlay'></div></div>" +
        
        "<div id='routes-panel'>" +
        "   <div class='panel-header'>Мои маршруты <button class='close-btn' onclick='toggleRoutesPanel()'>✖</button></div>" +
        "   <div id='routes-list'></div>" +
        "</div>" +

        "<script>" +
        "window.onerror = function(msg, url, line) { Android.log('JS ERROR: ' + msg + ' (Line: ' + line + ')'); };" +
        "var currentLat = 55.75; var currentLng = 37.61; var currentAlt = 0;" +
        "var map = L.map('map', {zoomControl: false, attributionControl: false}).setView([currentLat, currentLng], 3);" +
        "var customTrailsLayer = L.layerGroup().addTo(map);" +
        "var currentDrawLayer = L.layerGroup().addTo(map);" +
        "var recordLayer = L.layerGroup().addTo(map);" +
        "var savedTrailsData = []; var isDrawing = false; var isRecording = false;" +
        "var currentStrokes = []; var currentPoints = []; var currentPolyline = null;" +
        "var recordPoints = []; var recordPolyline = null; var lastRecordPoint = null;" +

        "var baseUrl = 'https://{s}.google.com/vt/lyrs=s&x={x}&y={y}&z={z}';" +
        "var darkUrl = 'https://{s}.basemaps.cartocdn.com/dark_all/{z}/{x}/{y}{r}.png';" +
        "var topoUrl = 'https://{s}.tile.opentopomap.org/{z}/{x}/{y}.png';" +
        "var cyclosmUrl = 'https://{s}.tile-cyclosm.openstreetmap.fr/cyclosm/{z}/{x}/{y}.png';" +

        "var OfflineTileLayer = L.TileLayer.extend({" +
        "    createTile: function (coords, done) {" +
        "        var tile = document.createElement('img'); var url = this.getTileUrl(coords);" +
        "        localforage.getItem(url).then(function(blob) {" +
        "            if (blob) { tile.src = URL.createObjectURL(blob); tile.onload = function() { done(null, tile); };" +
        "            } else { fetch(url).then(res => res.blob()).then(blob => { localforage.setItem(url, blob);" +
        "                    tile.src = URL.createObjectURL(blob); tile.onload = function() { done(null, tile); };" +
        "                }).catch(err => { tile.src = url; tile.onload = function() { done(null, tile); }; }); }" +
        "        }).catch(function() { tile.src = url; tile.onload = function() { done(null, tile); }; });" +
        "        return tile;" +
        "    }" +
        "});" +

        "var satLayer = new OfflineTileLayer(baseUrl, { maxZoom: 19, subdomains:['mt0','mt1','mt2','mt3'] });" +
        "var darkLayer = new OfflineTileLayer(darkUrl, { maxZoom: 19, subdomains:['a','b','c'] });" +
        "var topoLayer = new OfflineTileLayer(topoUrl, { maxZoom: 17 });" +
        "var cycloLayer = new OfflineTileLayer(cyclosmUrl, { maxZoom: 20 });" +
        "satLayer.addTo(map);" +
        "var baseMaps = {'🛰️ Спутник': satLayer, '🌙 Ночная': darkLayer, '🗺️ Топо': topoLayer, '🚵 Грунт': cycloLayer};" +
        "L.control.layers(baseMaps, {}, {position: 'topright'}).addTo(map);" +

        "var bikeIcon = L.divIcon({ className: 'bike-marker', html: '<div class=\"bike-icon-inner\">🏍️</div>', iconSize: [40, 40], iconAnchor: [20, 20] });" +
        "var marker = L.marker([currentLat, currentLng], {icon: bikeIcon}).addTo(map);" +

        "function calcDistance(lat1, lon1, lat2, lon2) {" +
        "  var R = 6371; var dLat = (lat2 - lat1) * Math.PI / 180; var dLon = (lon2 - lon1) * Math.PI / 180;" +
        "  var a = Math.sin(dLat/2) * Math.sin(dLat/2) + Math.cos(lat1 * Math.PI / 180) * Math.cos(lat2 * Math.PI / 180) * Math.sin(dLon/2) * Math.sin(dLon/2);" +
        "  var c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1-a)); return R * c;" +
        "}" +

        "var totalDistance = 0.0;" +
        "window.updateLocation = function(lat, lng, bearing, firstFix, alt) {" +
        "   currentLat = lat; currentLng = lng; currentAlt = alt;" +
        "   marker.setLatLng([lat, lng]);" +
        "   if (firstFix) { map.setView([lat, lng], 16); }" +
        "   var icon = document.querySelector('.bike-icon-inner');" +
        "   if (icon && bearing !== null) { icon.style.transform = 'rotate(' + bearing + 'deg)'; }" +
        
        "   if (lastRecordPoint) {" +
        "       var d = calcDistance(lastRecordPoint[0], lastRecordPoint[1], lat, lng);" +
        "       if (d > 0.01) { totalDistance += d; document.getElementById('odo_text').innerText = totalDistance.toFixed(1); }" +
        "   }" +
        "   lastRecordPoint = [lat, lng];" +

        "   if (isRecording) {" +
        "       recordPoints.push([lat, lng]);" +
        "       if (!recordPolyline) { recordPolyline = L.polyline(recordPoints, {color: '#00FF00', weight: 5}).addTo(recordLayer); }" +
        "       else { recordPolyline.setLatLngs(recordPoints); }" +
        "   }" +
        "};" +

        "function toggleRoutesPanel() { document.getElementById('routes-panel').classList.toggle('open'); }" +

        "function updateRoutesUI() {" +
        "    var list = document.getElementById('routes-list');" +
        "    list.innerHTML = '';" +
        "    if (savedTrailsData.length === 0) { list.innerHTML = '<div style=\"color:#888; text-align:center; margin-top:20px;\">Нет сохраненных маршрутов</div>'; return; }" +
        "    savedTrailsData.forEach(function(route, index) {" +
        "        var div = document.createElement('div'); div.className = 'route-item';" +
        "        var html = \"<div class='route-info'>Маршрут #\" + (index + 1) + \"<br><span style='font-size:12px;color:#aaa;'>Точек: \" + route[0].length + \"</span></div>\" +" +
        "                   \"<div class='route-actions'>\" +" +
        "                   \"<button class='action-btn show' onclick='showRoute(\" + index + \")'>📍</button>\" +" +
        "                   \"<button class='action-btn' onclick='shareRoute(\" + index + \")'>📤</button>\" +" +
        "                   \"<button class='action-btn delete' onclick='deleteRoute(\" + index + \")'>🗑️</button>\" +" +
        "                   \"</div>\";" +
        "        div.innerHTML = html;" +
        "        list.appendChild(div);" +
        "    });" +
        "}" +

        "window.showRoute = function(index) {" +
        "    customTrailsLayer.clearLayers();" +
        "    var route = savedTrailsData[index][0];" +
        "    var polyline = L.polyline(route, {color: '#00E5FF', weight: 6}).addTo(customTrailsLayer);" +
        "    map.fitBounds(polyline.getBounds(), {padding: [50, 50]});" +
        "    toggleRoutesPanel();" +
        "};" +

        "window.shareRoute = function(index) {" +
        "    var route = savedTrailsData[index][0];" +
        "    var start = route[0]; var end = route[route.length - 1];" +
        "    var msg = 'Маршрут Enduro Map #' + (index + 1) + ':\\n' +" +
        "              'Старт: https://maps.google.com/?q=' + start[0] + ',' + start[1] + '\\n' +" +
        "              'Финиш: https://maps.google.com/?q=' + end[0] + ',' + end[1];" +
        "    Android.shareText(msg);" +
        "};" +

        "window.deleteRoute = function(index) {" +
        "    if(confirm('Удалить маршрут #' + (index + 1) + '?')) {" +
        "        savedTrailsData.splice(index, 1);" +
        "        localforage.setItem('custom_trails', savedTrailsData).then(function() {" +
        "            updateRoutesUI(); customTrailsLayer.clearLayers();" +
        "        });" +
        "    }" +
        "};" +

        "function loadTrails() {" +
        "    localforage.getItem('custom_trails').then(function(trailsData) {" +
        "        if (trailsData) { savedTrailsData = trailsData; updateRoutesUI(); }" +
        "        else { updateRoutesUI(); }" +
        "    });" +
        "}" +
        "loadTrails();" +

        "var overlay = document.getElementById('draw-overlay');" +
        "overlay.addEventListener('touchstart', function(e) {" +
        "    if (e.touches.length > 1 || !isDrawing) return; e.preventDefault();" +
        "    var touch = e.touches[0]; var rect = overlay.getBoundingClientRect();" +
        "    var latlng = map.containerPointToLatLng([touch.clientX - rect.left, touch.clientY - rect.top]);" +
        "    currentPoints = [[latlng.lat, latlng.lng]];" +
        "    currentPolyline = L.polyline(currentPoints, {color: '#FF5722', weight: 6, dashArray: '8, 8'}).addTo(currentDrawLayer);" +
        "}, { passive: false });" +
        "overlay.addEventListener('touchmove', function(e) {" +
        "    if (e.touches.length > 1 || !isDrawing) return; e.preventDefault();" +
        "    var touch = e.touches[0]; var rect = overlay.getBoundingClientRect();" +
        "    var latlng = map.containerPointToLatLng([touch.clientX - rect.left, touch.clientY - rect.top]);" +
        "    if (currentPolyline) { currentPoints.push([latlng.lat, latlng.lng]); currentPolyline.setLatLngs(currentPoints); }" +
        "}, { passive: false });" +
        "overlay.addEventListener('touchend', function(e) {" +
        "    if (!isDrawing) return; e.preventDefault();" +
        "    if (currentPoints.length > 1) { currentStrokes.push(currentPoints); }" + 
        "    currentPoints = []; currentPolyline = null;" +
        "}, { passive: false });" +

        "var drawContainer, recordContainer;" +

        "var drawControl = L.Control.extend({ options: { position: 'topleft' }, onAdd: function (map) {" +
        "    drawContainer = L.DomUtil.create('div', 'custom-btn'); drawContainer.innerHTML = '✏️';" +
        "    L.DomEvent.disableClickPropagation(drawContainer);" +
        "    drawContainer.onclick = function() { " +
        "        isDrawing = !isDrawing; isRecording = false; " +
        "        if (isDrawing) { map.dragging.disable(); overlay.style.display = 'block'; drawContainer.classList.add('btn-active'); } " +
        "        else { map.dragging.enable(); overlay.style.display = 'none'; drawContainer.classList.remove('btn-active'); } " +
        "    };" +
        "    return drawContainer; } });" +
        "map.addControl(new drawControl());" +

        "var recordControl = L.Control.extend({ options: { position: 'topleft' }, onAdd: function (map) {" +
        "    recordContainer = L.DomUtil.create('div', 'custom-btn'); recordContainer.innerHTML = '⏺️';" +
        "    L.DomEvent.disableClickPropagation(recordContainer);" +
        "    recordContainer.onclick = function() { " +
        "        isRecording = !isRecording; isDrawing = false; overlay.style.display = 'none'; " +
        "        if (isRecording) { recordPoints = []; recordPolyline = null; totalDistance = 0.0; recordContainer.classList.add('btn-active'); Android.log('Запись трека ВКЛ'); } " +
        "        else { recordContainer.classList.remove('btn-active'); Android.log('Запись трека ВЫКЛ'); } " +
        "    };" +
        "    return recordContainer; } });" +
        "map.addControl(new recordControl());" +

        "var undoControl = L.Control.extend({ options: { position: 'topleft' }, onAdd: function (map) {" +
        "    var container = L.DomUtil.create('div', 'custom-btn'); container.innerHTML = '↩️';" +
        "    L.DomEvent.disableClickPropagation(container);" +
        "    container.onclick = function() { if (currentStrokes.length > 0) { currentStrokes.pop(); currentDrawLayer.clearLayers(); currentStrokes.forEach(function(stroke) { L.polyline(stroke, {color: '#FF5722', weight: 6, dashArray: '8, 8'}).addTo(currentDrawLayer); }); } };" +
        "    return container; } });" +
        "map.addControl(new undoControl());" +

        "var saveControl = L.Control.extend({ options: { position: 'topleft' }, onAdd: function (map) {" +
        "    var container = L.DomUtil.create('div', 'custom-btn'); container.innerHTML = '💾';" +
        "    L.DomEvent.disableClickPropagation(container);" +
        "    container.onclick = function() { " +
        "        if (isRecording && recordPoints.length > 1) {" +
        "            savedTrailsData.push([recordPoints]); isRecording = false; recordContainer.classList.remove('btn-active'); recordLayer.clearLayers();" +
        "        } else if (currentStrokes.length > 0) { savedTrailsData.push(currentStrokes); currentDrawLayer.clearLayers(); currentStrokes = []; isDrawing = false; overlay.style.display = 'none'; map.dragging.enable(); if(drawContainer) drawContainer.classList.remove('btn-active'); } " +
        "        else { alert('Нечего сохранять!'); return; }" +
        "        localforage.setItem('custom_trails', savedTrailsData).then(function() { updateRoutesUI(); alert('Маршрут сохранен в список!'); });" +
        "    };" +
        "    return container; } });" +
        "map.addControl(new saveControl());" +

        "var listControl = L.Control.extend({ options: { position: 'topleft' }, onAdd: function (map) {" +
        "    var container = L.DomUtil.create('div', 'custom-btn'); container.innerHTML = '📋';" +
        "    L.DomEvent.disableClickPropagation(container);" +
        "    container.onclick = function() { toggleRoutesPanel(); };" +
        "    return container; } });" +
        "map.addControl(new listControl());" +

        "var sosControl = L.Control.extend({ options: { position: 'topleft' }, onAdd: function (map) {" +
        "    var container = L.DomUtil.create('div', 'custom-btn btn-sos'); container.innerHTML = 'SOS';" +
        "    L.DomEvent.disableClickPropagation(container);" +
        "    container.onclick = function() { Android.triggerSOS(currentLat, currentLng); };" +
        "    return container; } });" +
        "map.addControl(new sosControl());" +

        "var locateControl = L.Control.extend({ options: { position: 'bottomright' }, onAdd: function (map) {" +
        "    var container = L.DomUtil.create('div', 'custom-btn'); container.innerHTML = '📍';" +
        "    container.style.marginBottom = '20px';" +
        "    L.DomEvent.disableClickPropagation(container);" +
        "    container.onclick = function() { map.setView([currentLat, currentLng], 16); };" +
        "    return container; } });" +
        "map.addControl(new locateControl());" +
        "</script></body></html>";

    public class WebAppInterface {
        @JavascriptInterface
        public void log(String message) { debugLog.append(message).append("\n"); }

        @JavascriptInterface
        public void shareText(final String text) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    Intent sendIntent = new Intent();
                    sendIntent.setAction(Intent.ACTION_SEND);
                    sendIntent.putExtra(Intent.EXTRA_TEXT, text);
                    sendIntent.setType("text/plain");
                    startActivity(Intent.createChooser(sendIntent, "Поделиться маршрутом"));
                }
            });
        }

        @JavascriptInterface
        public void triggerSOS(double lat, double lng) {
            String message = "SOS! Я застрял. Мои координаты: " + lat + ", " + lng + ". Ссылка на карту: https://maps.google.com/?q=" + lat + "," + lng;
            Intent intent = new Intent(Intent.ACTION_SENDTO);
            intent.setData(Uri.parse("smsto:"));
            intent.putExtra("sms_body", message);
            startActivity(intent);
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);

        speedText = findViewById(R.id.speed_text);
        gpsStatus = findViewById(R.id.gps_status);
        altText = findViewById(R.id.alt_text);
        odoText = findViewById(R.id.odo_text);
        webView = findViewById(R.id.map_webview);

        notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        createNotificationChannel();

        webView.setLayerType(View.LAYER_TYPE_HARDWARE, null);
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
        settings.setCacheMode(WebSettings.LOAD_CACHE_ELSE_NETWORK); 
        settings.setLoadWithOverviewMode(true);
        settings.setUseWideViewPort(true);

        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onConsoleMessage(ConsoleMessage consoleMessage) {
                debugLog.append("JS Console: ").append(consoleMessage.message()).append("\n");
                return true;
            }
        });

        webView.addJavascriptInterface(new WebAppInterface(), "Android");
        webView.loadDataWithBaseURL("https://localhost", MAP_HTML, "text/html", "UTF-8", null);
        isMapLoaded = true;

        speedText.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                new AlertDialog.Builder(MainActivity.this)
                    .setTitle("Логи отладки")
                    .setMessage(debugLog.length() == 0 ? "Ошибок нет!" : debugLog.toString())
                    .setPositiveButton("Копировать", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                            clipboard.setPrimaryClip(ClipData.newPlainText("Logs", debugLog.toString()));
                            Toast.makeText(MainActivity.this, "Логи скопированы!", Toast.LENGTH_SHORT).show();
                        }
                    }).show();
                return true;
            }
        });

        if (Build.VERSION.SDK_INT >= 33) {
            requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.POST_NOTIFICATIONS}, 1);
        } else if (Build.VERSION.SDK_INT >= 23) {
            requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, 1);
        } else {
            checkGpsEnabled();
            startGPS();
        }
    }

    @Override
    protected void onDestroy() {
        // УДАЛЯЕМ УВЕДОМЛЕНИЕ ПРИ ЗАКРЫТИИ ПРИЛОЖЕНИЯ
        if (notificationManager != null) {
            notificationManager.cancel(NOTIF_ID);
        }
        super.onDestroy();
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel channel = new NotificationChannel("gps_channel", "GPS Status", NotificationManager.IMPORTANCE_LOW);
            notificationManager.createNotificationChannel(channel);
        }
    }

    private void updateNotification(String status, String details) {
        if (status.equals(lastNotifText)) return;
        lastNotifText = status;
        Intent intent = new Intent(this, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE);
        Notification.Builder builder = (Build.VERSION.SDK_INT >= 26) ? new Notification.Builder(this, "gps_channel") : new Notification.Builder(this);
        
        // Убрали .setOngoing(true), чтобы уведомление можно было смахнуть
        builder.setContentTitle("Enduro Map: " + status).setContentText(details).setSmallIcon(android.R.drawable.ic_menu_mylocation).setContentIntent(pendingIntent).setOngoing(false);
        
        notificationManager.notify(NOTIF_ID, builder.build());
    }

    private void checkGpsEnabled() {
        LocationManager lm = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        if (lm != null && !lm.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
            gpsStatus.setText("GPS ВЫКЛ"); gpsStatus.setTextColor(0xFFFF0000);
            updateNotification("GPS Выключен", "Включите геолокацию!");
            Intent intent = new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS);
            startActivity(intent);
        } else {
            startGPS();
        }
    }

    private void startGPS() {
        locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        if (locationManager == null) return;
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) return;

        showLastKnownLocation(LocationManager.GPS_PROVIDER);
        showLastKnownLocation(LocationManager.NETWORK_PROVIDER);

        try {
            locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 1000, 1, this);
            locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000, 0, this);
            gpsStatus.setText("ПОИСК GPS"); gpsStatus.setTextColor(0xFFFF9800);
            updateNotification("Поиск спутников", "Ожидание сигнала...");
        } catch (SecurityException e) {
            gpsStatus.setText("НЕТ ДОСТУПА"); gpsStatus.setTextColor(0xFFFF0000);
        }
    }

    private void showLastKnownLocation(String provider) {
        if (Build.VERSION.SDK_INT >= 23 && checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) return;
        try {
            Location lastLocation = locationManager.getLastKnownLocation(provider);
            if (lastLocation != null) {
                double alt = lastLocation.getAltitude();
                String js = "if (typeof updateLocation === 'function') { updateLocation(" + lastLocation.getLatitude() + ", " + lastLocation.getLongitude() + ", null, 0, " + alt + "); }";
                webView.evaluateJavascript(js, null);
                gpsStatus.setText("КЭШ GPS"); gpsStatus.setTextColor(0xFFAAAAAA);
            }
        } catch (SecurityException ignored) {}
    }

    @Override
    public void onLocationChanged(Location location) {
        if (location != null && location.getLatitude() != 0.0) {
            float accuracy = location.getAccuracy();
            if (accuracy > 50.0f) {
                gpsStatus.setText("СЛАБЫЙ СИГНАЛ"); gpsStatus.setTextColor(0xFFFF9800);
                return; 
            }

            double lat = location.getLatitude(); double lng = location.getLongitude();
            float speed = location.getSpeed(); float bearing = location.getBearing(); 
            double alt = location.getAltitude();
            int speedKmh = Math.round(speed * 3.6f);
            
            speedText.setText(String.valueOf(speedKmh)); 
            altText.setText(String.valueOf(Math.round(alt)));
            gpsStatus.setText("GPS: " + Math.round(accuracy) + "м");
            gpsStatus.setTextColor(0xFF00E5FF);
            
            updateNotification("GPS Активен", "Скорость: " + speedKmh + " км/ч");

            if (isMapLoaded) {
                String js = "if (typeof updateLocation === 'function') { updateLocation(" + lat + ", " + lng + ", " + bearing + ", " + (isFirstFix ? 1 : 0) + ", " + alt + "); }";
                webView.evaluateJavascript(js, null);
                if (isFirstFix) isFirstFix = false; 
            }
        }
    }

    @Override
    public void onStatusChanged(String provider, int status, Bundle extras) {
        if (provider.equals(LocationManager.GPS_PROVIDER)) {
            if (status == android.location.LocationProvider.OUT_OF_SERVICE) {
                gpsStatus.setText("ПОИСК GPS"); gpsStatus.setTextColor(0xFFFF9800);
            } else if (status == android.location.LocationProvider.AVAILABLE) {
                gpsStatus.setText("GPS АКТИВЕН"); gpsStatus.setTextColor(0xFF00E5FF);
            }
        }
    }

    @Override public void onProviderEnabled(String provider) { startGPS(); }
    @Override
    public void onProviderDisabled(String provider) {
        gpsStatus.setText("GPS ВЫКЛ"); gpsStatus.setTextColor(0xFFFF0000); updateNotification("GPS Выключен", "Сигнал потерян.");
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        if (requestCode == 1) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) { checkGpsEnabled(); startGPS(); } 
            else { Toast.makeText(this, "Без GPS карта не будет работать!", Toast.LENGTH_LONG).show(); }
        }
    }
}
