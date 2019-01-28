package nl.knmi.geoweb.backend.product.airmet;

import java.io.File;
import java.io.IOException;
import java.time.Duration;
import java.time.OffsetDateTime;

import org.geojson.Feature;
import org.geojson.GeoJsonObject;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.Getter;
import lombok.Setter;
import nl.knmi.adaguc.tools.Debug;
import nl.knmi.geoweb.backend.product.GeoWebProduct;
import nl.knmi.geoweb.backend.product.IExportable;
import nl.knmi.geoweb.backend.product.ProductConverter;

@JsonInclude(JsonInclude.Include.NON_NULL)
@Getter
@Setter
public class Airmet implements GeoWebProduct, IExportable<Airmet> {
    public static final Duration WSVALIDTIME = Duration.ofHours(4); //4*3600*1000;
    public static final Duration WVVALIDTIME = Duration.ofHours(6); //6*3600*1000;

//    private GeoJsonObject geojson;
    private Phenomenon phenomenon;
//    private ObsFc obs_or_forecast;
    //	@JsonFormat(shape = JsonFormat.Shape.STRING)
    //	private OffsetDateTime forecast_position_time;
//    private AirmetLevel levelinfo;
//    private AirmetMovementType movement_type;
//    private AirmetMovement movement;
//    private AirmetChange change;
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
    private AirmetStatus status;
    private AirmetType type;
    private int sequence;

    @JsonIgnore
    private Feature firFeature;

    public enum Obscuration {

    }

    public enum ParamInfo {
        WITH_CLOUDLEVELS, WITH_OBSCURATION, WITH_WIND;
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
    }

    public Airmet(String firname, String location, String issuing_mwo, String uuid) {
        this.firname=firname;
        this.location_indicator_icao=location;
        this.location_indicator_mwo=issuing_mwo;
        this.uuid=uuid;
        this.sequence=-1;
        this.phenomenon = null;
        // If an AIRMET is posted, this has no effect
        this.status= Airmet.AirmetStatus.concept;
        this.type= Airmet.AirmetType.test;
    }

    public Airmet(Airmet otherAirmet) {
        this.firname=otherAirmet.getFirname();
        this.location_indicator_icao=otherAirmet.getLocation_indicator_icao();
        this.location_indicator_mwo=otherAirmet.getLocation_indicator_mwo();
        this.sequence=-1;
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
            this.status = AirmetStatus.concept;
        }
        if(this.type == null) {
            this.type = AirmetType.test;
        }
        try {
            om.writeValue(new File(fn), this);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public String serializeAirmetToString(ObjectMapper om) throws JsonProcessingException {
        return om.writeValueAsString(this);
    }
    public static Airmet getAirmetFromFile(ObjectMapper om, File f) throws JsonParseException, JsonMappingException, IOException {
        Airmet sm=om.readValue(f, Airmet.class);
        //		Debug.println("Sigmet from "+f.getName());
        //		Debug.println(sm.dumpSigmetGeometryInfo());
        return sm;
    }

    @Override
    public String export(final File path, final ProductConverter<Airmet> converter, final ObjectMapper om) {
        return "OK";
    }
}
