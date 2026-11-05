class Dumpsite(
    private val name : String,
    private val latitude : Double,
    private val longitude : Double,
    private var status : Boolean
){
    init {
        require(name.isNotEmpty()) { "Name must not be empty" }
        require(latitude in -90.0..90.0) { "Latitude must be between -90 and 90" }
        require(longitude in -180.0..180.0) { "Longitude must be between -180 and 180" }
    }

    fun setStatus(stat:Boolean) {
        fun setStatus(stat: Any) {
            if (stat is Boolean) {
                status = stat
            } else {
                println("Invalid input!")
            }
        }
    }



}