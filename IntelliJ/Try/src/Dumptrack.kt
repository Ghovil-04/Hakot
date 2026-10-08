class Dumptrack(
    private val plateNum : String = "",
    private val driverName : String = "",
    protected var route : Route,
    protected var Destination : Dumpsite,

    /*
    var speed : Double
    var lat
    var long
     */
) {
    fun assignInfo(PlateNum: String, DriverName: String) {
        plateNum = PlateNum
        driverName = DriverName
    }
}
/* fun getter(plateNum : String, driverName : String) : Pair<String,String>{
     return Pair(plateNum,driverName)
 }*/