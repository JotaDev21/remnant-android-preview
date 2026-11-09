package com.liferpg.data

import android.content.Context
import android.util.AtomicFile
import com.liferpg.domain.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

/** Precise routes are excluded from Android backup/transfer as well as account cloud saves. */
class CardioRouteStore(context: Context) {
    private val directory = File(context.noBackupFilesDir, "cardio_routes")
    private val revisions = MutableStateFlow(0L)
    private fun file(id: String): AtomicFile {
        require(UUID.fromString(id).toString() == id)
        check(directory.isDirectory || directory.mkdirs())
        return AtomicFile(File(directory, "$id.json"))
    }
    fun observe(id: String) = revisions.map { read(id) }
    @Synchronized fun read(id: String): CardioRouteState {
        val f = file(id)
        if (!f.baseFile.exists() && !File(f.baseFile.path + ".bak").exists()) return CardioRouteState()
        val bytes = f.readFully(); require(bytes.size <= 1_500_000)
        val o = JSONObject(bytes.toString(Charsets.UTF_8)); val array = o.getJSONArray("points")
        require(array.length() <= CardioRouteEngine.MAX_POINTS)
        val points = (0 until array.length()).map { i -> val p = array.getJSONArray(i)
            CardioFix(p.getDouble(0), p.getDouble(1), p.getDouble(2).toFloat(), p.getLong(3), p.getInt(4)) }
        require(points.all { it.lat.isFinite() && it.lon.isFinite() && it.lat in -90.0..90.0 && it.lon in -180.0..180.0 && it.accuracy in 0f..25f && it.elapsed > 0 && it.segment >= 0 })
        val meters = o.getDouble("meters"); val steps = if (o.isNull("steps")) null else o.getLong("steps"); val segment = o.getInt("segment")
        require(meters.isFinite() && meters in 0.0..200_000.0 && (steps == null || steps in 0..200_000) && segment in 0..100_000)
        val signal = o.optLong("lastSignalElapsed", 0L); require(signal >= 0)
        return CardioRouteState(points, meters, steps, segment, signal)
    }
    @Synchronized fun write(id: String, state: CardioRouteState) {
        val array = JSONArray(); state.points.forEach { p -> array.put(JSONArray(listOf(p.lat, p.lon, p.accuracy, p.elapsed, p.segment))) }
        val bytes = JSONObject().put("points", array).put("meters", state.meters).put("steps", state.steps ?: JSONObject.NULL).put("segment", state.segment)
            .put("lastSignalElapsed", state.lastSignalElapsed).toString().toByteArray()
        val f = file(id); val output = f.startWrite()
        try { output.write(bytes); f.finishWrite(output) } catch (e: Exception) { f.failWrite(output); throw e }
        revisions.value += 1
    }
    @Synchronized fun delete(id: String) { file(id).delete(); revisions.value += 1 }
    @Synchronized fun clear() { directory.listFiles()?.filter { it.isFile }?.forEach { check(it.delete()) }; revisions.value += 1 }
}
