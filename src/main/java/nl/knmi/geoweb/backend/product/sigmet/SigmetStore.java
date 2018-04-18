package nl.knmi.geoweb.backend.product.sigmet;

import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.nio.file.NotDirectoryException;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import nl.knmi.adaguc.tools.Debug;
import nl.knmi.adaguc.tools.Tools;
import nl.knmi.geoweb.backend.product.sigmet.Sigmet.SigmetStatus;

@Component
public class SigmetStore {
	
	private String directory;
	
	@Autowired
	@Qualifier("sigmetObjectMapper")
	private ObjectMapper sigmetObjectMapper;
	
	public ObjectMapper getOM(){ return sigmetObjectMapper;}
	
	public SigmetStore(@Value(value = "${productstorelocation}") String productstorelocation) throws IOException {
		this.setLocation(productstorelocation);
	}
	
	public void setLocation(String productstorelocation) throws IOException {
		String dir = productstorelocation + "/sigmets";
		Debug.println("SIGMET STORE at " + dir);
		File f = new File(dir);
		if(f.exists() == false){
			Tools.mksubdirs(f.getAbsolutePath());
			Debug.println("Creating sigmet store at ["+f.getAbsolutePath()+"]");		}
		if(f.isDirectory() == false){
			Debug.errprintln("Sigmet directory location is not a directory");
			throw new NotDirectoryException("Sigmet directory location is not a directorty");
		}
		
		this.directory=dir;
	}

	public void storeSigmet(ObjectMapper om, Sigmet sigmet) {
		String fn=String.format("%s/sigmet_%s.json", this.directory, sigmet.getUuid());
		sigmet.serializeSigmet(om, fn);	
	}
	
	public void storeSigmet(Sigmet sigmet) {
		String fn=String.format("%s/sigmet_%s.json", this.directory, sigmet.getUuid());
		sigmet.serializeSigmet(sigmetObjectMapper, fn);	
	}

	public synchronized int getNextSequence() {
		// Day zero means all sigmets of today since midnight UTC
		Sigmet[] sigmets = getPublishedSigmetsSinceDay(0);
		int seq = 1;

		if (sigmets.length > 0){
			Arrays.sort(sigmets, (rhs, lhs) -> rhs.getSequence() < lhs.getSequence() ? 1 : (rhs.getSequence() == lhs.getSequence() ? 0 : -1));
			seq = sigmets[0].getSequence() + 1;
		}
		return seq;
	}
	
	public Sigmet[] getPublishedSigmetsSinceDay (int daysOffset) {
		Sigmet[] sigmets = getSigmets(false, SigmetStatus.PUBLISHED);
		OffsetDateTime offset = OffsetDateTime.now(ZoneId.of("Z")).minusDays(daysOffset);
		OffsetDateTime offsetSinceMidnight = offset.withHour(0).withMinute(0).withSecond(0).withNano(0);

		return Arrays.stream(sigmets).filter(sigmet -> sigmet.getValiddate().isAfter(offsetSinceMidnight)).toArray(Sigmet[]::new);
	}

	public Sigmet[] getSigmets(boolean selectActive, SigmetStatus selectStatus) {
		Comparator<Sigmet> comp = new Comparator<Sigmet>() {
			public int compare(Sigmet lhs, Sigmet rhs) {
				if (rhs.getIssuedate() != null && lhs.getIssuedate() != null)
					return rhs.getIssuedate().compareTo(lhs.getIssuedate());
				else 
					return rhs.getValiddate().compareTo(lhs.getValiddate());
			}
		};
		//Scan directory for sigmets
		File dir=new File(directory);
		File[] files=dir.listFiles(new FilenameFilter() {

			@Override
			public boolean accept(File dir, String name) {
				return !name.contains("..") && name.contains("sigmet_")&&name.endsWith(".json");
			}
		});

		OffsetDateTime now= OffsetDateTime.now(ZoneId.of("Z"));

		if (files!=null) {
			List<Sigmet> sigmets=new ArrayList<Sigmet>();
			for (File f: files) {
				Sigmet sm;
				try {
					sm = Sigmet.getSigmetFromFile(sigmetObjectMapper, f);
					if (selectActive) {
						if ((sm.getStatus()==SigmetStatus.PUBLISHED)&&(sm.getValiddate().isBefore(now)) && (sm.getValiddate_end().isAfter(now))) {
							sigmets.add(sm);
						}
					}else if (selectStatus != null) {
						if (sm.getStatus()==selectStatus) {
							sigmets.add(sm);
						}
					} else {
						sigmets.add(sm);
					}
				} catch (JsonParseException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				} catch (JsonMappingException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}
			sigmets.sort(comp);
			return sigmets.toArray(new Sigmet[0]);
		}
		return null;
	}

	public Sigmet getByUuid(String uuid) {
		for (Sigmet sigmet: getSigmets(false, null)) {
			if (uuid.equals(sigmet.getUuid())){
				return sigmet;
			}
		}
		return null;
	}
}
