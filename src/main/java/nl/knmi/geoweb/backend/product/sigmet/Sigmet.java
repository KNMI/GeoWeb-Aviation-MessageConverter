package nl.knmi.geoweb.backend.product.sigmet;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.Duration;

import org.geojson.Feature;
import org.geojson.FeatureCollection;
import org.geojson.GeoJsonObject;
import org.geojson.LngLatAlt;
import org.geojson.Polygon;
import org.geojson.Point;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Envelope;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.PrecisionModel;
import org.locationtech.jts.geom.impl.CoordinateArraySequenceFactory;
import org.locationtech.jts.io.ParseException;
import org.locationtech.jts.io.geojson.GeoJsonReader;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonTypeInfo.As;
import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

import lombok.Getter;
import lombok.Setter;
import nl.knmi.adaguc.tools.Debug;
import nl.knmi.adaguc.tools.Tools;
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
	//	@JsonFormat(shape = JsonFormat.Shape.STRING)
	//	private OffsetDateTime forecast_position_time;
	private SigmetLevel levelinfo;
	private SigmetMovementType movement_type;
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
	private SigmetType type;
	private int sequence;

	@JsonInclude(Include.NON_NULL)
	private Integer cancels;
	@JsonInclude(Include.NON_NULL)
	@JsonFormat(shape = JsonFormat.Shape.STRING)
	private OffsetDateTime cancelsStart;

	@JsonInclude(Include.NON_NULL)
	@JsonDeserialize(as = VAExtraFields.class)
	private VAExtraFields va_extra_fields;

	@JsonInclude(Include.NON_NULL)
	@JsonDeserialize(as = TCExtraFields.class)
	private TCExtraFields tc_extra_fields;

	@JsonIgnore
	private Feature firFeature;

	@Getter
	public static class VAExtraFields {
		/* https://www.icao.int/APAC/Documents/edocs/WV-SIGMET.pdf */
		// TODO: Add TAC for CANCEL move_to
		// TODO TAC should be truncated on 69 characters.
		public Volcano volcano;
		boolean no_va_expected;
		List <String> move_to;
		@Getter
		public static class Volcano {
			String name;
			List <Number> position;
			public String toTAC() {
				String volcanoName = (this.name != null && this.name.length() > 0) ? " MT " + this.name : "";
				String location = "";
				try {
					location = (position != null && position.size() == 2) ?
							" PSN " + Sigmet.convertLat(position.get(0).doubleValue()) 
							+" " + Sigmet.convertLon(position.get(1).doubleValue()) :
								"";
				}catch(Exception e){
					Debug.printStackTrace(e);
				}
				return  ((volcanoName .length() >0 || location.length() > 0) ? "VA ERUPTION" : "") + 
						volcanoName +  
						location +
						((volcanoName .length() >0 || location.length() > 0) ? " " : "");
			}
		}
		public String toTAC () {
			if (volcano != null ) {
				return volcano.toTAC();
			}
			return "";
		}
	}

	@Getter
	public static class TCExtraFields {
		TropicalCyclone tropical_cyclone;
		@Getter
		public static class TropicalCyclone {
			String name;
		}
	}

	@Getter
	public enum Phenomenon {
		OBSC_TS("OBSC TS", "Obscured Thunderstorms"),OBSC_TSGR("OBSC TSGR", "Obscured Thunderstorms with hail"),
		EMBD_TS("EMBD TS", "Embedded Thunderstorms"),EMBD_TSGR("EMBD TSGR", "Embedded Thunderstorms with hail"),
		FRQ_TS("FRQ TS", "Frequent Thunderstorms"),FRQ_TSGR("FRQ TSGR", "Frequent Thunderstorms with hail"),
		SQL_TS("SQL TS", "Squall line"),SQL_TSGR("SQL TSGR", "Squall line with hail"),
		SEV_TURB("SEV TURB", "Severe Turbulence"),
		SEV_ICE("SEV ICE", "Severe Icing"), SEV_ICE_FZRA("SEV ICE (FZRA)", "Severe Icing with Freezing Rain"),
		SEV_MTW("SEV MTW", "Severe Mountain Wave"),
		HVY_DS("HVY DS", "Heavy Duststorm"),HVY_SS("HVY SS", "Heavy Sandstorm"),
		RDOACT_CLD("RDOACT CLD", "Radioactive Cloud"),
		VA_CLD("VA CLD", "Volcanic Ash Cloud"), /* https://www.icao.int/APAC/Documents/edocs/sigmet_guide6.pdf, 3.2 Sigmet phenomena */
		TROPICAL_CYCLONE("TC", "Tropical Cyclone");

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

	public enum SigmetMovementType {
		STATIONARY, MOVEMENT, FORECAST_POSITION;
	}

	//	public enum SigmetLevelOperator {
	//		TOP, TOP_ABV;
	//	}

	@JsonInclude(Include.NON_NULL)
	@Getter
	public static class SigmetLevelPart{
		Integer value;
		SigmetLevelUnit unit;
		public SigmetLevelPart(){};
		public SigmetLevelPart(SigmetLevelUnit unit, int val) {
			this.unit=unit;
			this.value=val;
		}

		public String toTACValue() {
			if (value==null) return "";
			if (this.unit==SigmetLevelUnit.FL) {
				return String.format("%03d", value);
			}
			if (this.unit==SigmetLevelUnit.FT) {
				if (value>9999) {
					return String.format("%05d", value);
				} else {
					return String.format("%04d", value);
				}
			}
			if (this.unit==SigmetLevelUnit.M) {
				if (value<=9999) {
					return String.format("%04d", value);
				}
			}
			return "";
		}

		public String toTAC() {
			if (value==null) return "";
			if (this.unit==SigmetLevelUnit.FL) {
				return "FL"+this.toTACValue();
			}
			if (this.unit==SigmetLevelUnit.FT) {
				return this.toTACValue()+"FT";
			}
			if (this.unit==SigmetLevelUnit.M) {
				return this.toTACValue() + "M";
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
					if (this.levels[0].getUnit().equals(this.levels[1].getUnit())) {
						switch (this.levels[0].getUnit()) {
							case FL:
								return this.levels[0].toTAC() + "/" + this.levels[1].toTACValue();
							case FT:
								return this.levels[0].toTACValue() + "/" + this.levels[1].toTAC();
							case M:
								return this.levels[0].toTACValue() + "/" + this.levels[1].toTAC();
						}
						return "";
					} else {
						return this.levels[0].toTAC() + "/" + this.levels[1].toTAC();
					}
				}
				break;
			case BETW_SFC:
				if (this.levels[1] != null) {
					return "SFC/"+this.levels[1].toTAC();
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
					return "TOP "+this.levels[0].toTAC();
				}
				break;
			case TOPS_ABV:
				if (this.levels[0]!=null) {
					return "TOP ABV "+this.levels[0].toTAC();
				}
				break;
			case TOPS_BLW:
				if (this.levels[0]!=null) {
					return "TOP BLW "+this.levels[0].toTAC();
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
		public SigmetMovement(){};
		public SigmetMovement(String dir, int speed, String uoM) {
			this.speed=speed;
			this.speeduom=uoM;
			this.dir=SigmetDirection.getSigmetDirection(dir);
		}

		public String getSpeeduom() {
			if (this.speeduom==null) {
				return "KT";
			} else {
				return speeduom;
			}
		}

		public String toTAC() {
			if ((this.dir!=null)&&(this.speed!=null)) {
				if (this.speeduom==null) {
					return "MOV " + this.dir.toString() + " " + this.speed + "KT";
				} else {
					return "MOV " + this.dir.toString() + " " + this.speed + this.speeduom;
				}
			}
			return "";
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
		concept("concept"), canceled("canceled"), published("published");//, test("test"); TODO: Check, should be in Type now.
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

	@Getter
	public enum SigmetType {
		normal("normal"), test("test"), exercise("exercise");
		private String type;
		private SigmetType (String type) {
			this.type = type;
		}
		public static SigmetType getSigmetType(String itype){
			for (SigmetType stype: SigmetType.values()) {
				if (itype.equals(stype.toString())){
					return stype;
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
		this.movement_type = otherSigmet.getMovement_type();
		//		this.forecast_position_time = otherSigmet.getForecast_position_time();
		this.validdate = otherSigmet.getValiddate();
		this.validdate_end = otherSigmet.getValiddate_end();
		this.issuedate = otherSigmet.getIssuedate();
		this.firFeature = otherSigmet.firFeature;
		this.type=otherSigmet.type;
		this.va_extra_fields = otherSigmet.va_extra_fields;
		this.tc_extra_fields = otherSigmet.tc_extra_fields;
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
		this.type=SigmetType.test;
	}

	public static Sigmet getSigmetFromFile(ObjectMapper om, File f) throws JsonParseException, JsonMappingException, IOException {
		Sigmet sm=om.readValue(f, Sigmet.class);
		//		Debug.println("Sigmet from "+f.getName());
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
		if(this.type == null) {
			this.type = SigmetType.test;
		}
		try {
			om.writeValue(new File(fn), this);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	public String serializeSigmetToString(ObjectMapper om) throws JsonProcessingException {
		return om.writeValueAsString(this);
	}

	public static String convertLat(double lat) {
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

	public static String convertLon(double lon) {
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

		return Sigmet.convertLat(lat) + " " + Sigmet.convertLon(lon);
	}

	public String pointToDMSString(Coordinate coord) {
		double lon = coord.getOrdinate(Coordinate.X);
		double lat = coord.getOrdinate(Coordinate.Y);

		return Sigmet.convertLat(lat) + " " + Sigmet.convertLon(lon);
	}

	public String latlonToDMS(List<LngLatAlt> coords) {
		return coords.stream().map(lnglat -> this.pointToDMSString(lnglat)).collect(Collectors.joining(" - "));
	}

	public String latlonToDMS(Coordinate[] coords) {
		Arrays.stream(coords);
		return Arrays.stream(coords).map(coord -> this.pointToDMSString(coord)).collect(Collectors.joining(" - "));
	}

	public String featureToTAC(Feature f, Feature FIR) {
		List<LngLatAlt> coords;

		if (f==null) return " ERR ";

		switch(f.getProperty("selectionType").toString().toLowerCase()) {
		case "poly":
			// This assumes that one feature contains one set of coordinates
			coords = ((Polygon)(f.getGeometry())).getCoordinates().get(0);
			return "WI " + this.latlonToDMS(coords);
		case "fir":
			return "ENTIRE FIR";
		case "point":
			Point p=(Point)f.getGeometry();
			return this.pointToDMSString(p.getCoordinates());
		case "box":
			// A box is drawn which can mean multiple things whether how many intersections there are.
			// If one line segment intersects, the phenomenon happens in the area opposite of the line intersection
			// e.g. if the south border of the box intersects, the phenomenon happens north of this line.
			// If there are multiple intersections -- we assume two currently -- the phenomenon happens in the quadrant
			// opposite of the intersection lines.
			// E.g. the south and west border of the box intersect, the phenomenon happens north of the south intersection line and east of the west intersection line
			GeometryFactory gf=new GeometryFactory(new PrecisionModel(PrecisionModel.FLOATING));
			GeoJsonReader reader=new GeoJsonReader(gf);

			if (FIR.getGeometry()==null) {
			    Debug.println("FIR is null!!");
			    return "";
            }
			try {
				ObjectMapper om = new ObjectMapper();
				String FIRs=om.writeValueAsString(FIR.getGeometry()); //FIR as String

                try {
                    org.locationtech.jts.geom.Geometry drawnGeometry = reader.read(om.writeValueAsString(f.getGeometry()));

                    org.locationtech.jts.geom.Geometry geom_fir = reader.read(FIRs);

                    //Sort box's coordinates
                    Envelope env = drawnGeometry.getEnvelopeInternal();
                    double minX = env.getMinX();
                    double maxX = env.getMaxX();
                    double minY = env.getMinY();
                    double maxY = env.getMaxY();
                    //				Debug.println("BBOX (++);: "+minX+"-"+maxX+","+minY+"-"+maxY);

                    if ((minX==maxX)||(minY==maxY)) return " POINT "; //Box is one point!!

                    org.locationtech.jts.geom.Geometry firBorder = geom_fir.getBoundary();

                    //Find intersections with box's sides
                    CoordinateArraySequenceFactory caf = CoordinateArraySequenceFactory.instance();
                    boolean[] boxSidesIntersecting = new boolean[4];
                    int boxSidesIntersectingCount = 0;

                    //Sort the rectangle points counterclockwise, starting at lower left
                    Coordinate[] drawnCoords = new Coordinate[5];
                    for (int i = 0; i < 4; i++) {
                        if (drawnGeometry.getCoordinates()[i].x == minX) {
                            if (drawnGeometry.getCoordinates()[i].y == minY) {
                                drawnCoords[0] = drawnGeometry.getCoordinates()[i];
                            } else {
                                drawnCoords[3] = drawnGeometry.getCoordinates()[i];
                            }
                        } else {
                            if (drawnGeometry.getCoordinates()[i].y == minY) {
                                drawnCoords[1] = drawnGeometry.getCoordinates()[i];
                            } else {
                                drawnCoords[2] = drawnGeometry.getCoordinates()[i];
                            }
                        }
                    }
                    drawnCoords[4] = drawnCoords[0]; //Copy first point to last
                    Debug.println("drawnCoords: "+drawnCoords[0]+" "
                            +drawnCoords[1]+" "+drawnCoords[2]+" "+drawnCoords[3]+" "+drawnCoords[4]);

                    for (int i = 0; i < 4; i++) {
                        LineString side = new LineString(caf.create(Arrays.copyOfRange(drawnCoords, i, i + 2)), gf);
                        if (side==null) return " ERR (side) ";
                        if (geom_fir==null) return " ERR (geom_fir) ";
                        if (side.intersects(geom_fir)) { //TODO or: firBorder
                            boxSidesIntersecting[i] = true;
                            boxSidesIntersectingCount++;
                            //						Debug.println("Intersecting on side "+i);
                            //						Debug.println("I:"+side.intersection(geom_fir));
                        } else {
                            boxSidesIntersecting[i] = false;
                        }
                    }

                    if (boxSidesIntersectingCount == 1) {
                        Debug.println("Intersecting box on 1 side");
                        if (boxSidesIntersecting[0]) {
                            //N of
                            return String.format("N OF %s", convertLat(minY));
                        } else if (boxSidesIntersecting[1]) {
                            //W of
                            return String.format("W OF %s", convertLon(maxX));
                        } else if (boxSidesIntersecting[2]) {
                            //S of
                            return String.format("S OF %s", convertLat(maxY));
                        } else if (boxSidesIntersecting[3]) {
                            //E of
                            return String.format("E OF %s", convertLon(minX));
                        }
                    } else if (boxSidesIntersectingCount == 2) {
                        Debug.println("Intersecting box on 2 sides");
                        if (boxSidesIntersecting[0] && boxSidesIntersecting[1]) {
                            //N of and W of
                            return String.format("N OF %s AND W OF %s", convertLat(minY), convertLon(maxX));
                        } else if (boxSidesIntersecting[1] && boxSidesIntersecting[2]) {
                            //S of and W of
                            return String.format("S OF %s AND W OF %s", convertLat(maxY), convertLon(maxX));
                        } else if (boxSidesIntersecting[2] && boxSidesIntersecting[3]) {
                            //S of and E of
                            return String.format("S OF %s AND E OF %s", convertLat(maxY), convertLon(minX));
                        } else if (boxSidesIntersecting[3] && boxSidesIntersecting[0]) {
                            //N of and E of
                            return String.format("N OF %s AND E OF %s", convertLat(minY), convertLon(minX));
                        } else if (boxSidesIntersecting[0] && boxSidesIntersecting[2]) {
                            //N of and S of
                            return String.format("N OF %s AND S OF %s", convertLat(minY), convertLat(maxY));
                        } else if (boxSidesIntersecting[1] && boxSidesIntersecting[3]) {
                            //E of  and W of
                            return String.format("E OF %s AND W OF %s", convertLon(minX), convertLon(maxX));
                        }
                    } else if (boxSidesIntersectingCount == 3) {
                        Debug.println("Intersecting box on 3 sides");
                    } else if (boxSidesIntersectingCount == 4) {
                        Debug.println("Intersecting box on 4 sides");
                    }

                    // Intersect the box with the FIR
                    org.locationtech.jts.geom.Geometry intersection = drawnGeometry.intersection(geom_fir);

                    //				Debug.println("intersection: "+intersection);

                    if (intersection.equalsTopo(geom_fir)) {
                        return "ENTIRE FIR";
                    }

                    Coordinate[] drawn = drawnGeometry.getCoordinates();
                    Coordinate[] intersected = intersection.getCoordinates();
                    coords = ((Polygon) (f.getGeometry())).getCoordinates().get(0);
                    //				Debug.println("SIZES: "+drawn.length+"  "+intersected.length);
                    if (intersected.length > 7) {
                        Debug.println("More than 7 in intersection!!");
                        return "WI " + this.latlonToDMS(drawn);
                    }
                    return "WI " + this.latlonToDMS(intersected);
                } catch (ParseException pe) {
                   //pe.printStackTrace();
                }
			} catch (JsonProcessingException e) {
				e.printStackTrace();
			}
			return " ERR ";
		default:
			return "";
		}
	}

	public String toTAC() {
		if (this.firFeature!=null) {
			return this.toTAC(this.firFeature);
		}
		return "";
	}

	public String toTAC(Feature FIR) {
		GeoJsonObject effectiveStartGeometry = this.findStartGeometry() ; //findStartGeometry();
		if ((effectiveStartGeometry==null)||(((Feature)effectiveStartGeometry).getProperty("selectionType")==null)) {
			return "Missing geometry";
		}
		if (!((Feature)effectiveStartGeometry).getProperty("selectionType").equals("box")&&
				!((Feature)effectiveStartGeometry).getProperty("selectionType").equals("fir")&&
				!((Feature)effectiveStartGeometry).getProperty("selectionType").equals("point")) {
			GeoJsonObject intersected=this.extractSingleStartGeometry();
			int sz=((Polygon)((Feature)intersected).getGeometry()).getCoordinates().get(0).size();
			if (sz<=7)  {
				effectiveStartGeometry = intersected; // Use intersection result
			}
		}
		StringBuilder sb = new StringBuilder();
		String validdateFormatted = String.format("%02d", this.validdate.getDayOfMonth()) + String.format("%02d", this.validdate.getHour()) + String.format("%02d", this.validdate.getMinute());
		String validdateEndFormatted = String.format("%02d", this.validdate_end.getDayOfMonth()) + String.format("%02d", this.validdate_end.getHour()) + String.format("%02d", this.validdate_end.getMinute());



		sb.append(this.location_indicator_icao).append(" SIGMET ").append(this.sequence).append(" VALID ").append(validdateFormatted).append('/').append(validdateEndFormatted).append(' ').append(this.location_indicator_mwo).append('-');
		sb.append('\n');

		sb.append(this.location_indicator_icao).append(' ').append(this.firname);

	
		if (this.cancels != null && this.cancelsStart != null) {
			String validdateCancelled = String.format("%02d", this.cancelsStart.getDayOfMonth()) + String.format("%02d", this.cancelsStart.getHour()) + String.format("%02d", this.cancelsStart.getMinute());

			sb.append(' ').append("CNL SIGMET ").append(this.cancels).append(" ").append(validdateCancelled).append('/').append(validdateEndFormatted);
			if (va_extra_fields != null && va_extra_fields.move_to != null && va_extra_fields.move_to.size() > 0) {
				sb.append(' ').append("VA MOV TO ").append(va_extra_fields.move_to.get(0)).append(" FIR");
			}
			return sb.toString();
		}
		sb.append('\n');
		/* Test or exercise */
		SigmetType type = this.type == null ? SigmetType.normal :this.type;
		switch(type) {
		case test:
			sb.append("TEST ");
			break;
		case exercise:
			sb.append("EXER ");
			break;			
		default:
		}

		if (va_extra_fields != null) {
			sb.append(va_extra_fields.toTAC());
		}

		Debug.println("phen: "+this.phenomenon);
		sb.append(this.phenomenon.getShortDescription());


		sb.append('\n');
		if (this.getObs_or_forecast()!=null) {
			sb.append(this.obs_or_forecast.toTAC());
			sb.append('\n');
		}
		sb.append(this.featureToTAC((Feature)effectiveStartGeometry, FIR));
		sb.append('\n');

		String levelInfoText=this.levelinfo.toTAC();
		if (!levelInfoText.isEmpty()) {
			sb.append(levelInfoText);
			sb.append('\n');
		}

		if (this.movement_type==null) {
			this.movement_type=SigmetMovementType.STATIONARY;
		}

		switch (this.movement_type) {
		case STATIONARY:
			sb.append("STNR ");
			break;
		case MOVEMENT:
			if (this.movement!=null) {
				sb.append(this.movement.toTAC());
				sb.append('\n');
			}
			break;
		case FORECAST_POSITION:
			// Present forecast_position geometry below
			break;
		}

		if (this.change!=null) {
			sb.append(this.change.toTAC());
			sb.append('\n');
		}

		if (this.movement_type==SigmetMovementType.FORECAST_POSITION) {
			OffsetDateTime fpaTime=this.validdate_end;
			sb.append("FCST AT ").append(String.format("%02d", fpaTime.getHour())).append(String.format("%02d", fpaTime.getMinute())).append("Z");
			sb.append('\n');
			sb.append(this.featureToTAC((Feature)this.findEndGeometry(((Feature)findStartGeometry()).getId()), FIR));

		} else {
			if (va_extra_fields !=null && va_extra_fields.no_va_expected) {
				OffsetDateTime fpaTime=this.validdate_end;
				sb.append("FCST AT ").append(String.format("%02d", fpaTime.getHour())).append(String.format("%02d", fpaTime.getMinute())).append("Z");
				sb.append(" NO VA EXP");
			} 
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

	// Same as TAC, but maximum line with 69 chars where words (e.g. "OVC020CB") are not split
	// Also has a header and footer to the message
	//    private String __getPublishableTAC() {
	//        String line = "";
	//        String publishTAC = "";
	//        String[] TACwords = this.toTAC(this.getFirFeature()).split("\\s+");
	//        for(int i = 0; i < TACwords.length; ++i) {
	//            if (line.length() + TACwords[i].length() + 1 <= 69) {
	//                if (line.length() > 0) line += " ";
	//                line += TACwords[i];
	//            } else {
	//                publishTAC += line + '\n';
	//                line = TACwords[i];
	//            }
	//        }
	//        publishTAC += line;
	//        String time = this.getValiddate().format(DateTimeFormatter.ofPattern("ddHHmm"));;
	//
	//        String header = "WSNL31 " + this.getLocation_indicator_mwo() + " " + time +'\n';
	//        String footer = "=";
	//        return header + publishTAC + footer;
	//    }

	@Override
	public String export(File path, ProductConverter<Sigmet> converter, ObjectMapper om) {
		//		String s=converter.ToIWXXM_2_1(this);
		List<String> toDeleteIfError=new ArrayList<>(); //List of products to delete in case of error
		try {
			OffsetDateTime now = OffsetDateTime.now(ZoneId.of("Z"));
			String time = now.format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
			String validTime = this.getValiddate().format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HHmm"));

			String bulletinHeader = "";
			if (this.getPhenomenon() == Phenomenon.VA_CLD) {
				bulletinHeader = "WVNL31";
			} else if (this.getPhenomenon() == Phenomenon.TROPICAL_CYCLONE) {
				bulletinHeader = "WCNL31";  // TODO CHECK if WS is OK for TC
			} else {
				bulletinHeader = "WSNL31";
			}

			String TACName = bulletinHeader + this.getLocation_indicator_mwo() + "_" + validTime + "_" + time;
			String tacFileName=path.getPath() + "/" + TACName + ".tac";
			String TACHeaderTime = now.format(DateTimeFormatter.ofPattern("ddHHmm"));
			String TACHeaderLocation = this.getLocation_indicator_mwo();
			/* Create TAC header */
			String TACHeader = "ZCZC\n" + bulletinHeader + " " + TACHeaderLocation+" "+TACHeaderTime+"\n";	
			/* Create TAC message */
			String TACCode = this.toTAC(this.getFirFeature());
			// Remove all empty lines			
			TACCode  = TACCode.replaceAll("(?m)^[ \t]*\r?\n", "");
			// Replace last \n if available
			if (TACCode.length() > 1 && TACCode.endsWith("\n")) { TACCode = TACCode.substring(0, TACCode.length() - 1); }
			/* Create TAC footer */
			String TACFooter = "=\nNNNN\n";
			Tools.writeFile(tacFileName, TACHeader + TACCode +  TACFooter);
			toDeleteIfError.add(tacFileName);

			String name = "SIGMET_" + this.getLocation_indicator_mwo() + "_" + validTime + "_" + time;
			String jsonFileName=path.getPath() + "/" + name + ".json";
			Tools.writeFile(jsonFileName, this.toJSON(om));
			toDeleteIfError.add(jsonFileName);

			String iwxxmName="A_"+"LSNL31"+this.getLocation_indicator_mwo()+this.getValiddate().format(DateTimeFormatter.ofPattern("ddHHmm"));
			iwxxmName+="_C_"+this.getLocation_indicator_mwo()+"_"+time;
			String s=converter.ToIWXXM_2_1(this);
			Tools.writeFile(path.getPath() + "/" + iwxxmName + ".xml", s);
		} catch (IOException | NullPointerException e) {
			toDeleteIfError.stream().forEach(f ->  {Debug.println("REMOVING "+f); Tools.rm(f); });
			return "ERROR "+e.getMessage();
		}
		return "OK";
	}
}
