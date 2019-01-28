package nl.knmi.geoweb.backend.product.airmet;

import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;

import java.io.IOException;
import java.time.OffsetDateTime;
import java.time.ZoneId;

import org.geojson.GeoJsonObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringRunner;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import nl.knmi.adaguc.tools.Debug;
import nl.knmi.adaguc.tools.Tools;
import nl.knmi.geoweb.backend.product.airmet.Airmet;
import nl.knmi.geoweb.backend.product.airmet.Airmet.Phenomenon;
//import nl.knmi.geoweb.backend.product.airmet.Airmet.AirmetChange;
import nl.knmi.geoweb.backend.product.airmet.Airmet.AirmetStatus;
import nl.knmi.geoweb.backend.product.airmet.AirmetStore;
import nl.knmi.geoweb.backend.product.airmet.AirmetStoreTestConfig;

@RunWith(SpringRunner.class)
@SpringBootTest
@ContextConfiguration(classes = {AirmetStoreTestConfig.class})
public class AirmetStoreTest {
	@Autowired
	@Qualifier("airmetObjectMapper")
	private ObjectMapper airmetObjectMapper;
	
	public final String airmetStoreLocation = "/tmp/junit/geowebbackendstore/";
	
	static String testGeoJson="{\"type\":\"FeatureCollection\",\"features\":[{\"type\":\"Feature\",\"geometry\":{\"type\":\"Polygon\",\"coordinates\":[[[4.44963571205923,52.75852934878266],[1.4462013467168233,52.00458561642831],[5.342222631879865,50.69927379063084],[7.754619712476178,50.59854892065259],[8.731640530117685,52.3196364467871],[8.695454573908739,53.50720041878871],[6.847813968390116,54.08633053026368],[3.086939481359807,53.90252679590722]]]},\"properties\":{\"prop0\":\"value0\",\"prop1\":{\"this\":\"that\"}}}]}";

	static String testSigmet="{\"geojson\":"
			+"{\"type\":\"FeatureCollection\",\"features\":"+"[{\"type\":\"Feature\",\"properties\":{\"prop0\":\"value0\",\"prop1\":{\"this\":\"that\"}},\"geometry\":{\"type\":\"Polygon\",\"coordinates\":[[[4.44963571205923,52.75852934878266],[1.4462013467168233,52.00458561642831],[5.342222631879865,50.69927379063084],[7.754619712476178,50.59854892065259],[8.731640530117685,52.3196364467871],[8.695454573908739,53.50720041878871],[6.847813968390116,54.08633053026368],[3.086939481359807,53.90252679590722]]]}}]},"
			+"\"phenomenon\":\"OBSC_TS\","
			+"\"obs_or_forecast\":{\"obs\":true},"
			+"\"level\":{\"lev1\":{\"value\":100.0,\"unit\":\"FL\"}},"
			+"\"movement_type\":\"STATIONARY\","
			+"\"movement\":{\"stationary\":true},"
			+"\"change\":\"NC\","
			+"\"issuedate\":\"2017-03-24T15:56:16Z\","
			+"\"validdate\":\"2017-03-24T15:56:16Z\","
			+"\"firname\":\"AMSTERDAM FIR\","
			+"\"location_indicator_icao\":\"EHAA\","
			+"\"location_indicator_mwo\":\"EHDB\"}";
	
	@Test
	public void contextLoads() throws Exception {
		assertThat(airmetObjectMapper,notNullValue());
	}
	
	public Airmet createAirmet () throws Exception {
		Airmet sm=new Airmet("AMSTERDAM FIR", "EHAA", "EHDB", "abcd");
		sm.setPhenomenon(Phenomenon.getPhenomenon("ISOL_CB"));
		sm.setValiddate(OffsetDateTime.now(ZoneId.of("Z")).minusHours(1));
//		sm.setChange(SigmetChange.NC);
		setGeoFromString(sm, testGeoJson);
		return sm;
	}
	
	public void setGeoFromString(Airmet am, String json) {
		Debug.println("setGeoFromString "+json);
		GeoJsonObject geo;	
		try {
			geo = airmetObjectMapper.readValue(json, GeoJsonObject.class);
///			am.setGeojson(geo);
			Debug.println("setGeoFromString ["+json+"] set");
			return;
		} catch (JsonParseException e) {
		} catch (JsonMappingException e) {
		} catch (IOException e) {
		}
		Debug.errprintln("setGeoFromString on ["+json+"] failed");
///		am.setGeojson(null);
	}
	
	public void validateAirmet (Airmet sm) throws Exception {
		Debug.println("Testing createAndCheckAirmet");
		Debug.println(sm.getValiddate().toString());
		assertThat(sm.getPhenomenon().toString(), is("ISOL_CB"));
	}
	
	@Test 
	public void createAndValidateAirmet () throws Exception {
		Airmet sm = createAirmet();
		validateAirmet(sm);
	}

	@Autowired
	AirmetStore testAirmetStore;
	
	public AirmetStore createNewStore() throws IOException {
		Tools.rmdir(airmetStoreLocation);
		Tools.mksubdirs(airmetStoreLocation);
		testAirmetStore.setLocation(airmetStoreLocation);
		Airmet[] airmets=testAirmetStore.getAirmets(false, AirmetStatus.concept);
		assertThat(airmets.length, is(0));
		return testAirmetStore;
	}
	
	@Test
	public void saveOneAirmet () throws Exception {
		AirmetStore store=createNewStore();
		Airmet sm = createAirmet();
		assertThat(store.getOM(),notNullValue());
		
		store.storeAirmet(sm);
		assertThat(store.getAirmets(false, AirmetStatus.concept).length, is(1));
	}
	
	@Test
	public void loadAndValidateAirmet () throws Exception {
		AirmetStore store=createNewStore();
		Airmet sm = createAirmet();
		assertThat(store.getOM(),notNullValue());
		store.storeAirmet(sm);
		
		Airmet[] sigmets=store.getAirmets(false, AirmetStatus.concept);
		assertThat(sigmets.length, is(1));
		validateAirmet(sigmets[0]);
	}
	
}
