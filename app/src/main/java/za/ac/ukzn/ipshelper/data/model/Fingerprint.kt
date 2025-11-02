package za.ac.ukzn.ipshelper.data.model

data class Fingerprint(
    val timestampMs: Long,
    val x: Double,
    val y: Double,
    val z: Int,
    val place: String,
    val accessPoints: List<AccessPoint>
)
