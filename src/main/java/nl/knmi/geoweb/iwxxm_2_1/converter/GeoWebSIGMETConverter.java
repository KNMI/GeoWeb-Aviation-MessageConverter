package nl.knmi.geoweb.iwxxm_2_1.converter;

import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import fi.fmi.avi.model.PartialOrCompleteTimeInstant;
import fi.fmi.avi.model.PartialOrCompleteTimePeriod;
import fi.fmi.avi.model.immutable.UnitPropertyGroupImpl;
import fi.fmi.avi.model.sigmet.SigmetAnalysis;
import fi.fmi.avi.model.sigmet.SigmetAnalysisType;
import fi.fmi.avi.model.sigmet.immutable.SIGMETImpl;
import fi.fmi.avi.model.sigmet.immutable.SigmetAnalysisImpl;
import org.geojson.Feature;
import org.locationtech.jts.util.Debug;

import fi.fmi.avi.converter.ConversionHints;
import fi.fmi.avi.converter.ConversionResult;
import fi.fmi.avi.model.immutable.NumericMeasureImpl;
import fi.fmi.avi.model.sigmet.SIGMET;
import fi.fmi.avi.model.AviationCodeListUser;
import fi.fmi.avi.model.NumericMeasure;
import nl.knmi.geoweb.backend.product.sigmet.Sigmet;
import nl.knmi.geoweb.backend.product.sigmet.Sigmet.SigmetMovementType;
import nl.knmi.geoweb.backend.product.sigmet.SigmetPhenomenaMapping.SigmetPhenomenon;
import nl.knmi.geoweb.backend.product.sigmet.geo.GeoUtils;

public class GeoWebSIGMETConverter extends AbstractGeoWebSigmetConverter<SIGMET> {

    @Override
    public ConversionResult<SIGMET> convertMessage(Sigmet input, ConversionHints hints) {
        Debug.println("convertMessage: " + this.getClass().getName());
        ConversionResult<SIGMET> retval = new ConversionResult<>();
        SIGMETImpl.Builder sigmet = new SIGMETImpl.Builder();

        UnitPropertyGroupImpl.Builder unit = new UnitPropertyGroupImpl.Builder();
        unit.setPropertyGroup(input.getFirname(), input.getLocation_indicator_icao(), "FIR");
        sigmet.setIssuingAirTrafficServicesUnit(unit.build());
        UnitPropertyGroupImpl.Builder mwo = new UnitPropertyGroupImpl.Builder();
        mwo.setPropertyGroup(input.getLocation_indicator_mwo(), input.getLocation_indicator_mwo(), "MWO");
        sigmet.setMeteorologicalWatchOffice(mwo.build());

        if (input.getIssuedate() == null) {
            sigmet.setIssueTime(PartialOrCompleteTimeInstant.of(ZonedDateTime.now()));
        } else {
            sigmet.setIssueTime(PartialOrCompleteTimeInstant.of(input.getIssuedate().atZoneSameInstant(ZoneId.of("UTC")))); //TODO
        }
        AviationCodeListUser.AeronauticalSignificantWeatherPhenomenon at = AviationCodeListUser.AeronauticalSignificantWeatherPhenomenon.valueOf(input.getPhenomenon().toString());
        sigmet.setSigmetPhenomenon(at);
        sigmet.setSequenceNumber(Integer.toString(input.getSequence())); //TODO Should be a String??

        PartialOrCompleteTimePeriod.Builder validPeriod=new PartialOrCompleteTimePeriod.Builder();
        validPeriod.setStartTime(PartialOrCompleteTimeInstant.of(input.getValiddate().atZoneSameInstant(ZoneId.of("UTC"))));
        validPeriod.setEndTime(PartialOrCompleteTimeInstant.of(input.getValiddate_end().atZoneSameInstant(ZoneId.of("UTC"))));
        sigmet.setValidityPeriod(validPeriod.build());

        SigmetAnalysisImpl.Builder sa = new SigmetAnalysisImpl.Builder();
        if (input.getObs_or_forecast() != null) {
            if (input.getObs_or_forecast().isObs()) {
                sa.setAnalysisType(SigmetAnalysisType.OBSERVATION);
            } else {
                sa.setAnalysisType(SigmetAnalysisType.FORECAST);
            }
            if (input.getObs_or_forecast().getObsFcTime() != null) {
                sa.setAnalysisTime(PartialOrCompleteTimeInstant.of(input.getObs_or_forecast().getObsFcTime().atZoneSameInstant(ZoneId.of("UTC"))));
            }
        }
        sa.setAnalysisApproximateLocation(false); //TODO
        boolean fpaRequired = true;

        if (input.getMovement_type() == null) {
            input.setMovement_type(SigmetMovementType.STATIONARY);
        }
        switch (input.getMovement_type()) {
            case STATIONARY:
                sa.setMovingDirection(Optional.empty());
                sa.setMovingSpeed(Optional.empty());
                fpaRequired = false;
                break;
            case MOVEMENT:
                if ((input.getMovement().getDir() != null) && (input.getMovement().getSpeed() != null)) {
                    NumericMeasure numDir = NumericMeasureImpl.of(input.getMovement().getDir().getDir(), "deg");
                    sa.setMovingDirection(numDir);
                    String uom = input.getMovement().getSpeeduom();
                    if ("KMH".equals(uom)) {
                        uom = "km/h";
                    }
                    if ("KT".equals(uom)) {
                        uom = "[kn_i]";
                    }
                    NumericMeasure numSpd = NumericMeasureImpl.of(input.getMovement().getSpeed(), uom);
                    sa.setMovingSpeed(numSpd);
                    fpaRequired = false;
                }
                break;
            case FORECAST_POSITION:
                break;
        }

        Debug.println("levelinfo: " + input.getLevelinfo());

        if (input.getLevelinfo() != null) {
            Debug.println("setLevelInfo(" + input.getLevelinfo().getMode() + ")");
            switch (input.getLevelinfo().getMode()) {
                case BETW:
                    NumericMeasure nmLower = NumericMeasureImpl.of((double) input.getLevelinfo().getLevels()[0].getValue(), input.getLevelinfo().getLevels()[0].getUnit().toString());
                    sa.setLowerLimit(nmLower);
                    NumericMeasure nmUpper = NumericMeasureImpl.of((double) input.getLevelinfo().getLevels()[1].getValue(), input.getLevelinfo().getLevels()[1].getUnit().toString());
                    sa.setUpperLimit(nmUpper);
                    break;
                case BETW_SFC:
                    nmLower = NumericMeasureImpl.of((Double) null, "SFC");
                    sa.setLowerLimit(nmLower);
                    nmUpper = NumericMeasureImpl.of((double) input.getLevelinfo().getLevels()[1].getValue(), input.getLevelinfo().getLevels()[1].getUnit().toString());
                    sa.setUpperLimit(nmUpper);
                    break;
                case AT:
                    nmLower = NumericMeasureImpl.of((double) input.getLevelinfo().getLevels()[0].getValue(), input.getLevelinfo().getLevels()[0].getUnit().toString());
                    sa.setLowerLimit(nmLower);
                    sa.setUpperLimit(nmLower);
                    break;
                case ABV:
                    nmLower = NumericMeasureImpl.of((double) input.getLevelinfo().getLevels()[0].getValue(), input.getLevelinfo().getLevels()[0].getUnit().toString());
                    sa.setLowerLimit(nmLower);
                    sa.setUpperLimit(nmLower);
                    sa.setLowerLimitOperator(AviationCodeListUser.RelationalOperator.ABOVE);
                    break;
                case TOPS:
                    nmUpper = NumericMeasureImpl.of((double) input.getLevelinfo().getLevels()[0].getValue(), input.getLevelinfo().getLevels()[0].getUnit().toString());
                    sa.setUpperLimit(nmUpper);
                    break;
                case TOPS_ABV:
                    nmUpper = NumericMeasureImpl.of((double) input.getLevelinfo().getLevels()[0].getValue(), input.getLevelinfo().getLevels()[0].getUnit().toString());
                    sa.setUpperLimit(nmUpper);
                    sa.setLowerLimitOperator(AviationCodeListUser.RelationalOperator.ABOVE);
                    break;
                case TOPS_BLW:
                    nmLower = NumericMeasureImpl.of((double) input.getLevelinfo().getLevels()[0].getValue(), input.getLevelinfo().getLevels()[0].getUnit().toString());
                    sa.setLowerLimit(nmLower);
                    sa.setLowerLimitOperator(AviationCodeListUser.RelationalOperator.ABOVE);
                    break;
            }
        }

        sa.setAnalysisGeometry(GeoUtils.jsonFeature2jtsGeometry((Feature) input.extractSingleStartGeometry()));


        Debug.println("FPA required: " + fpaRequired);
        if (fpaRequired) {
            sa.setForecastTime(PartialOrCompleteTimeInstant.of(input.getValiddate_end().atZoneSameInstant(ZoneId.of("UTC"))));
            sa.setForecastGeometry(GeoUtils.jsonFeature2jtsGeometry((Feature) input.extractSingleEndGeometry()));
            sa.setForecastApproximateLocation(false);
        }

        List<SigmetAnalysis> sigmetAnalysisList=new ArrayList<>();
        sigmetAnalysisList.add(sa.build());
        sigmet.setAnalysis(sigmetAnalysisList);

        //Not translated
        sigmet.setTranslated(false);
        Sigmet.SigmetStatus st=input.getStatus();
        if (input.getStatus().getStatus().equals(Sigmet.SigmetStatus.canceled)) {
            sigmet.setStatus(AviationCodeListUser.SigmetReportStatus.CANCELLATION);
            sigmet.setPermissibleUsage(AviationCodeListUser.PermissibleUsage.OPERATIONAL);
        } else {
            if ( input.getStatus().getStatus().equals(Sigmet.SigmetStatus.test)) {
                sigmet.setPermissibleUsage(AviationCodeListUser.PermissibleUsage.NON_OPERATIONAL);
                sigmet.setPermissibleUsageReason(AviationCodeListUser.PermissibleUsageReason.EXERCISE);
            } else {
                sigmet.setPermissibleUsage(AviationCodeListUser.PermissibleUsage.OPERATIONAL);
            }
            sigmet.setStatus(AviationCodeListUser.SigmetReportStatus.NORMAL);
        }

        retval.setConvertedMessage(sigmet.build());

        return retval;
    }
}
