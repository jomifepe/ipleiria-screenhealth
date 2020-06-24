import java.util.*

fun compareTimestampsDateEqual(timestamp1: Long, timestamp2: Long): Boolean {
    val cal1 = Calendar.getInstance()
    cal1.timeInMillis = timestamp1
    cal1.set(Calendar.HOUR_OF_DAY, 0)
    cal1.set(Calendar.MINUTE, 0)
    cal1.set(Calendar.SECOND, 0)
    cal1.set(Calendar.MILLISECOND, 0)
    val cal2 = Calendar.getInstance()
    cal2.timeInMillis = timestamp2
    cal2.set(Calendar.HOUR_OF_DAY, 0)
    cal2.set(Calendar.MINUTE, 0)
    cal2.set(Calendar.SECOND, 0)
    cal2.set(Calendar.MILLISECOND, 0)

    return cal1.timeInMillis == cal2.timeInMillis
}

fun getHoursMinutesSeconds(timeInMillis: Long): Triple<Long, Long, Long> {
    val seconds: Long = (timeInMillis / 1000) % 60
    val minutes: Long = (timeInMillis / (1000 * 60)) % 60
    val hours: Long = (timeInMillis / (1000 * 60 * 60))

    return Triple(hours, minutes, seconds)
}