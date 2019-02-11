package nl.knmi.geoweb.backend.product.airmet;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

import org.geojson.Feature;
import org.geojson.FeatureCollection;
import org.geojson.GeoJsonObject;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

import lombok.Getter;
import lombok.Setter;
import nl.knmi.adaguc.tools.Debug;
import nl.knmi.geoweb.backend.product.GeoWebProduct;
import nl.knmi.geoweb.backend.product.IExportable;
import nl.knmi.geoweb.backend.product.ProductConverter;
import nl.knmi.geoweb.backend.product.sigmetairmet.ObsFc;
import nl.knmi.geoweb.backend.product.sigmetairmet.SigmetAirmetChange;
import nl.knmi.geoweb.backend.product.sigmetairmet.SigmetAirmetLevel;
import nl.knmi.geoweb.backend.product.sigmetairmet.SigmetAirmetMovement;
import nl.knmi.geoweb.backend.product.sigmetairmet.SigmetAirmetStatus;
import nl.knmi.geoweb.backend.product.sigmetairmet.SigmetAirmetType;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
@Getter
@Setter
public class Airmet implements GeoWebProduct, IExportable<Airmet> {
    public static final Duration WAVALIDTIME = Duration.ofHours(4); //4*3600*1000;

    private GeoJsonObject geojson;
    private Phenomenon phenomenon;
    private List<ObscuringPhenomenonList.ObscuringPhenomenon> obscuring;
    private AirmetWindInfo wind;
    private AirmetCloudLevelInfo cloudLevels;

    private AirmetValue visibility;
    private ObsFc obs_or_forecast;
    private SigmetAirmetLevel levelinfo;
    private AirmetMovementType movement_type;
    private SigmetAirmetMovement movement;
    private SigmetAirmetChange change;
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
    private SigmetAirmetStatus status;
    private SigmetAirmetType type;
    private int sequence;

    @JsonIgnore
    private Feature firFeature;

    public enum ParamInfo {
        WITH_CLOUDLEVELS, WITH_OBSCURATION, WITH_WIND;
    }

    @Getter
    @Setter
    public static class AirmetWindInfo {
        private AirmetValue speed;
        private AirmetValue  direction;

        public AirmetWindInfo(double speed, String speedUnit, double direction, String unit) {
            this.speed=new AirmetValue(speed, speedUnit);
            this.direction=new AirmetValue(direction, unit);
        }

        public AirmetWindInfo(double speed, double direction) {
            this(speed, "KT", direction, "degrees");
        }

        public AirmetWindInfo(){
        }
    }

    @Getter
    public static class LowerCloudLevel extends AirmetValue {
        Boolean surface;

        public LowerCloudLevel() {}

        public LowerCloudLevel(boolean isSurface) {
            this.surface=isSurface;
        }

        public LowerCloudLevel(double level, String unit) {
            this.setVal(level);
            this.setUnit(unit);
        }
    }

    @Getter
    public static class UpperCloudLevel extends AirmetValue {
        Boolean above;

        public UpperCloudLevel() {}

        public UpperCloudLevel(boolean isSurface) {
            this.above=new Boolean(isSurface);
        }

        public UpperCloudLevel(double level, String unit) {
            this(false, level, unit);
        }

        public UpperCloudLevel(boolean isAbove, double level, String unit) {
            if (isAbove) {
                this.above=isAbove;
            }
            this.setVal(level);
            this.setUnit(unit);
        }
    }

    @Getter
    @Setter
    public static class AirmetCloudLevelInfo {

        private LowerCloudLevel lower;
        private UpperCloudLevel upper;

        public AirmetCloudLevelInfo(double upper) {
            this.lower=new LowerCloudLevel(true);
            this.upper=new UpperCloudLevel(upper, "FT");
        }

        public AirmetCloudLevelInfo(boolean above, double upper) {
            this(upper);
            this.getUpper().above=above;
        }

        public AirmetCloudLevelInfo(double lower, double upper) {
            this.lower=new LowerCloudLevel(lower, "FT");
            this.upper=new UpperCloudLevel(upper, "FT");
        }

        public AirmetCloudLevelInfo(double lower, boolean above, double upper, String unit) {
            this.lower=new LowerCloudLevel(lower, unit);
            this.upper=new UpperCloudLevel(above, upper, unit);
        }

        public AirmetCloudLevelInfo(){}
    }

    @Getter
    @Setter
    public static class AirmetValue {
        private Double val;
        private String unit;
        public AirmetValue(double val, String unit) {
            this.val=val;
            this.unit=unit;
        }
        public AirmetValue(){
        }
    }

    @Getter
    public enum Phenomenon {
        BKN_CLD("BKN_CLD", ParamInfo.WITH_CLOUDLEVELS),
        OVC_CLD("OVC_CLD", ParamInfo.WITH_CLOUDLEVELS),
        FRQ_CB("FRQ_CB"),
        FRQ_TCU("FRQ_TCU"),
        ISOL_CB("ISOL_CB"),
        ISOL_TCU("ISOL_TCU"),
        ISOL_TS("ISOL_TS"),
        ISOL_TSGR("ISOL_TSGR"),
        MOD_ICE("MOD_ICE"),
        MOD_MTW("MOD_MTW"),
        MOD_TURB("MOD_TURB"),
        MT_OBSC("MT_OBSC"),
        OCNL_CB("OCNL_CB"),
        OCNL_TS("OCNL_TS"),
        OCNL_TSGR("OCNL_TSGR"),
        OCNL_TCU("OCNL_TCU"),
        SFC_VIS("SFC_VIS", ParamInfo.WITH_OBSCURATION),
        SFC_WIND("SFC_WIND", ParamInfo.WITH_WIND);


        private String description;
        private String shortDescription;
        private ParamInfo paramInfo;

        public static Phenomenon getRandomPhenomenon() {
            int i=(int)(Math.random()*Phenomenon.values().length);
            System.err.println("rand "+i+ " "+Phenomenon.values().length);
            return Phenomenon.valueOf(Phenomenon.values()[i].toString());
        }

        private Phenomenon(String shrt, ParamInfo paramInfo) {
            this(shrt, "", paramInfo);
        }

        private Phenomenon(String shrt) {
            this(shrt, "", null);
        }

        private Phenomenon(String shrt, String description, ParamInfo paramInfo) {
            this.shortDescription=shrt;
            this.description=description;
            this.paramInfo = paramInfo;
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

    @Getter
    public enum AirmetStatus {
        concept("concept"), canceled("canceled"), published("published");//, test("test"); TODO: Check, should be in Type now.
        private String status;
        private AirmetStatus (String status) {
            this.status = status;
        }
        public static AirmetStatus getSigmetStatus(String status){
            Debug.println("SIGMET status: " + status);

            for (AirmetStatus sstatus: AirmetStatus.values()) {
                if (status.equals(sstatus.toString())){
                    return sstatus;
                }
            }
            return null;
        }

    }

    @Getter
    public enum AirmetType {
        normal("normal"), test("test"), exercise("exercise");
        private String type;
        private AirmetType (String type) {
            this.type = type;
        }
        public static AirmetType getSigmetType(String itype){
            for (AirmetType stype: AirmetType.values()) {
                if (itype.equals(stype.toString())){
                    return stype;
                }
            }
            return null;
        }

    }

    public enum AirmetMovementType {
        STATIONARY, MOVEMENT;
    }

 	public String toTAC() {
		if (this.firFeature!=null) {
			return this.toTAC(this.firFeature);
		}
		return "";
	}

	public String toTAC(Feature FIR) {
        return "TAC of AIRMET";
    }

    public String toJSON(ObjectMapper om) throws JsonProcessingException {
        return om.writerWithDefaultPrettyPrinter().writeValueAsString(this);
    }

    public Airmet() {
        this.sequence=-1;
        this.obscuring = new ArrayList<>();
    }

    public Airmet(String firname, String location, String issuing_mwo, String uuid) {
        this.firname=firname;
        this.location_indicator_icao=location;
        this.location_indicator_mwo=issuing_mwo;
        this.uuid=uuid;
        this.sequence=-1;
        this.obscuring = new ArrayList<>();
        this.phenomenon = null;
        // If an AIRMET is posted, this has no effect
        this.status= SigmetAirmetStatus.concept;
        this.type= SigmetAirmetType.test;
    }

    public Airmet(Airmet otherAirmet) {
        this.firname=otherAirmet.getFirname();
        this.location_indicator_icao=otherAirmet.getLocation_indicator_icao();
        this.location_indicator_mwo=otherAirmet.getLocation_indicator_mwo();
        this.sequence=-1;
        this.obscuring = new ArrayList<>();
        this.phenomenon = otherAirmet.getPhenomenon();
        this.validdate = otherAirmet.getValiddate();
        this.validdate_end = otherAirmet.getValiddate_end();
        this.issuedate = otherAirmet.getIssuedate();
        this.firFeature = otherAirmet.firFeature;
        this.type=otherAirmet.type;
    }

    public void serializeAirmet(ObjectMapper om, String fn) {
        Debug.println("serializeAirmet to "+fn);
        if(/*this.geojson == null ||*/ this.phenomenon == null) {
            throw new IllegalArgumentException("GeoJSON and Phenomenon are required");
        }
        // .... value from constructor is lost here, set it explicitly. (Why?)
        if(this.status == null) {
            this.status = SigmetAirmetStatus.concept;
        }
        if(this.type == null) {
            this.type = SigmetAirmetType.test;
        }
        try {
            om.writeValue(new File(fn), this);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public String dumpAirmetGeometryInfo() {
        StringWriter sw=new StringWriter();
        PrintWriter pw=new PrintWriter(sw);
        pw.println("AIRMET ");
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

    public String serializeAirmetToString(ObjectMapper om) throws JsonProcessingException {
        return om.writeValueAsString(this);
    }

    public static Airmet getAirmetFromFile(ObjectMapper om, File f) throws JsonParseException, JsonMappingException, IOException {
        Airmet sm=om.readValue(f, Airmet.class);
        //		Debug.println("Airmet from "+f.getName());
        //		Debug.println(sm.dumpAirmetGeometryInfo());
        return sm;
    }

    @Override
    public String export(final File path, final ProductConverter<Airmet> converter, final ObjectMapper om) {
        return "OK";
    }
}
