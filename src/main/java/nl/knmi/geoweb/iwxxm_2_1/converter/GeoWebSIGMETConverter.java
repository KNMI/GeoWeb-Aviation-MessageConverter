package nl.knmi.geoweb.iwxxm_2_1.converter;

import java.time.ZoneId;

import org.geojson.Feature;

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
import nl.knmi.geoweb.backend.product.sigmet.geo.GeoUtils;

public class GeoWebSIGMETConverter extends AbstractGeoWebSigmetConverter<SIGMET>{

	@Override
	public ConversionResult<SIGMET> convertMessage(Sigmet input, ConversionHints hints) {
		ConversionResult<SIGMET> retval = new ConversionResult<>();
		SIGMET sigmet = new SigmetImpl();

		AirTrafficServicesUnit unit=new AirTrafficServicesUnitImpl(input.getFirname(),input.getLocation_indicator_icao(), input.getLocation_indicator_mwo());
		sigmet.setAirTrafficServicesUnit(unit)	;

		sigmet.setIssueTime(input.getIssuedate().atZoneSameInstant(ZoneId.of("UTC"))); //TODO
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
				if ((input.getMovement().getDir()!=null)) {
					NumericMeasure numDir=new NumericMeasureImpl(input.getMovement().getDir().getDir(), "deg");//TODO make new constructor 
					sa.setSingleMovingDirection(numDir);
					fpaRequired=false;
				}
				if ((input.getMovement().getSpeed()!=null)) {
					NumericMeasure numDir=new NumericMeasureImpl(input.getMovement().getSpeed(), "KMH");//TODO make new constructor 
					sa.setSingleMovingSpeed(numDir);
					fpaRequired=false;
				}
			}
		}
		if ((input.getLevel()!=null)&&(input.getLevel().getLev1()!=null)) {
			System.err.println("lev1");
			NumericMeasure nm=new NumericMeasureImpl((double)input.getLevel().getLev1().getValue(), input.getLevel().getLev1().getUnit().toString());
			sa.setSingleLowerLimit(nm);
		}
		if ((input.getLevel()!=null)&&(input.getLevel().getLev2()!=null)) {
			System.err.println("lev2");
			NumericMeasure nm=new NumericMeasureImpl((double)input.getLevel().getLev2().getValue(), input.getLevel().getLev2().getUnit().toString());
			sa.setSingleLowerLimit(nm);
		}	
		sa.setGeometry(GeoUtils.jsonFeature2jtsGeometry((Feature)input.getSingleStartGeometry()));
		
		sigmet.getAnalysis().add(sa);
		
		System.err.println("FPA required: "+fpaRequired);
		if (fpaRequired) {
			SigmetForecastPositionAnalysis fpa=new SigmetForecastPositionAnalysisImpl();
			fpa.setTime(input.getValiddate_end().atZoneSameInstant(ZoneId.of("UTC")));
			fpa.setApproximateLocation(false); //TODO
			fpa.setGeometry(GeoUtils.jsonFeature2jtsGeometry((Feature)input.getSingleEndGeometry()));
			sigmet.setSingleForecastPositionAnalysis(fpa);
		}
		
		retval.setConvertedMessage(sigmet);

		return retval;
	}
}