package fun.seabird;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class SubStats 
{
	private static final DateTimeFormatter indexDtf = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
	
	private final LocalDateTime date;
	private final String subnational1Code;
	private final String county;

	private int numAssetsUploaded = 0;
	private int numAssetsLocal = 0;		

	public SubStats(LocalDateTime date, String subnational1Code, String county) {
		super();
		this.date = date;
		this.subnational1Code = subnational1Code;
		this.county = county;
	}

	public Integer getNumAssetsLocal() {
		return numAssetsLocal;
	}

	public void incNumAssetsLocal() {
		this.numAssetsLocal++;
	}
	
	public void incNumAssetsUploaded(int amount) {
		this.numAssetsUploaded += amount;
	}

	public String getDate() {
		return date.format(indexDtf);
	}

	public int getNumAssetsUploaded() {
		return numAssetsUploaded;
	}

	public static DateTimeFormatter getIndexdtf() {
		return indexDtf;
	}

	public String getSubnational1Code() {
		return subnational1Code;
	}

	public String getCounty() {
		return county;
	}
}
