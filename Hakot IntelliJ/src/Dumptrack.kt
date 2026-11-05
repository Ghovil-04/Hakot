open class Dumptrack(
    private var plateNum : String,

//if truck is working or not
    protected var isCollecting : Boolean = false,
    protected var route : Route? = null,
    protected var Destination : Dumpsite? = null,

//for Tracking the truck feature
    protected var isOnline : Boolean = false,
    private var driverName : String? = null,
    protected var speed : Double? = null,
    protected var latitude : Double? = null,
    protected var longitude : Double? = null

){
    init {
        require(plateNum.isNotEmpty()) { "Plate Number must not be empty" }
        require(! isOnline || isCollecting) {"Truck can only be online when collecting"}

        if (isCollecting) {
            require(route != null) { "Route must not be null" }
            require(Destination != null) { "Destination must not be null" }

            if (isOnline) {
                require(!driverName.isNullOrBlank()) { "Driver Name must not be empty" }
                require(speed != null && speed!! >= 0.0) { "Speed must be zero or positive" }
                require(latitude != null && latitude!! in -90.0..90.0) { "Latitude must be between -90 and 90" }
                require(longitude != null && longitude!! in -180.0..180.0) { "Longitude must be between -180 and 180" }
            }
        }

    }

    fun plateNumber(plateNum: String) {this.plateNum = plateNum}
    fun collecting(
        routeName: String,
        streets: List<String>,
        destination: Dumpsite
    ){
        require(routeName.isNotBlank()) {"Route name must not be empty"}
        require(streets.isNotEmpty()) {"At least one street is required"}

        this.route = Route(routeName, streets)
        this.Destination = destination
        isCollecting = true
    }
    fun status(
        driverName: String,
        speed: Double,
        latitude: Double,
        longitude: Double
    ){
        require(isCollecting) {"Truck must be collecting before going online"}
        require(driverName.isNotBlank()) {"Driver Name must not be empty"}
        require(speed >= 0.0) {"Speed must be zero or positive"}
        require(latitude in -90.0..90.0) {"Latitude must be between -90 and 90"}
        require(longitude in -180.0..180.0) {"Longitude must be between -180 and 180"}

        this.driverName = driverName
        this.speed = speed
        this.latitude = latitude
        this.longitude = longitude
        isOnline = true
    }


}