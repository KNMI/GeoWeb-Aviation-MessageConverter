package nl.knmi.geoweb.backend.aviation;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import nl.knmi.adaguc.tools.Debug;

import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
@Component
public class AirportStore {
	private String airportFile;
	private Map<String, AirportInfo> airportInfos;
	public AirportStore(@Value(value = "${productstorelocation}") String productstorelocation){
		this.airportFile=productstorelocation+"/admin/config/"+"BREM_20160310.json";
	}

	public void initStore() {
		this.airportInfos=new HashMap<String,AirportInfo>();
		File fn=new File(this.airportFile);
		ObjectMapper om=new ObjectMapper();
		try {
			AirportJsonRecord[] airports = om.readValue(fn, AirportJsonRecord[].class);
			for (AirportJsonRecord airport: airports) {
				try {
					AirportInfo airportInfo=new AirportInfo(airport.getIcao(), airport.getName(), Float.parseFloat(airport.getLat()), Float.parseFloat(airport.getLon()), Float.parseFloat(airport.getHeight()));
					airportInfos.put(airport.getIcao(), airportInfo);
				} catch (NumberFormatException e) {
					Debug.println("Error parsing airport record "+airport.getIcao());
				}
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
		Debug.println("Found "+airportInfos.size()+" records of airportinfo");
	}

	public AirportInfo lookup(String ICAO) {
		if (airportInfos==null) {
			initStore();
		}
		return airportInfos.get(ICAO);
	}
}
