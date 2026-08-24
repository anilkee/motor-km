package com.seferdefteri.app.ui

import android.graphics.Color as AColor
import android.graphics.Paint
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import com.seferdefteri.app.LatLon
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Polygon
import org.osmdroid.views.overlay.Polyline

/**
 * Rotayi cizen harita.
 *
 * @param path       cizilecek noktalar
 * @param followLast true ise harita son konumu takip eder (canli vardiya)
 * @param fitAll     true ise tum rota ekrana sigdirilir (gecmis kaydi)
 */
@Composable
fun RouteMap(
    path: List<LatLon>,
    modifier: Modifier = Modifier,
    followLast: Boolean = false,
    fitAll: Boolean = false,
    /** Paket birakilan yerler; haritada ayri renkte isaretlenir. */
    paketler: List<LatLon> = emptyList()
) {
    val ctx = LocalContext.current

    val mapView = remember {
        MapView(ctx).apply {
            setTileSource(TileSourceFactory.MAPNIK)
            setMultiTouchControls(true)
            zoomController.setVisibility(CustomZoomButtonsController.Visibility.NEVER)
            isTilesScaledToDpi = true
            controller.setZoom(16.0)
            // Konum gelene kadar Turkiye merkezli dursun.
            controller.setCenter(GeoPoint(39.925, 32.866))
        }
    }

    DisposableEffect(Unit) {
        mapView.onResume()
        onDispose {
            mapView.onPause()
            mapView.onDetach()
        }
    }

    // Rota veya paketler degistikce cizimi tazele.
    LaunchedEffect(path.size, path.lastOrNull(), paketler.size) {
        mapView.overlays.clear()

        if (path.size >= 2) {
            val line = Polyline(mapView).apply {
                outlinePaint.color = AColor.parseColor("#1565C0")
                outlinePaint.strokeWidth = 14f
                outlinePaint.strokeCap = Paint.Cap.ROUND
                outlinePaint.strokeJoin = Paint.Join.ROUND
                outlinePaint.isAntiAlias = true
                setPoints(path.map { GeoPoint(it.lat, it.lon) })
            }
            mapView.overlays.add(line)
        }

        // Paket birakilan yerler (turuncu), rotanin ustunde dursun.
        paketler.forEach { p ->
            mapView.overlays.add(nokta(p, "#E08700", 26.0))
        }

        path.firstOrNull()?.let { basla ->
            mapView.overlays.add(nokta(basla, "#0E7C43", 30.0))
        }
        if (path.size >= 2) {
            path.lastOrNull()?.let { son ->
                mapView.overlays.add(nokta(son, "#C62828", 34.0))
            }
        }

        mapView.invalidate()
    }

    // Kamera konumu.
    LaunchedEffect(path.size, followLast, fitAll) {
        if (path.isEmpty()) return@LaunchedEffect
        if (fitAll && path.size >= 2) {
            val kuzey = path.maxOf { it.lat }
            val guney = path.minOf { it.lat }
            val dogu = path.maxOf { it.lon }
            val bati = path.minOf { it.lon }
            mapView.post {
                runCatching {
                    mapView.zoomToBoundingBox(
                        BoundingBox(kuzey, dogu, guney, bati).increaseByScale(1.3f),
                        false,
                        48
                    )
                }
            }
        } else {
            val hedef = path.last()
            mapView.post {
                runCatching {
                    if (followLast) {
                        mapView.controller.animateTo(GeoPoint(hedef.lat, hedef.lon))
                    } else {
                        mapView.controller.setCenter(GeoPoint(hedef.lat, hedef.lon))
                    }
                }
            }
        }
    }

    AndroidView(factory = { mapView }, modifier = modifier)
}

/** Harita uzerine renkli daire koyar (baslangic / bitis isareti). */
private fun nokta(p: LatLon, renk: String, metre: Double): Polygon =
    Polygon().apply {
        points = Polygon.pointsAsCircle(GeoPoint(p.lat, p.lon), metre)
        fillPaint.color = AColor.parseColor(renk)
        fillPaint.alpha = 210
        outlinePaint.color = AColor.WHITE
        outlinePaint.strokeWidth = 5f
        outlinePaint.isAntiAlias = true
    }
