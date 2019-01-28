package nl.knmi.geoweb.backend.product.airmet;

import java.util.List;
import java.util.Map;

import lombok.Getter;

/*
 * {
    "location_indicator_wmo": "EHDB",
    "firareas": {
      "EHAA": {
        "firname": "AMSTERDAM FIR",
        "location_indicator_icao": "EHAA",
        "areapreset": "NL_FIR",
        "maxhoursofvalidity": 4,
        "hoursbeforevalidity": 1,
        "adjacent_firs": [
          "EKDK",
          "EDWW",
          "EDGG",
          "EBBU",
          "EGTT",
          "EGPX" 
        ]
      }
    },
    "active_firs": [ "EHAA" ]
}
 */

@Getter
public class AirmetParameters {
	@Getter
    static public class FirArea {
    	String location_indicator_icao;
    	String firname;
    	String areapreset;
    	float maxhoursofvalidity;
    	float hoursbeforevalidity;
    	private List<String> adjacent_firs;
    }
	
	private Map<String, FirArea> firareas;
	private String location_indicator_wmo;
	private List<String> active_firs;
}