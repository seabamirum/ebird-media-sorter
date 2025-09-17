package fun.seabird.sorter;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.atomic.AtomicInteger;

class SubStats 
{
	private static final DateTimeFormatter indexDtf = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
	
	private final LocalDateTime date;
	private final String subnational1Code;
	private final String county;
	private final String locName;

	private AtomicInteger numAssetsUploaded = new AtomicInteger(0);
	private AtomicInteger numAssetsLocal = new AtomicInteger(0);

	public SubStats(LocalDateTime date, String subnational1Code, String county,String locName) {
		this.date = date;
		this.subnational1Code = subnational1Code;
		this.county = county;
		this.locName = locName;
	}

	public Integer getNumAssetsLocal() {
		return numAssetsLocal.get();
	}

	public int incNumAssetsLocal() {
		return numAssetsLocal.incrementAndGet();
	}
	
	public int incNumAssetsUploaded(int amount) {
		return numAssetsUploaded.addAndGet(amount);
	}

	public String getDate() {
		return date.format(indexDtf);
	}

	public int getNumAssetsUploaded() {
		return numAssetsUploaded.get();
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

	public String getLocName() {
		return locName;
	}
}
