package nl.knmi.geoweb.backend.product.sigmet;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.time.Duration;

import org.geojson.Feature;
import org.geojson.FeatureCollection;
import org.geojson.GeoJsonObject;
import org.geojson.Geometry;
import org.geojson.LngLatAlt;
import org.geojson.Polygon;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.PrecisionModel;
import org.locationtech.jts.geom.impl.CoordinateArraySequenceFactory;
import org.locationtech.jts.io.ParseException;
import org.locationtech.jts.io.geojson.GeoJsonReader;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.Getter;
import lombok.Setter;
import nl.knmi.adaguc.tools.Debug;
import nl.knmi.adaguc.tools.Tools;
import nl.knmi.geoweb.backend.aviation.FIRStore;
import nl.knmi.geoweb.backend.product.GeoWebProduct;
import nl.knmi.geoweb.backend.product.IExportable;
import nl.knmi.geoweb.backend.product.ProductConverter;
@JsonInclude(Include.NON_NULL)
@Getter
@Setter
public class Sigmet implements GeoWebProduct, IExportable<Sigmet>{
	public static final Duration WSVALIDTIME = Duration.ofHours(4); //4*3600*1000;
	public static final Duration WVVALIDTIME = Duration.ofHours(6); //6*3600*1000;

	private GeoJsonObject geojson;
	private Phenomenon phenomenon;
	private ObsFc obs_or_forecast;
	@JsonFormat(shape = JsonFormat.Shape.STRING)
	private OffsetDateTime forecast_position_time;
	private SigmetLevel levelinfo;
	private SigmetMovement movement;
	private SigmetChange change;

	@JsonFormat(shape = JsonFormat.Shape.STRING)
	private OffsetDateTime issuedate;
	@JsonFormat(shape = JsonFormat.Shape.STRING)
	private OffsetDateTime validdate;
	@JsonFormat(shape = JsonFormat.Shape.STRING)
	private OffsetDateTime validdate_end;
	private String firname;
	private String location_indicator_icao;
	private String location_indicator_mwo;
	private String uuid;
	private SigmetStatus status;
	private int sequence;

	@JsonInclude(Include.NON_NULL)
	private Integer cancels;
	@JsonInclude(Include.NON_NULL)
	@JsonFormat(shape = JsonFormat.Shape.STRING)
	private OffsetDateTime cancelsStart;

	@JsonIgnore
	private Feature firFeature;

	@Getter
	public enum Phenomenon {
		OBSC_TS("OBSC TS", "Obscured Thunderstorms"),OBSC_TSGR("OBSC TSGR", "Obscured Thunderstorms with hail"),
		EMBD_TS("EMBD TS", "Embedded Thunderstorms"),EMBD_TSGR("EMBD TSGR", "Embedded Thunderstorms with hail"),
		FRQ_TS("FRQ TS", "Frequent Thunderstorms"),FRQ_TSGR("FRQ TSGR", "Frequent Thunderstorms with hail"),
		SQL_TS("SQL TS", "Squall line"),SQL_TSGR("SQL TSGR", "Squall line with hail"),
		SEV_TURB("SEV TURB", "Severe Turbulence"),
		SEV_ICE("SEV ICE", "Severe Icing"), SEV_ICE_FRZA("SEV ICE (FRZA)", "Severe Icing with Freezing Rain"),
		SEV_MTW("SEV MTW", "Severe Mountain Wave"),
		HVY_DS("HVY DS", "Heavy Duststorm"),HVY_SS("HVY SS", "Heavy Sandstorm"),
		RDOACT_CLD("RDOACT CLD", "Radioactive Cloud")
		;
		private String description;
		private String shortDescription;

		public static Phenomenon getRandomPhenomenon() {
			int i=(int)(Math.random()*Phenomenon.values().length);
			System.err.println("rand "+i+ " "+Phenomenon.values().length);
			return Phenomenon.valueOf(Phenomenon.values()[i].toString());
		}

		private Phenomenon(String shrt, String description) {
			this.shortDescription=shrt;
			this.description=description;
		}
		public static Phenomenon getPhenomenon(String desc) {
			for (Phenomenon phen: Phenomenon.values()) {
				if (desc.equals(phen.toString())){
					return phen;
				}
			}
			return null;
			//throw new Exception("You NOOB: Non existing pheonomenon!!!" + desc);
		}
	}
	@JsonInclude(Include.NON_NULL)
	@Getter
	public static class ObsFc {
		private boolean obs=true ;
		@JsonFormat(shape = JsonFormat.Shape.STRING)		
		OffsetDateTime obsFcTime;
		public ObsFc(){};
		public ObsFc(boolean obs){
			this.obs=obs;
			this.obsFcTime=null;
		}
		public ObsFc(boolean obs, OffsetDateTime obsTime) {
			this.obs=obs;
			this.obsFcTime=obsTime;
		}
		public String toTAC () {
			StringBuilder sb = new StringBuilder();

			if (this.obs) { 
				sb.append("OBS");
			} else {
				sb.append("FCST");
			}

			if (this.obsFcTime != null) {
				sb.append(" AT ").append(String.format("%02d", this.obsFcTime.getHour())).append(String.format("%02d", this.obsFcTime.getMinute())).append("Z");
			}

			return sb.toString();
		}
	}

	public enum SigmetLevelUnit {
		FT, FL, M;
	}
	public enum SigmetLevelMode {
		AT, ABV, BETW, BETW_SFC, TOPS, TOPS_ABV, TOPS_BLW;
	}

	//	public enum SigmetLevelOperator {
	//		TOP, TOP_ABV;
	//	}

	@JsonInclude(Include.NON_NULL)
	@Getter
	public static class SigmetLevelPart{
		int value;
		SigmetLevelUnit unit;
		public SigmetLevelPart(){};
		public SigmetLevelPart(SigmetLevelUnit unit, int val) {
			this.unit=unit;
			this.value=val;
		}

		public String toTAC() {
			if (this.unit==SigmetLevelUnit.FL) {
				return "FL"+value;
			}
			if (this.unit==SigmetLevelUnit.FT) {
				return value+"FT";
			}
			if (this.unit==SigmetLevelUnit.M) {
				return value+"M";
			}
			return "";
		}
	}

	@JsonInclude(Include.NON_NULL)
	@Getter
	public static class SigmetLevel {
		SigmetLevelPart[]levels;
		SigmetLevelMode mode;

		public SigmetLevel(){};
		public SigmetLevel(SigmetLevelPart lev1, SigmetLevelMode mode) {
			this.levels=new SigmetLevelPart[1];
			this.levels[0]=lev1;
			this.mode=mode;
		}
		public SigmetLevel(SigmetLevelPart lev1, SigmetLevelPart lev2, SigmetLevelMode mode) {
			levels=new SigmetLevelPart[2];
			this.levels[0]=lev1;
			this.levels[1]=lev2;
			this.mode=mode;
		}
		public String toTAC() {
			switch (this.mode) {
			case BETW:
				if ((this.levels[0] != null)  && (this.levels[1] != null)) {
					if (this.levels[0].getUnit().equals(this.levels[1].getUnit())&&this.levels[0].getUnit().equals(SigmetLevelUnit.FL)){
						return this.levels[0].toTAC() + "/" + this.levels[1].value;
					}
				}
				break;
			case BETW_SFC:
				if (this.levels[0] != null) {
					if (this.levels[1].unit == SigmetLevelUnit.M || this.levels[1].unit == SigmetLevelUnit.FT) {
						return "SFC/"+this.levels[1].value + this.levels[1].unit.toString().toUpperCase();
					} else 	if (this.levels[1].unit == SigmetLevelUnit.FL) {
						return "SFC/FL" + this.levels[1].value;
					}
				}
				break;
			case ABV:
				if (this.levels[0]!=null) {
					return "ABV "+this.levels[0].toTAC();
				}
				break;
			case AT:
				if (this.levels[0]!=null) {
					return ""+this.levels[0].toTAC();
				}
				break;
			case TOPS:
				if (this.levels[0]!=null) {
					return "TOPS "+this.levels[0].toTAC();
				}
				break;
			case TOPS_ABV:
				if (this.levels[0]!=null) {
					return "TOPS ABV "+this.levels[0].toTAC();
				}
				break;
			case TOPS_BLW:
				if (this.levels[0]!=null) {
					return "TOPS BLW "+this.levels[0].toTAC();
				}
				break;
			default:
			}
			return "";
		}
	}

	public enum SigmetDirection {
		N(0),NNE(22.5),NE(45),ENE(67.5),E(90),ESE(112.5),SE(135),SSE(157.5),S(180),SSW(202.5),SW(225),WSW(247.5),W(270),WNW(292.5),NW(315),NNW(337.5);
		public static SigmetDirection getSigmetDirection(String dir) {
			for (SigmetDirection v: SigmetDirection.values()) {
				if (dir.equals(v.toString())) return v;
			}
			return null;
		}
		private double dir;

		public double getDir() {
			return this.dir;
		}

		SigmetDirection(double dir) {
			this.dir=dir;
		}
	}

	@JsonInclude(Include.NON_NULL)
	@Getter
	public static class SigmetMovement {
		private Integer speed;
		private String speeduom;
		private SigmetDirection dir;
		private boolean stationary=true;
		public SigmetMovement(){};
		public SigmetMovement(boolean stationary) {
			this.stationary=stationary;
		}
		public SigmetMovement(String dir, int speed, String uoM) {
			this.stationary=false;
			this.speed=speed;
			this.speeduom=uoM;
			this.dir=SigmetDirection.getSigmetDirection(dir);
		}

		public String toTAC() {
			if (this.stationary == true) {
				return "STNR";	
			} else {
				if (this.speeduom==null) {
					return "MOV " + this.dir.toString() + " " + this.speed + "KT";
				} else {
					return "MOV " + this.dir.toString() + " " + this.speed + this.speeduom;
				}
			}
		}
	}

	@Getter
	public enum SigmetChange {
		INTSF("Intensifying"), WKN("Weakening"), NC("No change");
		private String description;
		private SigmetChange(String desc) {
			this.description=desc;
		}
		public String toTAC() {
			return Arrays.stream(values())
					.filter(sc -> sc.description.equalsIgnoreCase(this.description))
					.findFirst()
					.orElse(null).toString();
		}
	}

	@Getter
	public enum SigmetStatus {
		concept("concept"), canceled("canceled"), published("published"), test("test"); 
		private String status;
		private SigmetStatus (String status) {
			this.status = status;
		}
		public static SigmetStatus getSigmetStatus(String status){
			Debug.println("SIGMET status: " + status);

			for (SigmetStatus sstatus: SigmetStatus.values()) {
				if (status.equals(sstatus.toString())){
					return sstatus;
				}
			}
			return null;
		}

	}

	@Override
	public String toString() {
		ByteArrayOutputStream baos=new ByteArrayOutputStream();
		PrintStream ps=new PrintStream(baos);
		ps.println(String.format("Sigmet: %s %s %s [%s]", this.firname, location_indicator_icao, location_indicator_mwo, uuid));
		ps.println(String.format("seq: %d issued at %s valid from %s",sequence, this.issuedate, this.validdate));
		ps.println(String.format("change: %s geo: %s", this.change, this.geojson));
		return baos.toString();
	}

	public Sigmet() {
		this.sequence=-1;
	}

	public Sigmet(Sigmet otherSigmet) {
		this.firname=otherSigmet.getFirname();
		this.location_indicator_icao=otherSigmet.getLocation_indicator_icao();
		this.location_indicator_mwo=otherSigmet.getLocation_indicator_mwo();
		this.sequence=-1;
		this.phenomenon = otherSigmet.getPhenomenon();
		this.change = otherSigmet.getChange();
		this.geojson = otherSigmet.getGeojson();
		this.levelinfo = otherSigmet.getLevelinfo();
		this.movement = otherSigmet.getMovement();
		this.obs_or_forecast = otherSigmet.getObs_or_forecast();
		this.forecast_position_time = otherSigmet.getForecast_position_time();
		this.validdate = otherSigmet.getValiddate();
		this.validdate_end = otherSigmet.getValiddate_end();
		this.issuedate = otherSigmet.getIssuedate();
	}

	public Sigmet(String firname, String location, String issuing_mwo, String uuid) {
		this.firname=firname;
		this.location_indicator_icao=location;
		this.location_indicator_mwo=issuing_mwo;
		this.uuid=uuid;
		this.sequence=-1;
		this.phenomenon = null;
		// If a SIGMET is posted, this has no effect
		this.status=SigmetStatus.concept;
	}

	public static Sigmet getSigmetFromFile(ObjectMapper om, File f) throws JsonParseException, JsonMappingException, IOException {
		Sigmet sm=om.readValue(f, Sigmet.class);
		Debug.println("Sigmet from "+f.getName());
		//		Debug.println(sm.dumpSigmetGeometryInfo());
		return sm;
	}


	public void serializeSigmet(ObjectMapper om, String fn) {
		Debug.println("serializeSigmet to "+fn);		
		if(this.geojson == null || this.phenomenon == null) {
			throw new IllegalArgumentException("GeoJSON and Phenomenon are required");
		}
		// .... value from constructor is lost here, set it explicitly. (Why?)
		if(this.status == null) {
			this.status = SigmetStatus.concept;
		}
		try {
			om.writeValue(new File(fn), this);
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	public String serializeSigmetToString(ObjectMapper om) throws JsonProcessingException {
		return om.writeValueAsString(this);
	}

	public String convertLat(double lat) {
		String latDM = "";
		if (lat < 0) {
			latDM = "S";
			lat = Math.abs(lat);
		} else {
			latDM = "N";
		}
		int degrees = (int)Math.floor(lat);
		latDM += String.format("%02d", degrees);
		double fracPart = lat - degrees;
		int minutes = (int)Math.floor(fracPart * 60.0);
		latDM += String.format("%02d", minutes);
		return latDM;
	}

	public String convertLon(double lon) {
		String lonDM = "";
		if (lon < 0) {
			lonDM = "W";
			lon = Math.abs(lon);
		} else {
			lonDM = "E";
		}
		int degreesLon = (int)Math.floor(lon);
		lonDM += String.format("%03d", degreesLon);
		double fracPartLon = lon - degreesLon;
		int minutesLon = (int)Math.floor(fracPartLon * 60.0);
		lonDM += String.format("%02d", minutesLon);
		return lonDM;
	}

	public String pointToDMSString(LngLatAlt lnglat) {
		double lon = lnglat.getLongitude();
		double lat = lnglat.getLatitude();

		return this.convertLat(lat) + " " + this.convertLon(lon);
	}

	public String latlonToDMS(List<LngLatAlt> coords) {
		return coords.stream().map(lnglat -> this.pointToDMSString(lnglat)).collect(Collectors.joining(" - "));
	}

	public String lineToTAC(LineString intersectionLine, org.locationtech.jts.geom.Geometry box) {
		// TODO: Might only work if all points are in the same octant of earth?
		double _minX = Double.MAX_VALUE, _maxX = Double.MIN_VALUE, _minY = Double.MAX_VALUE, _maxY = Double.MIN_VALUE;
		for (org.locationtech.jts.geom.Coordinate coord : box.getCoordinates()) {
			_minX = Math.min(coord.x, _minX);
			_maxX = Math.max(coord.x, _maxX);
			_minY = Math.min(coord.y, _minY);
			_maxY = Math.max(coord.y, _maxY);
		}
		final double minY = _minY;
		final double minX = _minX;
		final double maxX = _maxX;
		final double maxY = _maxY;

		if (Arrays.stream(intersectionLine.getCoordinates()).allMatch(point -> point.y == minY)) {
			// South line intersects - so north of intersection line
			return "N OF " + this.convertLat(minY);
		} 
		if (Arrays.stream(intersectionLine.getCoordinates()).allMatch(point -> point.y == maxY)) {
			// North line intersects - so south of intersection line
			return "S OF " + this.convertLat(maxY);
		}
		if (Arrays.stream(intersectionLine.getCoordinates()).allMatch(point -> point.x == maxX)) {
			// East line intersects - so west of intersection line
			return "E OF " + this.convertLon(maxX);
		}
		if (Arrays.stream(intersectionLine.getCoordinates()).allMatch(point -> point.x == minX)) {
			// West line intersects - so east of intersection line
			return "W OF " + this.convertLon(minX);
		}
		return "";
	}

	public String featureToTAC(Feature f, Feature FIR) {
		List<LngLatAlt> coords;
		switch(f.getProperty("selectionType").toString().toLowerCase()) {
		case "poly":
			// This assumes that one feature contains one set of coordinates
			coords = ((Polygon)(f.getGeometry())).getCoordinates().get(0);
			return "WI " + this.latlonToDMS(coords);
		case "fir":
			return "ENTIRE FIR";
		case "point":
			coords = ((Polygon)(f.getGeometry())).getCoordinates().get(0);
			return this.pointToDMSString(coords.get(0));
		case "box":
			// A box is drawn which can mean multiple things whether how many intersections there are.
			// If one line segment intersects, the phenomenon happens in the area opposite of the line intersection
			// e.g. if the south border of the box intersects, the phenomenon happens north of this line.
			// If there are multiple intersections -- we assume two currently -- the phenomenon happens in the quadrant
			// opposite of the intersection lines. 
			// E.g. the south and west border of the box intersect, the phenomenon happens north of the south intersection line and east of the west intersection line
			GeometryFactory gf=new GeometryFactory(new PrecisionModel(PrecisionModel.FLOATING));
			GeoJsonReader reader=new GeoJsonReader(gf);

			try {
				ObjectMapper om = new ObjectMapper();
				String FIRs=om.writeValueAsString(FIR.getGeometry()); //FIR as String

				org.locationtech.jts.geom.Geometry jtsGeometry = reader.read(om.writeValueAsString(f.getGeometry()));
				org.locationtech.jts.geom.Geometry geom_fir=reader.read(FIRs);

				// Intersect the box with the FIR
				org.locationtech.jts.geom.Geometry intersection = jtsGeometry.intersection(geom_fir);
				CoordinateArraySequenceFactory caf=CoordinateArraySequenceFactory.instance();
				for (int i=0; i<4; i++) {
					LineString side=new LineString(caf.create(Arrays.copyOfRange(jtsGeometry.getCoordinates(), i, i+2)), gf);
					if (geom_fir.intersects(side)) {
						Debug.println("Intersecting on side "+i);
						Debug.println("I:"+side.intersection(geom_fir));
					}
				}

				// One line segment so encode that
				if (intersection.getClass().equals(org.locationtech.jts.geom.LineString.class)) {
					// single intersect
					org.locationtech.jts.geom.LineString intersectionLine = (org.locationtech.jts.geom.LineString)intersection;
					return this.lineToTAC(intersectionLine, jtsGeometry);
				} else if (intersection.getClass().equals(org.locationtech.jts.geom.MultiLineString.class)) {
					// Multiple intersects -- e.g. north east
					// Assert that they are encoded in the <North/South> - <East/West> order
					org.locationtech.jts.geom.MultiLineString intersectionLines = (org.locationtech.jts.geom.MultiLineString)intersection;
					List<LineString> asList = Arrays.asList((LineString)intersectionLines.getGeometryN(0), (LineString)intersectionLines.getGeometryN(1));
					if (asList.get(0).getCoordinateN(0).y != asList.get(0).getCoordinateN(1).y) {
						Collections.reverse(asList);
					}
					return asList.stream().map(line -> this.lineToTAC(line, jtsGeometry)).collect(Collectors.joining(" AND "));
				}
			} catch (ParseException | JsonProcessingException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		default:
			return "";
		}
	}

	public List<String> createLocationTAC(FeatureCollection fc, Feature FIR) {
		return fc.getFeatures().stream().map(feature -> this.featureToTAC(feature, FIR)).collect(Collectors.toList());
	}

	public String toTAC(Feature FIR) {
		GeoJsonObject startGeometry = this.findStartGeometry() ; //findStartGeometry();
		if (!((Feature)startGeometry).getProperty("selectionType").equals("box")) {
			startGeometry = this.extractSingleStartGeometry();
		}
		StringBuilder sb = new StringBuilder();
		String validdateFormatted = String.format("%02d", this.validdate.getDayOfMonth()) + String.format("%02d", this.validdate.getHour()) + String.format("%02d", this.validdate.getMinute());
		String validdateEndFormatted = String.format("%02d", this.validdate_end.getDayOfMonth()) + String.format("%02d", this.validdate_end.getHour()) + String.format("%02d", this.validdate_end.getMinute());

		sb.append(this.location_indicator_icao).append(" SIGMET ").append(this.sequence).append(" VALID ").append(validdateFormatted).append('/').append(validdateEndFormatted).append(' ').append(this.location_indicator_mwo).append('-');
		sb.append('\n');
		sb.append(this.location_indicator_icao).append(' ').append(this.firname);
		if (this.cancels != null && this.cancelsStart != null) {
			String validdateCancelled = String.format("%02d", this.cancelsStart.getDayOfMonth()) + String.format("%02d", this.cancelsStart.getHour()) + String.format("%02d", this.cancelsStart.getMinute());

			sb.append("CNL SIGMET ").append(this.cancels).append(" ").append(validdateCancelled).append('/').append(validdateEndFormatted);
			return sb.toString();	
		}
		sb.append('\n');
		sb.append(this.phenomenon.getShortDescription());
		sb.append('\n');
		sb.append(this.obs_or_forecast.toTAC());
		sb.append('\n');
		sb.append(this.featureToTAC((Feature)startGeometry, FIR));
		sb.append('\n');
		sb.append(this.levelinfo.toTAC());
		sb.append('\n');
		if (this.movement != null && this.forecast_position_time == null) {
			sb.append(this.movement.toTAC());
			sb.append('\n');
		}
		if (this.change!=null) {
			sb.append(this.change.toTAC());
			sb.append('\n');
		}
		if (this.movement != null && this.movement.stationary == false && this.forecast_position_time != null) {
			sb.append("FCST AT ").append(String.format("%02d", this.forecast_position_time.getHour())).append(String.format("%02d", this.forecast_position_time.getMinute())).append("Z");
			sb.append('\n');
			sb.append(this.featureToTAC((Feature)this.findEndGeometry(((Feature)startGeometry).getId()), FIR));
		}
		return sb.toString();
	}

	private static String START="start";
	private static String END="end";
	private static String INTERSECTION="intersection";

	public List<GeoJsonObject> findIntersectableGeometries() {
		List<GeoJsonObject>objs=new ArrayList<GeoJsonObject>();
		FeatureCollection fc=(FeatureCollection)this.geojson;
		for (Feature f: fc.getFeatures()) {
			if ((f.getProperty("featureFunction")!=null)&&(f.getProperty("featureFunction").equals(START)||f.getProperty("featureFunction").equals(END))){
				objs.add(f);
			}
		}
		return objs;
	}

	public List<GeoJsonObject> findEndGeometries() {
		List<GeoJsonObject>objs=new ArrayList<GeoJsonObject>();
		FeatureCollection fc=(FeatureCollection)this.geojson;
		for (Feature f: fc.getFeatures()) {
			if ((f.getProperty("featureFunction")!=null)&&f.getProperty("featureFunction").equals(END)){
				objs.add(f);
			}
		}
		return objs;
	}

	public GeoJsonObject findEndGeometry(String relatesTo) {
		FeatureCollection fc=(FeatureCollection)this.geojson;
		for (Feature f: fc.getFeatures()) {
			if ((f.getProperty("featureFunction")!=null)&&f.getProperty("featureFunction").equals(END)){
				if ((f.getProperty("relatesTo")!=null)&&f.getProperty("relatesTo").equals(relatesTo)) {
					return f;
				}
			}
		}
		return null;
	}

	public GeoJsonObject findStartGeometry() {
		FeatureCollection fc=(FeatureCollection)this.geojson;
		for (Feature f: fc.getFeatures()) {
			if ((f.getProperty("featureFunction")!=null)&&f.getProperty("featureFunction").equals(START)){
				return f;
			}
		}
		return null;
	}

	public GeoJsonObject extractSingleStartGeometryORG() {
		FeatureCollection fc=(FeatureCollection)this.geojson;
		for (Feature f: fc.getFeatures()) {
			if ((f.getProperty("featureFunction")!=null)&&f.getProperty("featureFunction").equals(START)){
				return f;
			}
		}
		return null;
	}

	public GeoJsonObject extractSingleStartGeometry() {
		FeatureCollection fc=(FeatureCollection)this.geojson;
		for (Feature f: fc.getFeatures()) {
			if ((f.getProperty("featureFunction")!=null)&&f.getProperty("featureFunction").equals(START)){
				for (Feature f2: fc.getFeatures()) {
					if ((f2.getProperty("featureFunction")!=null)&&f2.getProperty("featureFunction").equals(INTERSECTION)&&f.getId().equals(f2.getProperty("relatesTo"))){
						return f2;
					}
				}
				return f;
			}
		}
		return null;
	}

	public GeoJsonObject extractSingleEndGeometry() {
		FeatureCollection fc=(FeatureCollection)this.geojson;
		for (Feature f: fc.getFeatures()) {
			if ((f.getProperty("featureFunction")!=null)&&f.getProperty("featureFunction").equals(END)){
				for (Feature f2: fc.getFeatures()) {
					if ((f2.getProperty("featureFunction")!=null)&&f2.getProperty("featureFunction").equals(INTERSECTION)&&f.getId().equals(f2.getProperty("relatesTo"))){
						return f2;
					}
				}
				return f;
			}
		}
		return null;
	}

	public void putIntersectionGeometry(String relatesTo, Feature intersection) {
		FeatureCollection fc=(FeatureCollection)this.geojson;

		//Remove old intersection for id if it exists
		List<Feature> toremove=new ArrayList<Feature>();
		for (Feature f: fc.getFeatures()) {
			if ((f.getProperty("relatesTo")!=null)&&f.getProperty("relatesTo").equals(relatesTo)){
				if ((f.getProperty("featureFunction")!=null)&&f.getProperty("featureFunction").equals(INTERSECTION)){
					toremove.add(f);
				}
			}
		}
		if (!toremove.isEmpty()) {
			fc.getFeatures().removeAll(toremove);
		}
		//Add intersection
		//		intersection.setId(UUID.randomUUID().toString());
		intersection.setId(relatesTo+"-i");
		intersection.getProperties().put("relatesTo", relatesTo);
		intersection.getProperties().put("featureFunction", INTERSECTION);
		fc.getFeatures().add(intersection);
	}

	public void putEndGeometry(String relatesTo, Feature newFeature) {
		FeatureCollection fc=(FeatureCollection)this.geojson;

		//Remove old endGeometry for id if it exists
		List<Feature> toremove=new ArrayList<Feature>();
		for (Feature f: fc.getFeatures()) {
			if ((f.getProperty("relatesTo")!=null)&&f.getProperty("relatesTo").equals(relatesTo)){
				if ((f.getProperty("featureFunction")!=null)&&f.getProperty("featureFunction").equals(END)){
					toremove.add(f);
				}
			}
		}

		if (!toremove.isEmpty()) {
			fc.getFeatures().removeAll(toremove);
		}
		//Add intersection
		//		newFeature.setId(UUID.randomUUID().toString());
		newFeature.getProperties().put("relatesTo", relatesTo);
		newFeature.getProperties().put("featureFunction", END);
		fc.getFeatures().add(newFeature);
	}

	public List<String>fetchGeometryIds() {
		List<String>ids=new ArrayList<String>();
		FeatureCollection fc=(FeatureCollection)this.geojson;
		for (Feature f: fc.getFeatures()) {
			if ((f.getId()!=null)) {
				ids.add(f.getId());
			}else {
				ids.add("null");
			}
		}
		return ids;
	}

	public void putStartGeometry(Feature newFeature) {
		FeatureCollection fc=(FeatureCollection)this.geojson;

		//Add intersection
		newFeature.getProperties().put("featureFunction", START);
		fc.getFeatures().add(newFeature);
	}

	public String dumpSigmetGeometryInfo() {
		StringWriter sw=new StringWriter();
		PrintWriter pw=new PrintWriter(sw);
		pw.println("SIGMET ");
		FeatureCollection fc=(FeatureCollection)this.geojson;
		for (Feature f: fc.getFeatures()) {
			pw.print((f.getId()==null)?"  ":f.getId());
			pw.print(" ");
			pw.print((f.getProperty("featureFunction")==null)?"  ":f.getProperty("featureFunction").toString());
			pw.print(" ");
			pw.print((f.getProperty("selectionType")==null)?"  ":f.getProperty("selectionType").toString());
			pw.print(" ");
			pw.print((f.getProperty("relatesTo")==null)?"  ":f.getProperty("relatesTo").toString());
			pw.println();
		}
		return sw.toString();
	}

	public String toJSON(ObjectMapper om) throws JsonProcessingException {
		return om.writerWithDefaultPrettyPrinter().writeValueAsString(this);
	}

	@Override
	public void export(File path, ProductConverter<Sigmet> converter, ObjectMapper om) {
//		String s=converter.ToIWXXM_2_1(this);
		try {
			String time = OffsetDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
			String validTime = this.getValiddate().format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HHmm"));
			String name = "SIGMET_" + this.getLocation_indicator_icao() + "_" + validTime + "_" + time;
			Tools.writeFile(path.getPath() + "/" + name + ".tac", this.toTAC(this.getFirFeature()));
			Tools.writeFile(path.getPath() + "/" + name + ".json", this.toJSON(om));
			String iwxxmName="A_"+"WSNL99"+this.getLocation_indicator_icao()+this.getValiddate().format(DateTimeFormatter.ofPattern("ddHHmm"));
			iwxxmName+="_C_"+this.getLocation_indicator_icao()+"_"+time;
			String s=converter.ToIWXXM_2_1(this);
			Tools.writeFile(path.getPath() + "/" + iwxxmName + ".xml", s);
		} catch (IOException e) {
		}
	}
}
