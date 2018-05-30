package nl.knmi.geoweb.backend.product.sigmet.geo;


import org.geojson.Feature;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.PrecisionModel;
import org.locationtech.jts.io.ParseException;
import org.locationtech.jts.io.geojson.GeoJsonReader;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

public class GeoUtils {
	
  private static GeometryFactory gf;
  private static ObjectMapper om;
  private static GeoJsonReader reader;
  
  private static GeometryFactory getGeometryFactory() {
	  if (gf==null) {
		  gf=new GeometryFactory(new PrecisionModel(PrecisionModel.FLOATING));
	  }
      return gf;
  }
  
  private static GeoJsonReader getReader() {
	  if (reader==null) {
		  reader=new GeoJsonReader(GeoUtils.getGeometryFactory());
	  }
	  return reader;
  }
  
  private static ObjectMapper getObjectMapper() {
	  if (om==null) {
		  om=new ObjectMapper();
	  }
	  return om;
  }
  
  public static Geometry jsonFeature2jtsGeometry(Feature F)  {
	  try {
		  ObjectMapper om=getObjectMapper();
		  String json=om.writeValueAsString(F.getGeometry());
		  return getReader().read(json);
	  } catch(ParseException | JsonProcessingException e) {
          // TODO Auto-generated catch block
          e.printStackTrace();
      }
	  return null;
  }
  
  
}
