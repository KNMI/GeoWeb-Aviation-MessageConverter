package nl.knmi.geoweb.iwxxm_2_1.converter;

import java.time.OffsetDateTime;
import java.time.ZoneId;

import org.geojson.Feature;
import org.locationtech.jts.util.Debug;

import fi.fmi.avi.converter.ConversionHints;
import fi.fmi.avi.converter.ConversionResult;
import fi.fmi.avi.model.AirTrafficServicesUnit;
import fi.fmi.avi.model.impl.AirTrafficServicesUnitImpl;
import fi.fmi.avi.model.impl.NumericMeasureImpl;
import fi.fmi.avi.model.sigmet.SIGMET;
import fi.fmi.avi.model.sigmet.SigmetAnalysis;
import fi.fmi.avi.model.sigmet.SigmetAnalysis.SigmetAnalysisType;
import fi.fmi.avi.model.sigmet.SigmetForecastPositionAnalysis;
import fi.fmi.avi.model.sigmet.impl.SigmetAnalysisImpl;
import fi.fmi.avi.model.sigmet.impl.SigmetForecastPositionAnalysisImpl;
import fi.fmi.avi.model.sigmet.impl.SigmetImpl;
import fi.fmi.avi.model.sigmet.impl.SigmetValidityTime;
import fi.fmi.avi.model.AviationCodeListUser;
import fi.fmi.avi.model.NumericMeasure;
import nl.knmi.geoweb.backend.product.sigmet.Sigmet;
import nl.knmi.geoweb.backend.product.sigmet.Sigmet.SigmetLevelMode;
import nl.knmi.geoweb.backend.product.sigmet.geo.GeoUtils;

public class GeoWebSIGMETConverter extends AbstractGeoWebSigmetConverter<SIGMET>{

	@Override
	public ConversionResult<SIGMET> convertMessage(Sigmet input, ConversionHints hints) {
		System.err.println("cinvertMessage: "+this.getClass().getName());
		ConversionResult<SIGMET> retval = new ConversionResult<>();
		SIGMET sigmet = new SigmetImpl();

		AirTrafficServicesUnit unit=new AirTrafficServicesUnitImpl(input.getFirname(),input.getLocation_indicator_icao(), input.getLocation_indicator_mwo());
		sigmet.setAirTrafficServicesUnit(unit)	;

		if (input.getIssuedate()==null) {
		  sigmet.setIssueTime(OffsetDateTime.now().atZoneSameInstant(ZoneId.of("UTC")));
		} else {
		  sigmet.setIssueTime(input.getIssuedate().atZoneSameInstant(ZoneId.of("UTC"))); //TODO
		}
		AviationCodeListUser.AeronauticalSignificantWeatherPhenomenon at=AviationCodeListUser.AeronauticalSignificantWeatherPhenomenon.valueOf(input.getPhenomenon().toString());
		sigmet.setSigmetPhenomenon(at);
		sigmet.setSequenceNumber(Integer.toString(input.getSequence())); //TODO Should be a String??

		SigmetValidityTime validityTime=new SigmetValidityTime();
		validityTime.setCompleteStartTime(input.getValiddate().atZoneSameInstant(ZoneId.of("UTC")));
		validityTime.setCompleteEndTime(input.getValiddate_end().atZoneSameInstant(ZoneId.of("UTC")));
		sigmet.setValidityPeriod(validityTime);

		SigmetAnalysis sa=new SigmetAnalysisImpl();
		if (input.getObs_or_forecast()!=null) {
			if (input.getObs_or_forecast().isObs()){
				sa.setAnalysisType(SigmetAnalysisType.OBSERVATION);
			} else {
				sa.setAnalysisType(SigmetAnalysisType.FORECAST);
			}
			if (input.getObs_or_forecast().getObsFcTime()!=null) {
				sa.setTime(input.getObs_or_forecast().getObsFcTime().atZoneSameInstant(ZoneId.of("UTC")));
			}
		}
		sa.setApproximateLocation(false); //TODO
		boolean fpaRequired=true;
		if (input.getMovement()!=null) {
			if (!input.getMovement().isStationary()) {
				if ((input.getMovement().getDir()!=null) && (input.getMovement().getSpeed()!=null)) {
					NumericMeasure numDir=new NumericMeasureImpl(input.getMovement().getDir().getDir(), "deg");//TODO make new constructor 
					sa.setSingleMovingDirection(numDir);
					NumericMeasure numSpd=new NumericMeasureImpl(input.getMovement().getSpeed(), input.getMovement().getSpeeduom());//TODO make new constructor 
					sa.setSingleMovingSpeed(numSpd);
					fpaRequired=false;
				}
			} else {
				fpaRequired=false;
				sa.setSingleMovingDirection(null);
				sa.setSingleMovingSpeed(null);
			}
		}

		Debug.println("levelinfo: "+input.getLevelinfo());;
		if (input.getLevelinfo()!=null) {
			Debug.println("setLevelInfo("+input.getLevelinfo().getMode()+")");
			switch (input.getLevelinfo().getMode()) {
			case BETW:
				NumericMeasure nmLower=new NumericMeasureImpl((double)input.getLevelinfo().getLevels()[0].getValue(), input.getLevelinfo().getLevels()[0].getUnit().toString());
				sa.setSingleLowerLimit(nmLower);
				NumericMeasure nmUpper=new NumericMeasureImpl((double)input.getLevelinfo().getLevels()[1].getValue(), input.getLevelinfo().getLevels()[1].getUnit().toString());
				sa.setSingleUpperLimit(nmUpper);
				break;
			case BETW_SFC:
				nmLower=new NumericMeasureImpl((Double)null, "SFC");
				sa.setSingleLowerLimit(nmLower);
				nmUpper=new NumericMeasureImpl((double)input.getLevelinfo().getLevels()[1].getValue(), input.getLevelinfo().getLevels()[1].getUnit().toString());
				sa.setSingleUpperLimit(nmUpper);
				break;
			case AT:
				nmLower=new NumericMeasureImpl((double)input.getLevelinfo().getLevels()[0].getValue(), input.getLevelinfo().getLevels()[0].getUnit().toString());
				sa.setSingleLowerLimit(nmLower);
				sa.setSingleUpperLimit(nmLower);
				break;
			case ABV:
				nmLower=new NumericMeasureImpl((double)input.getLevelinfo().getLevels()[0].getValue(), input.getLevelinfo().getLevels()[0].getUnit().toString());
				sa.setSingleLowerLimit(nmLower);
				sa.setSingleUpperLimit(nmLower);
				sa.setSingleLowerLimitOperator(AviationCodeListUser.RelationalOperator.ABOVE);
				break;
			case TOPS:
				nmUpper=new NumericMeasureImpl((double)input.getLevelinfo().getLevels()[0].getValue(), input.getLevelinfo().getLevels()[0].getUnit().toString());
				sa.setSingleUpperLimit(nmUpper);
				break;
			case TOPS_ABV:
				nmUpper=new NumericMeasureImpl((double)input.getLevelinfo().getLevels()[0].getValue(), input.getLevelinfo().getLevels()[0].getUnit().toString());
				sa.setSingleUpperLimit(nmUpper);
				sa.setSingleLowerLimitOperator(AviationCodeListUser.RelationalOperator.ABOVE);
				break;
			case TOPS_BLW:
				nmLower=new NumericMeasureImpl((double)input.getLevelinfo().getLevels()[0].getValue(), input.getLevelinfo().getLevels()[0].getUnit().toString());
				sa.setSingleLowerLimit(nmLower);
				sa.setSingleLowerLimitOperator(AviationCodeListUser.RelationalOperator.ABOVE);
				break;
			}
		}

		sa.setGeometry(GeoUtils.jsonFeature2jtsGeometry((Feature)input.extractSingleStartGeometry()));

		sigmet.getAnalysis().add(sa);

		System.err.println("FPA required: "+fpaRequired);
		if (fpaRequired) {
			SigmetForecastPositionAnalysis fpa=new SigmetForecastPositionAnalysisImpl();
			fpa.setTime(input.getValiddate_end().atZoneSameInstant(ZoneId.of("UTC")));
			fpa.setApproximateLocation(false); //TODO
			fpa.setGeometry(GeoUtils.jsonFeature2jtsGeometry((Feature)input.extractSingleEndGeometry()));
			sigmet.setSingleForecastPositionAnalysis(fpa);
		}

		retval.setConvertedMessage(sigmet);

		return retval;
	}
}